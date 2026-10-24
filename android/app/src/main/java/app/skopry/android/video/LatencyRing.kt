package app.skopry.android.video

/**
 * Input queued → output rendered, per presentation time: the decoder's latency, for the HUD's "dec".
 * Outputs come in input order (there are no B-frames), so a ring of the last [SIZE] inputs is enough and
 * no frame boxes a Long. Not thread-safe: the decoder thread owns it.
 */
class LatencyRing {
    private val pts = LongArray(SIZE)
    private val nanos = LongArray(SIZE)
    private var head = 0
    private var count = 0

    val size get() = count

    /** An input went into the codec at `queuedNanos`. When the ring is full the oldest is forgotten. */
    fun queued(ptsUs: Long, queuedNanos: Long) {
        if (count == SIZE) { head = (head + 1) and (SIZE - 1); count-- }
        val i = (head + count) and (SIZE - 1)
        pts[i] = ptsUs; nanos[i] = queuedNanos
        count++
    }

    /**
     * The output for `ptsUs` was rendered at `nowNanos`: the time it took in ms, or null when its input
     * is not here (already forgotten, or never queued). Inputs older than it never came out (a flush, a
     * dropped frame) and are forgotten.
     */
    fun rendered(ptsUs: Long, nowNanos: Long): Long? {
        while (count > 0 && pts[head] < ptsUs) { head = (head + 1) and (SIZE - 1); count-- }
        if (count == 0 || pts[head] != ptsUs) return null
        val t = nanos[head]
        head = (head + 1) and (SIZE - 1); count--
        return (nowNanos - t) / 1_000_000
    }

    fun clear() { head = 0; count = 0 }

    companion object {
        /** A power of two. */
        const val SIZE = 128
    }
}
