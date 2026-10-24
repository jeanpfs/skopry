package app.skopry.android.ui

/**
 * Where the picture goes when the soft keyboard covers the bottom of the screen. Shrinking it into what is left
 * (the old way) makes the Mac's text a third of its size; instead it keeps its size and moves up, just far
 * enough that the point being typed at stays in the free part, like a form scrolling above the keyboard.
 */
object ImeLift {
    /** The point sits at most this far down the free part: the text with a little room under it. */
    private const val ANCHOR_AT = 0.8f

    /**
     * How far the picture moves up, in px: 0 while the point (`anchorY`, its distance from the top of the panel
     * in px) is already in the upper part of the free area, else what brings it to `ANCHOR_AT` of that area, and
     * never more than the keyboard's own height (the picture's bottom edge stops at the keyboard's top).
     */
    fun shift(anchorY: Float, panelHeight: Float, covered: Float): Float {
        if (covered <= 0f || panelHeight <= 0f) return 0f
        val free = panelHeight - covered
        if (free <= 0f) return 0f
        return (anchorY - free * ANCHOR_AT).coerceIn(0f, covered)
    }
}
