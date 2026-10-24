package app.skopry.android

import app.skopry.android.ui.input.BackspaceRepeat
import app.skopry.android.ui.input.BackspaceRepeat.Delete
import org.junit.Assert.assertEquals
import org.junit.Test

class BackspaceRepeatTest {
    /** The gaps (ms) Samsung Keyboard produced on the Fold, measured: a ramp from 400 ms down to 40, then a steady 222. */
    private val samsungHold = listOf(401, 112, 94, 81, 67, 58, 48, 42, 42, 40, 42, 41, 40, 41, 42, 222, 221, 221, 222, 223, 224, 222, 220)

    private fun run(gaps: List<Long>, start: Long = 1_000): List<Delete> {
        val r = BackspaceRepeat()
        var t = start
        val out = mutableListOf(r.onBackspace(t))
        for (g in gaps) { t += g; out += r.onBackspace(t) }
        return out
    }

    @Test fun aHeldKeyIsCharactersThroughTheRampAndWordsInTheSlowPhase() {
        val d = run(samsungHold.map { it.toLong() })
        // 24 events: the first (the key going down) and the ramp are characters...
        assertEquals(List(16) { Delete.CHAR }, d.take(16))
        // ...and every one after the keyboard dropped to its steady 222 ms is a word.
        assertEquals(List(d.size - 16) { Delete.WORD }, d.drop(16))
    }

    @Test fun tappingBackspaceByHandIsAlwaysOneCharacter() {
        assertEquals(List(30) { Delete.CHAR }, run(List(29) { 220L }))     // 4.5 taps a second, fast for a hand
        assertEquals(List(30) { Delete.CHAR }, run(List(29) { 120L }))     // 8 a second, faster than anyone sustains
        assertEquals(List(10) { Delete.CHAR }, run(List(9) { 300L }))
    }

    @Test fun aRepeatThatNeverGetsFastStaysCharacters() {
        // A keyboard repeating at a steady 90 ms (some do): no ramp to 70 ms, nothing to mistake for the slow phase.
        assertEquals(List(40) { Delete.CHAR }, run(List(39) { 90L }))
    }

    @Test fun lettingGoEndsTheRunAndTheNextHoldStartsOverAsCharacters() {
        val first = run(samsungHold.map { it.toLong() })
        val r = BackspaceRepeat()
        var t = 0L
        (listOf(0L) + samsungHold.map { it.toLong() }).forEach { g -> t += g; r.onBackspace(t) }
        t += 1_500                                   // the key was let go for a while
        assertEquals(Delete.CHAR, r.onBackspace(t))  // a new press
        t += 222
        assertEquals(Delete.CHAR, r.onBackspace(t))  // and a tap 222 ms later is not the slow phase
        assertEquals(Delete.WORD, first.last())
    }

    @Test fun aPauseInTheSlowPhaseEndsItToo() {
        val r = BackspaceRepeat()
        var t = 0L
        (listOf(0L) + samsungHold.map { it.toLong() }).forEach { g -> t += g; r.onBackspace(t) }
        t += 600
        assertEquals(Delete.CHAR, r.onBackspace(t))
    }
}
