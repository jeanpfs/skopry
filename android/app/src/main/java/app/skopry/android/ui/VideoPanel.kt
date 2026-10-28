package app.skopry.android.ui

import android.graphics.RectF
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.skopry.android.client.SkopryClient
import app.skopry.android.video.SharpVideoView
import app.skopry.android.ui.input.DirectTouchView
import app.skopry.android.ui.input.StreamZoom
import app.skopry.android.ui.input.ZoomFrame
import kotlin.math.roundToInt

/**
 * The video aspect-fit into the panel, centred, in pixels. A fit within half a percent of 1:1 is made
 * exactly 1:1: the Mac captures at this panel's own pixels, and drawn a pixel larger or smaller the
 * picture would be resampled, which is what softens text.
 */
fun fitRect(panel: IntSize, video: Pair<Int, Int>?): RectF? {
    val (vw, vh) = video ?: return null
    if (panel.width <= 0 || panel.height <= 0 || vw <= 0 || vh <= 0) return null
    var s = minOf(panel.width / vw.toFloat(), panel.height / vh.toFloat())
    if (kotlin.math.abs(s - 1f) < 0.005f) s = 1f
    val w = vw * s; val h = vh * s
    val l = (panel.width - w) / 2f; val t = (panel.height - h) / 2f
    return RectF(l, t, l + w, t + h)
}

/**
 * The stream panel (StreamScreen's picture with HEVCDisplayView and InputOverlay): the decoder's
 * SurfaceView at the picture's aspect, the touch overlay over the whole panel, and the one pointer
 * sprite. Reports its size as the Viewport (dp, the Mac's "device points").
 */
@Composable
fun VideoPanel(
    client: SkopryClient, modifier: Modifier, reportSize: Boolean = true, touch: Boolean = true,
    onTouched: () -> Boolean = { false }, onTapped: () -> Unit = {},
    /** Px at the bottom of the panel that the soft keyboard covers: the picture moves up to keep the typed-at point in view (`ImeLift`). */
    covered: Int = 0,
) {
    val density = LocalDensity.current
    var panel by remember { mutableStateOf(IntSize.Zero) }
    val sourceKey = client.sourceKey()
    var zoom by remember(sourceKey) { mutableStateOf(client.zooms[sourceKey]) }
    val fit = fitRect(panel, client.videoSize)
    // The picture as shown: the aspect fit, enlarged by the pinch. Touch, pointer sprite and decoder
    // surface all follow this one rect, so a tap lands under the finger at any zoom.
    // Where the picture is lifted to is decided when the keyboard opens and held until it closes. Followed live it
    // fed back into itself: a scroll moves the Mac's pointer to the finger, the lift follows the pointer, the
    // picture moves under the finger, and the next scroll step is computed from the moved picture.
    val anchorMemory = remember { floatArrayOf(0.5f) }
    if (covered == 0) anchorMemory[0] = client.pointerAnchor().y
    val anchorY = anchorMemory[0]
    // No animation of its own: `covered` follows the keyboard's slide frame by frame, so the picture rises with
    // it. A tween on top of that only made it trail the keyboard.
    val lift = fit?.let { f ->
        val z = zoom.frame(ZoomFrame(f.left, f.top, f.width(), f.height()), panel.width.toFloat(), panel.height.toFloat())
        ImeLift.shift(z.top + anchorY * z.height, panel.height.toFloat(), covered.toFloat())
    } ?: 0f
    val rect = fit?.let { f ->
        val z = zoom.frame(ZoomFrame(f.left, f.top, f.width(), f.height()), panel.width.toFloat(), panel.height.toFloat())
        RectF(z.left, z.top - lift, z.left + z.width, z.top - lift + z.height)
    }
    Box(
        modifier.clipToBounds().onSizeChanged { size ->
            panel = size
            if (reportSize && size.width > 0) with(density) { client.setPanel(size.width.toDp().value.toDouble(), size.height.toDp().value.toDouble(), size.width, size.height) }
        },
    ) {
        if (rect != null && fit != null) {
            // The surface keeps the 1x fit size and is zoomed by a view transform: resizing a SurfaceView
            // every pinch step re-creates its surface (and the decoder's output) and shows as a tremor.
            val w = with(density) { fit.width().toDp() }
            val h = with(density) { fit.height().toDp() }
            val k = rect.width() / fit.width()
            androidx.compose.runtime.key(client.sharpUpscale) {
            AndroidView(
                factory = { ctx ->
                    val picture: SurfaceView = if (client.sharpUpscale) SharpVideoView(ctx, client).apply { pivotX = 0f; pivotY = 0f }
                    else SurfaceView(ctx).apply {
                        pivotX = 0f; pivotY = 0f
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = client.decoder.setSurface(holder.surface).let { }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = client.decoder.setSurface(holder.surface).let { }
                            override fun surfaceDestroyed(holder: SurfaceHolder) = client.decoder.detachSurfaceNow()
                        })
                    }
                    client.pictureView = picture
                    picture
                },
                onRelease = { v -> if (client.pictureView === v) client.pictureView = null },
                update = { v ->
                    v.scaleX = k; v.scaleY = k
                    v.translationX = rect.left - fit.left; v.translationY = rect.top - fit.top
                },
                modifier = Modifier.offset { IntOffset(fit.left.roundToInt(), fit.top.roundToInt()) }.size(w, h),
            )
            }
        }
        if (touch) {
            AndroidView(
                factory = { ctx -> DirectTouchView(ctx, client) },
                update = { v ->
                    v.videoRect = rect ?: RectF(); v.onTouchedPicture = onTouched; v.onTapped = onTapped; v.zoomed = zoom.zoomed
                    v.onPinch = { k, x, y, dx, dy ->
                        if (fit != null) {
                            zoom = zoom.pinch(k, x, y, dx, dy, ZoomFrame(fit.left, fit.top, fit.width(), fit.height()), panel.width.toFloat(), panel.height.toFloat())
                            client.zooms[sourceKey] = zoom
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        PointerSprite(client, rect)
    }
}

/**
 * The one pointer sprite (PointerPresence): the Mac's arrow, in the Mac's cursor shape, while the
 * Mac or another device moved it last; this device's own only after the trackpad moved it.
 */
@Composable
private fun PointerSprite(client: SkopryClient, rect: RectF?) {
    rect ?: return
    val at: Offset = when {
        !client.pointerHere -> client.macPointer ?: return
        client.ownPointerVisible -> client.ownPointer
        else -> return
    }
    val sprite = client.cursor
    Canvas(Modifier.fillMaxSize()) {
        val x = rect.left + at.x * rect.width()
        val y = rect.top + at.y * rect.height()
        if (sprite != null && sprite.width > 0 && sprite.height > 0) {
            val w = sprite.width.dp.toPx(); val h = sprite.height.dp.toPx()
            val left = x - sprite.hotX / sprite.width * w
            val top = y - sprite.hotY / sprite.height * h
            drawImage(
                sprite.image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            )
        } else {
            // A plain arrow, its tip at the point (about 20 dp tall).
            val s = 1.dp.toPx()
            val p = Path().apply {
                moveTo(x, y); lineTo(x, y + 18 * s); lineTo(x + 4.5f * s, y + 13.5f * s); lineTo(x + 8 * s, y + 20 * s)
                lineTo(x + 10.5f * s, y + 19 * s); lineTo(x + 7.5f * s, y + 12.5f * s); lineTo(x + 13 * s, y + 12.5f * s); close()
            }
            drawPath(p, Color.Black)
            drawPath(p, Color.White, style = Stroke(width = 1.5f * s))
        }
    }
}
