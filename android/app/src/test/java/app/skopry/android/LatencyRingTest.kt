package app.skopry.android

import app.skopry.android.video.LatencyRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyRingTest {
    private val ms = 1_000_000L

    @Test fun anOutputIsTimedFromItsOwnInput() {
        val r = LatencyRing()
        r.queued(1_000, 10 * ms); r.queued(2_000, 20 * ms)
        assertEquals(7L, r.rendered(1_000, 17 * ms))
        assertEquals(5L, r.rendered(2_000, 25 * ms))
        assertEquals(0, r.size)
    }

    @Test fun inputsThatNeverCameOutAreForgottenWhenALaterOneDoes() {
        val r = LatencyRing()
        r.queued(1, 0); r.queued(2, 0); r.queued(3, 5 * ms)
        // 1 and 2 were dropped by the codec: the output for 3 is still timed against its own input.
        assertEquals(4L, r.rendered(3, 9 * ms))
        assertEquals(0, r.size)
    }

    @Test fun anOutputWhoseInputIsNotHereIsNull() {
        val r = LatencyRing()
        r.queued(10, 0)
        assertNull(r.rendered(5, 1 * ms))   // older than anything queued
        assertEquals(1, r.size)             // and the queued one is kept for its own output
        assertNull(LatencyRing().rendered(1, 1))
    }

    @Test fun aFullRingForgetsTheOldestAndKeepsTheNewest() {
        val r = LatencyRing()
        for (i in 0 until LatencyRing.SIZE + 10) r.queued(i.toLong(), i * ms)
        assertEquals(LatencyRing.SIZE, r.size)
        assertNull(r.rendered(0, 100 * ms))                                   // overwritten
        assertEquals(1L, r.rendered((LatencyRing.SIZE + 9).toLong(), (LatencyRing.SIZE + 10) * ms))
    }

    @Test fun theRingWrapsAroundManyTimes() {
        val r = LatencyRing()
        for (i in 0 until LatencyRing.SIZE * 5) {
            r.queued(i.toLong(), 0)
            assertEquals(3L, r.rendered(i.toLong(), 3 * ms))
        }
        assertEquals(0, r.size)
    }

    @Test fun clearForgetsEverything() {
        val r = LatencyRing()
        r.queued(1, 0); r.clear()
        assertNull(r.rendered(1, ms))
        assertEquals(0, r.size)
    }
}
