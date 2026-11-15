package com.example.data.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.DownloadHistoryDao
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.DownloadedMediaDao
import com.example.data.local.DownloadedMediaEntity
import com.example.data.local.SaveLocation
import com.example.data.local.SettingsDataStore
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaOption
import com.example.data.model.MediaResolveResult
import com.example.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class DownloadEvent {
    data class Completed(val title: String) : DownloadEvent()
    data class Failed(val title: String, val reason: String) : DownloadEvent()
}

class DownloadEngine(
    private val context: Context,
    private val historyDao: DownloadHistoryDao,
    private val mediaDao: DownloadedMediaDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val historyIdByTask = ConcurrentHashMap<String, Long>()

    private val partialDir: File by lazy {
        File(context.cacheDir, "partial_downloads").apply { mkdirs() }
    }

    init {
        createNotificationChannel()
    }

    fun startDownload(resolveResult: MediaResolveResult, option: MediaOption): String {
        val duplicate = _tasks.value.firstOrNull {
            it.mediaUrl == option.url && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
        }
        if (duplicate != null) return duplicate.id

        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            originalUrl = resolveResult.originalUrl,
            mediaUrl = option.url,
            title = resolveResult.title,
            platform = resolveResult.platform,
            mediaType = option.type,
            extension = option.extension,
            qualityLabel = option.qualityLabel,
            thumbnailUrl = resolveResult.thumbnailUrl,
            status = DownloadStatus.QUEUED,
            totalBytes = option.fileSizeBytes ?: -1L
        )

        _tasks.update { listOf(task) + it }

        scope.launch {
            val historyId = historyDao.insert(
                DownloadHistoryEntity(
                    platformId = resolveResult.platform.id,
                    originalUrl = resolveResult.originalUrl,
                    title = resolveResult.title,
                    thumbnailUrl = resolveResult.thumbnailUrl,
                    downloadedAt = task.createdAt,
                    mediaType = option.type.value,
                    fileSizeBytes = option.fileSizeBytes ?: 0L,
                    status = DownloadStatus.DOWNLOADING.name
                )
            )
            historyIdByTask[taskId] = historyId
            launchTaskJob(taskId)
        }

        return taskId
    }

    fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTask(taskId) {
            if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) {
                it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
            } else it
        }
    }

    fun resumeDownload(taskId: String) {
        val existing = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (existing.status == DownloadStatus.PAUSED || existing.status == DownloadStatus.FAILED) {
            launchTaskJob(taskId)
        }
    }

    fun retryDownload(taskId: String) {
        val existing = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.CANCELLED) {
            launchTaskJob(taskId)
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        File(partialDir, "$taskId.part").delete()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0L)
        }
        scope.launch {
            historyIdByTask[taskId]?.let { hId ->
                historyDao.updateStatus(hId, DownloadStatus.CANCELLED.name, 0L)
            }
        }
    }

    fun removeTaskFromList(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        File(partialDir, "$taskId.part").delete()
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    private fun launchTaskJob(taskId: String) {
        activeJobs[taskId]?.cancel()
        val job = scope.launch {
            executeDownload(taskId)
        }
        activeJobs[taskId] = job
    }

    private suspend fun executeDownload(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        val partFile = File(partialDir, "$taskId.part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        updateTask(taskId) {
            it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null, bytesDownloaded = existingBytes)
        }

        try {
            val requestBuilder = Request.Builder()
                .url(task.mediaUrl)
                .header("User-Agent", "MediaSaverPro/1.0 (Android)")

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416 && existingBytes > 0L) {
                        partFile.delete()
                        throw IOException("Range mismatch; please retry.")
                    }
                    throw IOException("HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response stream")
                val isResumed = response.code == 206 && existingBytes > 0L
                val startOffset = if (isResumed) existingBytes else {
                    if (partFile.exists()) partFile.delete()
                    0L
                }

                val contentLength = body.contentLength()
                val totalBytes = when {
                    contentLength > 0L -> startOffset + contentLength
                    task.totalBytes > 0L -> task.totalBytes
                    else -> -1L
                }

                updateTask(taskId) {
                    it.copy(bytesDownloaded = startOffset, totalBytes = totalBytes)
                }

                var downloaded = startOffset
                var windowStartTime = System.currentTimeMillis()
                var windowBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile, isResumed).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            windowBytes += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - windowStartTime
                            if (elapsed >= 350L) {
                                val speed = (windowBytes * 1000L) / elapsed.coerceAtLeast(1L)
                                updateTask(taskId) {
                                    it.copy(
                                        bytesDownloaded = downloaded,
                                        totalBytes = if (totalBytes > 0L) totalBytes else -1L,
                                        speedBytesPerSec = speed
                                    )
                                }
                                windowStartTime = now
                                windowBytes = 0L
                            }
                        }
                        output.flush()
                    }
                }

                if (!partFile.exists() || partFile.length() <= 0L) {
                    throw IOException("Downloaded file is empty")
                }

                val finalSize = partFile.length()
                val settings = settingsDataStore.settingsFlow.first()
                val savedResult = saveCompletedFile(task, partFile, settings.saveLocation)

                partFile.delete()

                val mediaEntity = DownloadedMediaEntity(
                    id = task.id,
                    title = task.title,
                    fileName = savedResult.fileName,
                    mediaType = task.mediaType.value,
                    mimeType = savedResult.mimeType,
                    platformId = task.platform.id,
                    fileSizeBytes = finalSize,
                    uriString = savedResult.uri.toString(),
                    isMediaStoreUri = savedResult.isMediaStore,
                    originalUrl = task.originalUrl,
                    sourceMediaUrl = task.mediaUrl,
                    thumbnailUrl = task.thumbnailUrl,
                    createdAt = System.currentTimeMillis()
                )
                mediaDao.insert(mediaEntity)

                historyIdByTask[taskId]?.let { hId ->
                    historyDao.updateStatus(hId, DownloadStatus.COMPLETED.name, finalSize)
                }

                updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = finalSize,
                        totalBytes = finalSize,
                        speedBytesPerSec = 0L,
                        savedUriString = savedResult.uri.toString()
                    )
                }

                if (settings.notificationsEnabled) {
                    postCompletionNotification(task.title)
                }
                _events.tryEmit(DownloadEvent.Completed(task.title))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val reason = e.localizedMessage ?: "Download failed"
            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    speedBytesPerSec = 0L,
                    errorMessage = reason
                )
            }
            historyIdByTask[taskId]?.let { hId ->
                historyDao.updateStatus(hId, DownloadStatus.FAILED.name, partFile.length())
            }
            _events.tryEmit(DownloadEvent.Failed(task.title, reason))
        } finally {
            activeJobs.remove(taskId)
        }
    }

    private data class SavedFileOutcome(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val isMediaStore: Boolean
    )

    private fun saveCompletedFile(
        task: DownloadTask,
        partFile: File,
        saveLocation: SaveLocation
    ): SavedFileOutcome {
        val ext = task.extension.lowercase().ifBlank {
            when (task.mediaType) {
                MediaType.VIDEO -> "mp4"
                MediaType.IMAGE -> "jpg"
                MediaType.AUDIO -> "mp3"
            }
        }
        val mimeType = when (task.mediaType) {
            MediaType.VIDEO -> "video/${if (ext == "mov") "quicktime" else ext}"
            MediaType.IMAGE -> "image/${if (ext == "jpg") "jpeg" else ext}"
            MediaType.AUDIO -> "audio/${if (ext == "mp3") "mpeg" else ext}"
        }
        val safeBase = task.title
            .replace(Regex("""[^\p{L}\p{N}_\- ]"""), "")
            .trim()
            .take(40)
            .ifBlank { "${task.platform.id}_media" }
            .replace(" ", "_")
        val fileName = "${safeBase}_${System.currentTimeMillis() % 100000}.$ext"

        if (saveLocation == SaveLocation.MEDIA_STORE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val (collection, relativeDir) = when (task.mediaType) {
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/MediaSaverPro"
                MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/MediaSaverPro"
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/MediaSaverPro"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(collection, values)
            if (insertedUri != null) {
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        partFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IOException("Failed to open MediaStore stream")

                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, publishValues, null, null)
                    return SavedFileOutcome(insertedUri, fileName, mimeType, isMediaStore = true)
                } catch (e: Exception) {
                    resolver.delete(insertedUri, null, null)
                    throw e
                }
            }
        }

        val downloadsDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads"),
            "MediaSaverPro"
        ).apply { mkdirs() }
        val targetFile = File(downloadsDir, fileName)
        partFile.inputStream().use { input ->
            FileOutputStream(targetFile).use { out ->
                input.copyTo(out)
            }
        }
        val providerUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        return SavedFileOutcome(providerUri, fileName, mimeType, isMediaStore = false)
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    fun getPartialCacheSizeBytes(): Long {
        return partialDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            (context.cacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
    }

    fun clearTempCache() {
        val activeIds = activeJobs.keys.toSet()
        partialDir.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".part")
            if (id !in activeIds) {
                file.delete()
            }
        }
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name != "partial_downloads") {
                file.deleteRecursively()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Media download completion notifications"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun postCompletionNotification(title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MediaSaver Pro")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "mediasaver_downloads"
    }
}
