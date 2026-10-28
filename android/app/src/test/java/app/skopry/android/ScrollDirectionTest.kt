package app.skopry.android

import app.skopry.android.client.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollDirectionTest {
    @Test fun offLeavesAFingersScrollAsItWas() {
        assertEquals(0.25, 0.25 * ScrollDirection.factor(false), 0.0)
        assertEquals(-0.4, -0.4 * ScrollDirection.factor(false), 0.0)
    }

    @Test fun onTurnsItAroundOnBothAxesAndTwiceIsTheOriginal() {
        assertEquals(-0.25, 0.25 * ScrollDirection.factor(true), 0.0)
        assertEquals(0.4, -0.4 * ScrollDirection.factor(true), 0.0)
        assertEquals(0.25, 0.25 * ScrollDirection.factor(true) * ScrollDirection.factor(true), 0.0)
    }
}
