package app.skopry.android.ui

/** A screen edge. */
enum class Side { LEFT, RIGHT;
    val opposite get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * The side rails' geometry, in dp, as proposal B of the Fold redesign draws it (the Fold held open with
 * both hands, thumbs resting on the side edges a little below the middle). Pure, so it is checked on the JVM.
 *
 * Two rails, one under each thumb: the controls under the dominant hand (Apps, the Mac's menus, Aa,
 * Keyboard, Settings), the Mac's windows under the other. Each folds away into a thin handle on its edge
 * and comes back from a tap on the handle or a swipe in from the edge. A panel opens as a drawer beside
 * the controls rail, at the same height.
 */
object RailGeometry {
    const val RAIL_WIDTH = 56f
    /** Every button, a handle and a thumbnail's row: at least the 48 dp a finger needs. */
    const val BUTTON = 48f
    const val HANDLE_WIDTH = 48f
    /** A handle's touch area, tall as the system lets an app take back from its back gesture (200 dp of each edge). */
    const val HANDLE_TOUCH_HEIGHT = 200f
    /** How far a swipe from the edge must go before the rail opens. */
    const val SWIPE_OPEN = 24f
    /** From a screen edge to a drawer's near side: the rail and a small gap. */
    const val DRAWER_OFFSET = 64f
    const val DRAWER_MIN_TOP = 32f
    const val DRAWER_MARGIN_BOTTOM = 16f
    const val AUTO_HIDE_MS = 4000L
    /** The windows list scrolls past this height. */
    const val WINDOW_LIST_MAX = 316f

    /** The side of the controls rail: under the right thumb for a right-handed person. */
    fun controlsSide(rightHanded: Boolean) = if (rightHanded) Side.RIGHT else Side.LEFT
    fun windowsSide(rightHanded: Boolean) = controlsSide(rightHanded).opposite

    /**
     * Where the rails are centred vertically in an area `height` tall: a little below the middle (412 of
     * 704), where a thumb rests, and in the middle of what is left above the keyboard.
     */
    fun centerY(height: Float, keyboardUp: Boolean) = if (keyboardUp) height / 2f else height * (412f / 704f)

    /** A drawer `drawerHeight` tall, centred on `centerY` and kept inside the `height` it may use. */
    fun drawerTop(centerY: Float, drawerHeight: Float, height: Float): Float {
        val lowest = maxOf(DRAWER_MIN_TOP, height - DRAWER_MARGIN_BOTTOM - drawerHeight)
        return (centerY - drawerHeight / 2f).coerceIn(DRAWER_MIN_TOP, lowest)
    }

    /** A drawer `drawerWidth` wide beside the rail on `side`, in an area `width` wide. */
    fun drawerLeft(side: Side, width: Float, drawerWidth: Float) =
        if (side == Side.RIGHT) width - DRAWER_OFFSET - drawerWidth else DRAWER_OFFSET

    /** The height a rail may be: it stays 8 dp from the top and bottom of the area. */
    fun maxRailHeight(height: Float) = height - 16f

    /** The top of a rail `railHeight` tall, centred on `centerY` and kept 8 dp inside the area. */
    fun railTop(centerY: Float, railHeight: Float, height: Float): Float {
        val lowest = maxOf(8f, height - 8f - railHeight)
        return (centerY - railHeight / 2f).coerceIn(8f, lowest)
    }

    /** The windows list's height: its rows, but no more than 316 dp nor what the area leaves beside the count. */
    fun windowListHeight(rows: Int, areaHeight: Float): Float {
        val rowsHeight = rows * (BUTTON + 4f)
        val room = maxRailHeight(areaHeight) - 16f - 32f
        return minOf(rowsHeight, WINDOW_LIST_MAX, maxOf(BUTTON, room))
    }

    /** Whether a swipe that moved `dx` px along x from the edge `side` goes inward far enough to open the rail. */
    fun swipeOpens(side: Side, dx: Float, density: Float): Boolean {
        val inward = if (side == Side.LEFT) dx else -dx
        return inward > SWIPE_OPEN * density
    }
}
