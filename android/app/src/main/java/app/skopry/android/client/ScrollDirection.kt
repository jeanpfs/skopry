package app.skopry.android.client

/**
 * Reverse Scrolling: a device-only switch (never sent to the Mac) that turns a finger's drag around, so
 * the content moves the other way. A mouse wheel's scroll is left as the wheel sends it.
 */
object ScrollDirection {
    /** What a finger's scroll distance is multiplied by: -1 reversed, 1 as it always was. */
    fun factor(reversed: Boolean): Double = if (reversed) -1.0 else 1.0
}
