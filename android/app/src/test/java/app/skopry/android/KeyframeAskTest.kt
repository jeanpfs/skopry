package app.skopry.android

import app.skopry.android.video.KeyframeAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeAskTest {
    @Test fun theFirstWaitAsksAtOnceAndOnlyOnce() {
        val a = KeyframeAsk()
        assertTrue(a.begin(10_000))
        assertFalse(a.begin(10_100))          // still the same wait
        assertFalse(a.poll(10_200))           // not due yet
    }

    @Test fun anUnansweredAskIsRepeatedAfter4_8_16_then_every32Seconds() {
        val a = KeyframeAsk()
        var now = 0L
        assertTrue(a.begin(now))
        val gaps = mutableListOf<Long>()
        repeat(6) {
            val d = a.nextDelay(now)!!
            gaps += d
            now += d
            assertFalse(a.poll(now - 1))      // a millisecond early: nothing
            assertTrue(a.poll(now))
        }
        assertEquals(listOf(4_000L, 8_000L, 16_000L, 32_000L, 32_000L, 32_000L), gaps)
    }

    @Test fun aKeyframeEndsTheWaitAndNothingIsAskedAfterIt() {
        val a = KeyframeAsk()
        a.begin(0)
        a.end()
        assertNull(a.nextDelay(1_000))
        assertFalse(a.poll(100_000))
    }

    @Test fun aWaitRightAfterAnAskWaitsOutTheSpacing() {
        val a = KeyframeAsk()
        assertTrue(a.begin(0)); a.end()
        assertFalse(a.begin(500))             // 0.5 s after the last ask: not yet
        assertEquals(1_500L, a.nextDelay(500))
        assertFalse(a.poll(1_999))
        assertTrue(a.poll(2_000))             // spacing over: asked, with the first repeat's wait after it
        assertEquals(4_000L, a.nextDelay(2_000))
    }

    @Test fun aLaterWaitStartsTheRepeatsAfresh() {
        val a = KeyframeAsk()
        a.begin(0)
        a.poll(4_000); a.poll(12_000)         // the series is at its third wait
        a.end()
        assertTrue(a.begin(100_000))
        assertEquals(4_000L, a.nextDelay(100_000))
    }
}
