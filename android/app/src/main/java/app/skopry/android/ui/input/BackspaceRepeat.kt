package app.skopry.android.ui.input

/**
 * Telling the soft keyboard's held backspace from fingers tapping it. Samsung Keyboard repeats a held backspace
 * fast (40 ms apart after a ramp from 400 ms) for about a second, then drops to a steady ~220 ms and expects the
 * editor to delete a word each time. The Mac's field is not in the keyboard's editor (it is empty there), so each
 * of those slow events deleted one character and the deletion seemed to slow down the longer the key was held.
 *
 * A person cannot tap 8 times with gaps of 70 ms or less, so a run of those marks the keyboard's own repeat, and
 * the events that follow it while the gaps stay under [RUN_BREAK_MS] are the slow phase: [WORD]. A pause longer than
 * that ends the run, and a keyboard that never repeats that fast (or taps) always gets [CHAR].
 */
class BackspaceRepeat {
    enum class Delete { CHAR, WORD }

    private var lastAt = Long.MIN_VALUE / 2
    private var fastRun = 0

    /** A backspace arrived at `nowMs` (any monotonic clock): what to delete for it. */
    fun onBackspace(nowMs: Long): Delete {
        val gap = nowMs - lastAt
        lastAt = nowMs
        if (gap > RUN_BREAK_MS) fastRun = 0
        if (gap <= FAST_GAP_MS) fastRun++
        return if (fastRun >= FAST_EVENTS && gap > FAST_GAP_MS) Delete.WORD else Delete.CHAR
    }

    companion object {
        /** The keyboard's repeat is at least this fast (ms) after its ramp; a person's taps never are. */
        const val FAST_GAP_MS = 70L
        /** Fast events in a row that make a keyboard repeat. */
        const val FAST_EVENTS = 8
        /** A gap longer than this ends the run (the key was let go). The slow phase's gap is ~220 ms. */
        const val RUN_BREAK_MS = 450L
    }
}
