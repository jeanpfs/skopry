package app.skopry.android.ui.input

/** A rectangle in the panel's pixels, without android.graphics so the math is plain JVM. */
data class ZoomFrame(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Pinch zoom of the picture, on this device only: the picture is drawn `scale`× larger (1× to 5×)
 * and the panel shows the part under it. `ox`/`oy` move the enlarged picture from where the centred
 * one would sit. The picture always covers the panel on an axis where it is larger than the panel,
 * so no edge of it can be dragged into view; on an axis where it still fits, it stays centred.
 */
data class StreamZoom(val scale: Float = 1f, val ox: Float = 0f, val oy: Float = 0f) {
    val zoomed get() = scale > 1f

    /** The picture's frame in the panel, `base` being its aspect fit at 1×. */
    fun frame(base: ZoomFrame, panelW: Float, panelH: Float): ZoomFrame {
        val s = scale.coerceIn(1f, MAX)
        val w = base.width * s
        val h = base.height * s
        return ZoomFrame(
            axis(base.left + base.width / 2f, w, ox, panelW),
            axis(base.top + base.height / 2f, h, oy, panelH),
            w, h,
        )
    }

    /**
     * The zoom after the fingers' spread changed by `factor` around `(fx, fy)` and their midpoint
     * moved by `(dx, dy)`: the picture's point under the fingers stays under them.
     */
    fun pinch(factor: Float, fx: Float, fy: Float, dx: Float, dy: Float, base: ZoomFrame, panelW: Float, panelH: Float): StreamZoom {
        if (factor <= 0f || !factor.isFinite()) return this
        val cur = frame(base, panelW, panelH)
        val next = (scale * factor).coerceIn(1f, MAX)
        if (next <= 1f) return IDENTITY
        val w = base.width * next
        val h = base.height * next
        val left = fx - (fx - cur.left) / cur.width * w + dx
        val top = fy - (fy - cur.top) / cur.height * h + dy
        val moved = StreamZoom(
            next,
            left - (base.left + base.width / 2f - w / 2f),
            top - (base.top + base.height / 2f - h / 2f),
        )
        // Store the offsets as clamped, so a drag past an edge does not have to be undone later.
        val f = moved.frame(base, panelW, panelH)
        return StreamZoom(
            next,
            f.left - (base.left + base.width / 2f - w / 2f),
            f.top - (base.top + base.height / 2f - h / 2f),
        )
    }

    private fun axis(center: Float, size: Float, offset: Float, panel: Float): Float =
        if (size <= panel) center - size / 2f
        else (center - size / 2f + offset).coerceIn(panel - size, 0f)

    companion object {
        const val MAX = 5f
        val IDENTITY = StreamZoom()
    }
}
