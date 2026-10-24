package app.skopry.android

import app.skopry.android.ui.input.StreamZoom
import app.skopry.android.ui.input.ZoomFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamZoomTest {
    private val pw = 1000f
    private val ph = 800f
    // A 16:10 picture in a 1000x800 panel: full width, bars above and below.
    private val base = ZoomFrame(0f, 87.5f, 1000f, 625f)

    private fun pinch(z: StreamZoom, k: Float, fx: Float, fy: Float, dx: Float = 0f, dy: Float = 0f) =
        z.pinch(k, fx, fy, dx, dy, base, pw, ph)

    @Test fun identityIsTheAspectFit() {
        val f = StreamZoom.IDENTITY.frame(base, pw, ph)
        assertEquals(base, f)
    }

    @Test fun thePointUnderTheFingersStaysUnderThem() {
        val fx = 700f; val fy = 300f
        val before = StreamZoom.IDENTITY.frame(base, pw, ph)
        val u = (fx - before.left) / before.width; val v = (fy - before.top) / before.height
        val z = pinch(StreamZoom.IDENTITY, 2f, fx, fy)
        val after = z.frame(base, pw, ph)
        assertEquals(2f, z.scale, 0f)
        assertEquals(fx, after.left + u * after.width, 0.01f)
        assertEquals(fy, after.top + v * after.height, 0.01f)
    }

    @Test fun scaleIsHeldBetweenOneAndFive() {
        assertEquals(StreamZoom.MAX, pinch(StreamZoom.IDENTITY, 50f, 500f, 400f).scale, 0f)
        assertEquals(StreamZoom.IDENTITY, pinch(pinch(StreamZoom.IDENTITY, 2f, 500f, 400f), 0.01f, 500f, 400f))
    }

    @Test fun aSlightPinchOutDoesNotSnapBackToFullScreen() {
        val z = pinch(pinch(StreamZoom.IDENTITY, 3f, 500f, 400f), 0.9f, 500f, 400f)
        assertTrue(z.zoomed)
        assertEquals(2.7f, z.scale, 0.001f)
    }

    @Test fun noEdgeOfThePictureCanBeDraggedIntoView() {
        var z = pinch(StreamZoom.IDENTITY, 3f, 500f, 400f)
        z = pinch(z, 1f, 500f, 400f, dx = 5000f, dy = 5000f)
        val f = z.frame(base, pw, ph)
        assertEquals(0f, f.left, 0.01f)
        assertEquals(0f, f.top, 0.01f)
        z = pinch(z, 1f, 500f, 400f, dx = -50000f, dy = -50000f)
        val g = z.frame(base, pw, ph)
        assertEquals(pw, g.left + g.width, 0.01f)
        assertEquals(ph, g.top + g.height, 0.01f)
    }

    @Test fun anAxisThatStillFitsStaysCentred() {
        // 1.2x: 750 high, shorter than the 800 panel, so it stays centred vertically however it is dragged.
        val z = pinch(StreamZoom.IDENTITY, 1.2f, 500f, 400f, dx = 0f, dy = 300f)
        val f = z.frame(base, pw, ph)
        assertEquals((ph - f.height) / 2f, f.top, 0.01f)
    }

    @Test fun aDegenerateFactorChangesNothing() {
        val z = pinch(StreamZoom.IDENTITY, 2f, 500f, 400f)
        assertEquals(z, pinch(z, 0f, 500f, 400f))
        assertEquals(z, pinch(z, Float.NaN, 500f, 400f))
        assertFalse(StreamZoom.IDENTITY.zoomed)
    }
}
