package app.skopry.android.video

/**
 * When a decoder that is waiting for a keyframe in the middle of a stream asks the Mac for one (kind 44).
 * A still window never sends a keyframe by itself, so a decoder that dropped its backlog, or was made again
 * with nothing to replay, would otherwise keep the old picture until the window changed.
 *
 * The first ask goes at once, but never within [spacingMs] of the one before (every ask costs every device a
 * keyframe). While the wait goes on it asks again after 4, 8 and 16 s and then every 32 s: a Mac from before
 * kind 44 skips every ask, and asking costs it nothing. Times are in milliseconds from any one clock.
 */
class KeyframeAsk(private val spacingMs: Long = 2_000, private val repeatsMs: LongArray = longArrayOf(4_000, 8_000, 16_000, 32_000)) {
    private var open = false
    private var lastAskAt = Long.MIN_VALUE
    private var dueAt = 0L
    private var asked = 0

    /** The decoder started waiting. True: ask now. Already waiting changes nothing. */
    fun begin(now: Long): Boolean {
        if (open) return false
        open = true
        asked = 0
        if (lastAskAt != Long.MIN_VALUE && now - lastAskAt < spacingMs) { dueAt = lastAskAt + spacingMs; return false }
        return ask(now)
    }

    /** True when the wait is open and an ask is due: ask now. */
    fun poll(now: Long): Boolean = open && now >= dueAt && ask(now)

    /** Milliseconds until the next ask is due, or null when nothing is being waited for. */
    fun nextDelay(now: Long): Long? = if (open) maxOf(0L, dueAt - now) else null

    /** A keyframe came (or the stream was forgotten): the wait is over. */
    fun end() { open = false }

    private fun ask(now: Long): Boolean {
        lastAskAt = now
        dueAt = now + repeatsMs[minOf(asked, repeatsMs.size - 1)]
        asked++
        return true
    }
}
