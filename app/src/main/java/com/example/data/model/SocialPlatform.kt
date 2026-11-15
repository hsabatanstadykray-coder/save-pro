package com.example.data.model

import android.net.Uri
import androidx.compose.ui.graphics.Color

enum class SocialPlatform(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val accentColor: Color,
    val hosts: List<String>
) {
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        shortBadge = "TT",
        accentColor = Color(0xFFFF2D55),
        hosts = listOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        shortBadge = "IG",
        accentColor = Color(0xFFE1306C),
        hosts = listOf("instagram.com", "instagr.am")
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        shortBadge = "FB",
        accentColor = Color(0xFF1877F2),
        hosts = listOf("facebook.com", "fb.watch", "fb.com", "m.facebook.com")
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        shortBadge = "YT",
        accentColor = Color(0xFFFF0000),
        hosts = listOf("youtube.com", "youtu.be", "music.youtube.com", "youtube-nocookie.com")
    ),
    X_TWITTER(
        id = "twitter",
        displayName = "X (Twitter)",
        shortBadge = "X",
        accentColor = Color(0xFF38BDF8),
        hosts = listOf("x.com", "twitter.com", "t.co", "fxtwitter.com", "vxtwitter.com")
    ),
    SNAPCHAT(
        id = "snapchat",
        displayName = "Snapchat",
        shortBadge = "SC",
        accentColor = Color(0xFFFACC15),
        hosts = listOf("snapchat.com", "t.snapchat.com", "story.snapchat.com")
    ),
    PINTEREST(
        id = "pinterest",
        displayName = "Pinterest",
        shortBadge = "PIN",
        accentColor = Color(0xFFE60023),
        hosts = listOf("pinterest.com", "pin.it", "pinterest.co.uk")
    ),
    THREADS(
        id = "threads",
        displayName = "Threads",
        shortBadge = "TH",
        accentColor = Color(0xFFA855F7),
        hosts = listOf("threads.net", "threads.com")
    ),
    TWITCH(
        id = "twitch",
        displayName = "Twitch",
        shortBadge = "TW",
        accentColor = Color(0xFF9146FF),
        hosts = listOf("twitch.tv", "clips.twitch.tv")
    ),
    VK(
        id = "vk",
        displayName = "VK",
        shortBadge = "VK",
        accentColor = Color(0xFF0077FF),
        hosts = listOf("vk.com", "vkvideo.ru", "vk.ru")
    ),
    LIKEE(
        id = "likee",
        displayName = "Likee",
        shortBadge = "LK",
        accentColor = Color(0xFFFF5C8A),
        hosts = listOf("likee.video", "l.likee.video", "like.video")
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        shortBadge = "RD",
        accentColor = Color(0xFFFF4500),
        hosts = listOf("reddit.com", "redd.it", "v.redd.it")
    ),
    NINE_GAG(
        id = "9gag",
        displayName = "9GAG",
        shortBadge = "9G",
        accentColor = Color(0xFF94A3B8),
        hosts = listOf("9gag.com")
    ),
    OTHER(
        id = "other",
        displayName = "Web Link",
        shortBadge = "WEB",
        accentColor = Color(0xFF22D3EE),
        hosts = emptyList()
    );

    companion object {
        val supportedList: List<SocialPlatform> = entries.filter { it != OTHER }

        fun detectFromUrl(rawInput: String): SocialPlatform? {
            val normalized = extractValidHttpsUrl(rawInput) ?: return null
            val host = try {
                Uri.parse(normalized).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
            } catch (_: Exception) {
                null
            } ?: return null

            return supportedList.firstOrNull { platform ->
                platform.hosts.any { known ->
                    host == known || host.endsWith(".$known")
                }
            }
        }

        fun fromId(id: String?): SocialPlatform {
            val clean = id?.trim()?.lowercase() ?: return OTHER
            return entries.firstOrNull { it.id == clean || it.displayName.lowercase() == clean }
                ?: when {
                    clean.contains("twitter") || clean == "x" -> X_TWITTER
                    clean.contains("9gag") -> NINE_GAG
                    clean.contains("tiktok") -> TIKTOK
                    clean.contains("insta") -> INSTAGRAM
                    clean.contains("face") -> FACEBOOK
                    clean.contains("you") -> YOUTUBE
                    clean.contains("snap") -> SNAPCHAT
                    clean.contains("pin") -> PINTEREST
                    clean.contains("thread") -> THREADS
                    clean.contains("twitch") -> TWITCH
                    clean.contains("vk") -> VK
                    clean.contains("like") -> LIKEE
                    clean.contains("reddit") -> REDDIT
                    else -> OTHER
                }
        }

        fun extractValidHttpsUrl(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null
            val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
            val candidate = urlRegex.find(trimmed)?.value ?: return null
            val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
            val scheme = parsed.scheme?.lowercase() ?: return null
            if (scheme != "https" && scheme != "http") return null
            val host = parsed.host?.lowercase() ?: return null
            if (host.isBlank() || !host.contains(".") || host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                return null
            }
            return if (scheme == "http") candidate.replaceFirst("http://", "https://", ignoreCase = true) else candidate
        }
    }
}
