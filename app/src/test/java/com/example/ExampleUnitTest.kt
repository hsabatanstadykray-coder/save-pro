package com.example

import com.example.data.model.MediaFormatters
import com.example.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `formatters output expected human readable values`() {
        assertEquals("—", MediaFormatters.formatBytes(null))
        assertEquals("512 B", MediaFormatters.formatBytes(512L))
        assertEquals("2 KB", MediaFormatters.formatBytes(2048L))
        assertEquals("1.5 MB", MediaFormatters.formatBytes(1572864L))
        assertEquals("2:05", MediaFormatters.formatDuration(125L))
        assertEquals("1:01:05", MediaFormatters.formatDuration(3665L))
    }

    @Test
    fun `media type mapping parses extensions and types`() {
        assertEquals(MediaType.AUDIO, MediaType.fromString("mp3"))
        assertEquals(MediaType.IMAGE, MediaType.fromString("image"))
        assertEquals(MediaType.VIDEO, MediaType.fromString("video"))
    }
}
