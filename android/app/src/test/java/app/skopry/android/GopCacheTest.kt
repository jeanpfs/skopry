package app.skopry.android

import app.skopry.android.video.GopCache
import app.skopry.android.video.GopCache.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GopCacheTest {
    private fun f(pts: Long, key: Boolean = false, n: Int = 10) = Frame(ByteArray(n), pts, key)

    @Test fun theRunStartsAtTheLastKeyframeAndKeepsTheOrder() {
        val c = GopCache()
        c.add(f(1, true)); c.add(f(2)); c.add(f(3)); c.add(f(4, true)); c.add(f(5)); c.add(f(6))
        assertEquals(listOf(4L, 5L, 6L), c.replay()!!.map { it.ptsUs })
        assertEquals(true, c.replay()!!.first().key)
    }

    @Test fun deltasBeforeAnyKeyframeAreNotARun() {
        val c = GopCache()
        c.add(f(1)); c.add(f(2))
        assertNull(c.replay())
        c.add(f(3, true))
        assertEquals(listOf(3L), c.replay()!!.map { it.ptsUs })
    }

    @Test fun aRunOverTheByteBudgetIsDroppedWholeNotCutShort() {
        val c = GopCache(maxBytes = 100)
        c.add(f(1, true, 60)); c.add(f(2, false, 30))
        assertEquals(2, c.replay()!!.size)
        c.add(f(3, false, 30))                          // 120 > 100
        assertNull(c.replay())                          // no partial run that would decode to a broken picture
        c.add(f(4, false, 5))
        assertNull(c.replay())                          // and it stays unusable until the next keyframe
        c.add(f(5, true, 20))
        assertEquals(listOf(5L), c.replay()!!.map { it.ptsUs })
    }

    @Test fun clearForgetsTheRun() {
        val c = GopCache()
        c.add(f(1, true)); c.clear()
        assertNull(c.replay())
        assertEquals(0, c.size)
    }

    @Test fun theReplayIsACopyTheCachingGoesOnUnderneath() {
        val c = GopCache()
        c.add(f(1, true)); val r = c.replay()!!
        c.add(f(2))
        assertEquals(1, r.size)
        assertEquals(2, c.replay()!!.size)
    }
}
