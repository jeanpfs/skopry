package app.skopry.android.video

/**
 * The frames of the stream since its last keyframe, kept so that a decoder that has to be made again (its screen
 * went away, a codec error) can be brought to the current picture at once by decoding them, instead of showing
 * nothing until the Mac's next keyframe (up to 4 s, and 2.7 s measured after one second away from the app).
 * A keyframe starts a new run and drops the old one. A run that would pass `maxBytes` is dropped whole: a partial
 * one would decode to a broken picture, so there is then no replay and the decoder waits for a keyframe, as before.
 */
class GopCache(private val maxBytes: Int = 32 shl 20) {
    class Frame(val data: ByteArray, val ptsUs: Long, val key: Boolean)

    private val frames = ArrayList<Frame>()
    private var bytes = 0
    private var usable = false

    /** The next frame of the stream, in order. */
    fun add(f: Frame) {
        if (f.key) { frames.clear(); bytes = 0; usable = true }
        if (!usable) return
        if (bytes + f.data.size > maxBytes) { clear(); return }
        frames.add(f); bytes += f.data.size
    }

    /** The run to decode again, keyframe first, or null when there is none to trust. */
    fun replay(): List<Frame>? = if (usable && frames.isNotEmpty() && frames[0].key) frames.toList() else null

    val size get() = frames.size

    fun clear() { frames.clear(); bytes = 0; usable = false }
}
