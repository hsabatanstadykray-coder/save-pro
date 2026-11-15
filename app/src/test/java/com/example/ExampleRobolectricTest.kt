package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MediaSaver Pro", appName)
    }

    @Test
    fun `detects all supported social platforms`() {
        val cases = mapOf(
            "https://www.tiktok.com/@user/video/12345" to SocialPlatform.TIKTOK,
            "https://www.instagram.com/reel/Cx1a2b3c/" to SocialPlatform.INSTAGRAM,
            "https://fb.watch/abc123/" to SocialPlatform.FACEBOOK,
            "https://youtu.be/dQw4w9WgXcQ" to SocialPlatform.YOUTUBE,
            "https://x.com/user/status/999" to SocialPlatform.X_TWITTER,
            "https://www.snapchat.com/spotlight/xyz" to SocialPlatform.SNAPCHAT,
            "https://pin.it/123abc" to SocialPlatform.PINTEREST,
            "https://www.threads.net/@user/post/123" to SocialPlatform.THREADS,
            "https://clips.twitch.tv/FunClip" to SocialPlatform.TWITCH,
            "https://vk.com/video-123_456" to SocialPlatform.VK,
            "https://l.likee.video/v/abc" to SocialPlatform.LIKEE,
            "https://www.reddit.com/r/kotlin/comments/123/post" to SocialPlatform.REDDIT,
            "https://9gag.com/gag/aXYZ123" to SocialPlatform.NINE_GAG
        )
        cases.forEach { (url, expected) ->
            assertEquals("Failed for $url", expected, SocialPlatform.detectFromUrl(url))
        }
    }

    @Test
    fun `rejects unsafe or invalid urls`() {
        assertNull(SocialPlatform.extractValidHttpsUrl("not a url"))
        assertNull(SocialPlatform.extractValidHttpsUrl("http://localhost:8080/video"))
        assertNull(SocialPlatform.extractValidHttpsUrl("https://127.0.0.1/test"))
        assertNull(SocialPlatform.extractValidHttpsUrl("https://192.168.1.1/file"))
        assertEquals(
            "https://instagram.com/p/123",
            SocialPlatform.extractValidHttpsUrl("Check this out: http://instagram.com/p/123")
        )
    }
}
