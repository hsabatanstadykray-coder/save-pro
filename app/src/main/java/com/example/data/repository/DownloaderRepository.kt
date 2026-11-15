package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.download.DownloadEngine
import com.example.data.download.DownloadEvent
import com.example.data.local.AppDatabase
import com.example.data.local.AppLanguage
import com.example.data.local.DefaultQuality
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.DownloadedMediaEntity
import com.example.data.local.SaveLocation
import com.example.data.local.SettingsDataStore
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.model.DownloadTask
import com.example.data.model.MediaOption
import com.example.data.model.MediaResolveResult
import com.example.data.model.ProviderUsageInfo
import com.example.data.network.MediaDownloaderProvider
import com.example.data.network.ResolveOutcome
import com.example.data.network.SaveApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class DownloaderRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    val settingsDataStore: SettingsDataStore = SettingsDataStore(context),
    private val provider: MediaDownloaderProvider = SaveApiProvider()
) {
    private val historyDao = database.downloadHistoryDao()
    private val mediaDao = database.downloadedMediaDao()
    private val downloadEngine = DownloadEngine(context, historyDao, mediaDao, settingsDataStore)

    val downloadTasks: StateFlow<List<DownloadTask>> = downloadEngine.tasks
    val downloadEvents: SharedFlow<DownloadEvent> = downloadEngine.events
    val historyFlow: Flow<List<DownloadHistoryEntity>> = historyDao.observeAll()
    val libraryFlow: Flow<List<DownloadedMediaEntity>> = mediaDao.observeAll()
    val savedCountFlow: Flow<Int> = mediaDao.observeCount()
    val settingsFlow: Flow<UserSettings> = settingsDataStore.settingsFlow

    fun isApiConfigured(): Boolean = provider.isConfigured()

    suspend fun resolveUrl(url: String): ResolveOutcome = provider.resolveMedia(url)

    suspend fun fetchApiUsage(): ProviderUsageInfo = provider.fetchUsageInfo()

    fun enqueueDownload(resolveResult: MediaResolveResult, option: MediaOption): String =
        downloadEngine.startDownload(resolveResult, option)

    fun pauseDownload(taskId: String) = downloadEngine.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = downloadEngine.resumeDownload(taskId)
    fun retryDownload(taskId: String) = downloadEngine.retryDownload(taskId)
    fun cancelDownload(taskId: String) = downloadEngine.cancelDownload(taskId)
    fun dismissTask(taskId: String) = downloadEngine.removeTaskFromList(taskId)

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)
    suspend fun clearAllHistory() = historyDao.clearAll()

    suspend fun renameMedia(entity: DownloadedMediaEntity, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val clean = newTitle.trim().replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").take(60)
        if (clean.isBlank()) return@withContext false
        val ext = entity.fileName.substringAfterLast(".", "mp4")
        val newFileName = "${clean.replace(" ", "_")}.$ext"

        if (entity.isMediaStoreUri) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                }
                context.contentResolver.update(Uri.parse(entity.uriString), values, null, null)
            }
        }
        mediaDao.updateTitle(entity.id, clean, newFileName)
        true
    }

    suspend fun deleteMedia(entity: DownloadedMediaEntity) = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(entity.uriString)
            if (entity.isMediaStoreUri) {
                context.contentResolver.delete(uri, null, null)
            } else {
                val path = uri.lastPathSegment
                if (path != null) {
                    File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "MediaSaverPro/$path").delete()
                }
            }
        }
        mediaDao.deleteById(entity.id)
    }

    fun isMediaUriAccessible(entity: DownloadedMediaEntity): Boolean {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(entity.uriString), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun getCacheSizeBytes(): Long = downloadEngine.getPartialCacheSizeBytes()
    fun clearCache() = downloadEngine.clearTempCache()

    suspend fun setThemeMode(mode: ThemeMode) = settingsDataStore.setThemeMode(mode)
    suspend fun setLanguage(language: AppLanguage) = settingsDataStore.setLanguage(language)
    suspend fun setDefaultQuality(quality: DefaultQuality) = settingsDataStore.setDefaultQuality(quality)
    suspend fun setSaveLocation(location: SaveLocation) = settingsDataStore.setSaveLocation(location)
    suspend fun setNotificationsEnabled(enabled: Boolean) = settingsDataStore.setNotificationsEnabled(enabled)
    suspend fun setAutoClipboardEnabled(enabled: Boolean) = settingsDataStore.setAutoClipboardEnabled(enabled)
}
