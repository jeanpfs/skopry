package app.skopry.android

import app.skopry.android.ui.input.StreamZoom
import app.skopry.android.ui.input.ZoomMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMemoryTest {
    @Test fun aSourceNeverZoomedIsAtOneTimes() {
        assertEquals(StreamZoom.IDENTITY, ZoomMemory()[ZoomMemory.DESKTOP])
    }

    @Test fun eachSourceKeepsItsOwnZoom() {
        val m = ZoomMemory()
        val warp = StreamZoom(3f, 10f, -20f)
        m[ZoomMemory.window("dev.warp", 1)] = warp
        assertEquals(warp, m[ZoomMemory.window("dev.warp", 99)])           // same app, reopened window
        assertEquals(StreamZoom.IDENTITY, m[ZoomMemory.window("com.apple.Safari", 2)])
        assertEquals(StreamZoom.IDENTITY, m[ZoomMemory.DESKTOP])
    }

    @Test fun zoomingBackToOneForgetsIt() {
        val m = ZoomMemory()
        m[ZoomMemory.DESKTOP] = StreamZoom(2f, 0f, 0f)
        m[ZoomMemory.DESKTOP] = StreamZoom.IDENTITY
        assertEquals(StreamZoom.IDENTITY, m[ZoomMemory.DESKTOP])
    }

    @Test fun aWindowWithoutAnAppIsToldById() {
        assertEquals("window:7", ZoomMemory.window("", 7))
    }
}
