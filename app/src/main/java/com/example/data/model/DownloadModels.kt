package com.example.data.model

import java.util.Locale

enum class MediaType(val value: String) {
    VIDEO("video"),
    IMAGE("image"),
    AUDIO("audio");

    companion object {
        fun fromString(raw: String?): MediaType {
            val normalized = raw?.trim()?.lowercase() ?: ""
            return when {
                normalized.contains("audio") || normalized.contains("mp3") || normalized.contains("m4a") -> AUDIO
                normalized.contains("image") || normalized.contains("photo") || normalized.contains("jpg") || normalized.contains("png") || normalized.contains("webp") -> IMAGE
                else -> VIDEO
            }
        }
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaOption(
    val id: String,
    val url: String,
    val type: MediaType,
    val extension: String,
    val qualityLabel: String,
    val fileSizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hasAudio: Boolean = true,
    val mimeType: String = when (type) {
        MediaType.VIDEO -> "video/${extension.lowercase().ifBlank { "mp4" }}"
        MediaType.IMAGE -> "image/${extension.lowercase().ifBlank { "jpg" }}"
        MediaType.AUDIO -> "audio/${extension.lowercase().ifBlank { "mp3" }}"
    }
)

data class MediaResolveResult(
    val originalUrl: String,
    val platform: SocialPlatform,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val options: List<MediaOption>,
    val creditsSpent: Double? = null,
    val creditsRemaining: Double? = null
)

data class ProviderUsageInfo(
    val isKeyConfigured: Boolean,
    val isConnected: Boolean,
    val planName: String? = null,
    val creditsRemaining: Double? = null,
    val totalRequests: Long? = null,
    val rateLimitDescription: String? = null,
    val statusMessage: String? = null
)

data class DownloadTask(
    val id: String,
    val originalUrl: String,
    val mediaUrl: String,
    val title: String,
    val platform: SocialPlatform,
    val mediaType: MediaType,
    val extension: String,
    val qualityLabel: String,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val savedUriString: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

object MediaFormatters {
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "—"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDuration(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) return ""
        val mins = seconds / 60
        val secs = seconds % 60
        val hours = mins / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins % 60, secs)
        } else {
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }
}
