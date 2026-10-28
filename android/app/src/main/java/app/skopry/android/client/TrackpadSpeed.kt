package app.skopry.android.client

/**
 * How far the Mac's pointer travels for a finger drag on the on-screen trackpad. Normal is the
 * original behaviour (a full swipe across the pad is 1.25 of the frame); the others scale that.
 * Stored by name, so reordering or adding a level never changes what a saved choice means.
 */
enum class TrackpadSpeed(val title: String, val multiplier: Double) {
    SLOWEST("Slowest", 0.5),
    SLOW("Slow", 0.75),
    NORMAL("Normal", 1.0),
    FAST("Fast", 1.5),
    FASTEST("Fastest", 2.0);

    companion object {
        /** The level saved under `name`; a missing, unknown or old value is Normal. */
        fun parse(name: String?): TrackpadSpeed = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
