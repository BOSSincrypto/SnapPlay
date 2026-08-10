package io.github.bossincrypto.snapplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSourceTest {
    @Test
    fun acceptsOnlyHttpVideoUrls() {
        assertEquals("https://example.com/video.mp4", parseVideoUrl(" https://example.com/video.mp4 "))
        assertEquals("http://example.com/video.webm", parseVideoUrl("http://example.com/video.webm"))
        assertNull(parseVideoUrl("file:///private/video.mp4"))
        assertNull(parseVideoUrl("javascript:alert(1)"))
        assertNull(parseVideoUrl("not a url"))
    }
}
