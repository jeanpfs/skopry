package app.skopry.android.ui

import kotlin.math.abs

/** How tall the deck is: only its tab row, about half the screen, or two thirds. */
enum class DeckSize { CLOSED, MEDIUM, TALL }

/** What the deck shows under its tab row. */
enum class DeckTab(val label: String) {
    TRACKPAD("Trackpad"), WINDOWS("Janelas"), KEYS("Teclas"), SETTINGS("Ajustes"),
}

/**
 * The upright screen's geometry, in dp, as proposal C of the portrait redesign draws it (the Fold held
 * open and upright, 704 × 932 dp): the picture on top, and under it one deck whose height the person
 * sets by dragging its handle. The deck takes the room the picture does not need; the picture stays 16:10
 * and shrinks, centred in what is left, when the deck grows. Pure, so it is checked on the JVM.
 */
object DeckGeometry {
    /** The tab row: the only part of the deck left when it is closed, or when the keyboard is up. */
    const val TAB_ROW = 62f
    /** A closed deck: its tab row and the rounded edge above it. */
    const val CLOSED = 76f
    /** 492 of 932 dp on the Fold. */
    const val MEDIUM_FRACTION = 492f / 932f
    /** 640 of 932 dp on the Fold. */
    const val TALL_FRACTION = 640f / 932f
    const val VIDEO_ASPECT = 1.6f

    /** The deck's height for `size` on an area `areaHeight` tall (the screen less the keyboard). */
    fun height(size: DeckSize, areaHeight: Float): Float = when (size) {
        DeckSize.CLOSED -> CLOSED
        DeckSize.MEDIUM -> maxOf(CLOSED, areaHeight * MEDIUM_FRACTION)
        DeckSize.TALL -> maxOf(CLOSED, areaHeight * TALL_FRACTION)
    }

    /** The size nearest a deck dragged to `heightNow`. */
    fun nearest(heightNow: Float, areaHeight: Float): DeckSize =
        DeckSize.entries.minBy { abs(height(it, areaHeight) - heightNow) }

    /** What a tap on the handle does: a closed deck opens, an open one closes, a tall one comes down to medium. */
    fun tapped(size: DeckSize): DeckSize = when (size) {
        DeckSize.CLOSED -> DeckSize.MEDIUM
        DeckSize.MEDIUM -> DeckSize.CLOSED
        DeckSize.TALL -> DeckSize.MEDIUM
    }

    /**
     * The picture's size in an area `width` wide with `room` dp above the deck: 16:10, as wide as the
     * area allows but no taller than the room. 704 × 440 on the Fold with the deck medium, closed or under
     * the keyboard; smaller with the deck tall.
     */
    fun video(width: Float, room: Float): Pair<Float, Float> {
        val h = minOf(width / VIDEO_ASPECT, maxOf(0f, room))
        return h * VIDEO_ASPECT to h
    }
}
