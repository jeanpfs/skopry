package app.skopry.android.ui.input

/**
 * The zoom each source was left at. Switching to another app starts from that app's own zoom (1× the first
 * time) instead of carrying over the last app's: a zoom is a position in one picture, and another app's picture
 * has another size and shape. A window is told by its app, not its id (the id changes whenever it is reopened).
 */
class ZoomMemory {
    private val byKey = HashMap<String, StreamZoom>()

    operator fun get(key: String): StreamZoom = byKey[key] ?: StreamZoom.IDENTITY

    operator fun set(key: String, zoom: StreamZoom) {
        if (zoom.zoomed) byKey[key] = zoom else byKey.remove(key)
    }

    fun clear() = byKey.clear()

    companion object {
        const val DESKTOP = "desktop"
        const val NONE = "none"
        fun window(bundleID: String, id: Long) = if (bundleID.isNotEmpty()) "app:$bundleID" else "window:$id"
    }
}
