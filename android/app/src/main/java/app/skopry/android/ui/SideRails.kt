package app.skopry.android.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import app.skopry.android.client.SkopryClient
import app.skopry.android.protocol.StreamSource
import app.skopry.android.protocol.WindowInfo
import kotlin.math.roundToInt

/** Which rails are out, and the clock that folds them away. Held by the stream screen, which also needs to know. */
class RailsState {
    var controlsOpen by mutableStateOf(false)
    var windowsOpen by mutableStateOf(false)
    /** Bumped by every touch on a rail: each one gives the rails four more seconds. */
    var touches by mutableIntStateOf(0)
    val anyOpen get() = controlsOpen || windowsOpen
    fun closeAll() { controlsOpen = false; windowsOpen = false }
    fun touch() { touches++ }
}

private val RailColor = SkopryColors.bar

/**
 * The stream's controls as two rails on the side edges (proposal B of the Fold redesign): the controls
 * under the dominant thumb and the Mac's windows under the other, each folded into a thin handle until a
 * tap on it or a swipe in from the edge brings it out, and folding away again after four seconds
 * untouched. A panel opens as a drawer beside the controls rail. The picture has the whole screen.
 */
@Composable
fun SideRails(
    client: SkopryClient,
    rails: RailsState,
    overlay: Overlay,
    keyboardUp: Boolean,
    onOpen: (Overlay) -> Unit,
    onKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
) {
    val d = LocalDensity.current.density
    var area by remember { mutableStateOf(IntSize.Zero) }
    val areaH = area.height / d
    val cy = RailGeometry.centerY(areaH, keyboardUp)
    val controlsSide = RailGeometry.controlsSide(client.rightHanded)
    val windowsSide = controlsSide.opposite
    var menuFor by remember { mutableStateOf<Long?>(null) }

    // Four seconds untouched fold the rails away; a panel or a window's menu being open holds them.
    LaunchedEffect(rails.controlsOpen, rails.windowsOpen, overlay, menuFor, rails.touches) {
        if (rails.anyOpen && overlay == Overlay.NONE && menuFor == null) {
            delay(RailGeometry.AUTO_HIDE_MS)
            rails.closeAll()
        }
    }

    val controlsSlide by animateFloatAsState(if (rails.controlsOpen) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "controls")
    val windowsSlide by animateFloatAsState(if (rails.windowsOpen) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "windows")

    Box(modifier.fillMaxSize().onSizeChanged { area = it }) {
        if (area == IntSize.Zero) return@Box

        // What is left of a folded rail: a thin pill on the edge at the thumb's height. A tap on it or a swipe
        // in from the edge brings the rail out.
        EdgeHandle(controlsSide, cy, 1f - controlsSlide, d, "Mostrar controles") { rails.controlsOpen = true; rails.touch() }
        EdgeHandle(windowsSide, cy, 1f - windowsSlide, d, "Mostrar janelas") { rails.windowsOpen = true; rails.touch() }

        if (controlsSlide > 0.001f) EdgeAnchored(controlsSide, cy, controlsSlide, d) {
            ControlsRail(controlsSide, client, overlay, keyboardUp, onOpen, onKeyboard) { rails.touch() }
        }
        if (windowsSlide > 0.001f) EdgeAnchored(windowsSide, cy, windowsSlide, d) {
            WindowsRail(windowsSide, client, areaH, menuFor, { menuFor = it }) { rails.touch() }
        }

        if (overlay != Overlay.NONE) Drawer(controlsSide, cy, d, content = panel)
    }
}

/**
 * The handle of a folded rail: a 5 × 44 dp pill on the edge inside a 48 × 200 dp area that takes a tap or a
 * swipe inward. Android's own back gesture owns the screen edge, so the area is excluded from it (the system
 * allows 200 dp of each edge) and sits where the thumb is.
 */
@Composable
private fun EdgeHandle(side: Side, cy: Float, visible: Float, d: Float, label: String, onOpen: () -> Unit) {
    if (visible <= 0.001f) return
    val open by rememberUpdatedState(onOpen)
    EdgeAnchored(side, cy, 1f, d) {
        Box(
            Modifier.size(RailGeometry.HANDLE_WIDTH.dp, RailGeometry.HANDLE_TOUCH_HEIGHT.dp).systemGestureExclusion()
                .alpha(visible.coerceIn(0f, 1f))
                .semantics { contentDescription = label; role = Role.Button }
                .pointerInput(side) { detectTapGestures(onTap = { open() }) }
                .pointerInput(side) {
                    var travelled = 0f
                    detectHorizontalDragGestures(onDragStart = { travelled = 0f }) { change, dx ->
                        change.consume()
                        travelled += dx
                        if (RailGeometry.swipeOpens(side, travelled, d)) { travelled = 0f; open() }
                    }
                },
        ) {
            Box(
                Modifier.align(if (side == Side.RIGHT) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 4.dp).size(5.dp, 44.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.42f)),
            )
        }
    }
}

/**
 * Puts `content` on the edge `side`, centred on `centerY` dp and kept 8 dp inside the area, `slide` of the
 * way out from behind the edge (1: fully shown).
 */
@Composable
private fun EdgeAnchored(side: Side, centerY: Float, slide: Float, d: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val p = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = (RailGeometry.maxRailHeight(h / d) * d).toInt().coerceAtLeast(0)),
        )
        val top = (RailGeometry.railTop(centerY, p.height / d, h / d) * d).roundToInt()
        val hidden = ((p.width + 4f * d) * (1f - slide)).roundToInt()
        val x = if (side == Side.RIGHT) w - p.width + hidden else -hidden
        layout(w, h) { p.place(x, top) }
    }
}

/** The panel as a drawer beside the controls rail, centred on the rail's height and kept inside the screen. */
@Composable
private fun Drawer(side: Side, centerY: Float, d: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val room = (h - (RailGeometry.DRAWER_MIN_TOP + RailGeometry.DRAWER_MARGIN_BOTTOM) * d).toInt().coerceAtLeast(0)
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = room))
        // The panels carry 8 dp of padding of their own, so their card sits 64 dp from the edge.
        val pad = 8f
        val left = (RailGeometry.drawerLeft(side, w / d, p.width / d) + if (side == Side.RIGHT) pad else -pad) * d
        val top = RailGeometry.drawerTop(centerY, p.height / d, h / d) * d
        layout(w, h) { p.place(left.roundToInt(), top.roundToInt()) }
    }
}

private fun railShape(side: Side) =
    if (side == Side.RIGHT) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)

@Composable
private fun ControlsRail(
    side: Side, client: SkopryClient, overlay: Overlay, keyboardUp: Boolean,
    onOpen: (Overlay) -> Unit, onKeyboard: () -> Unit, touch: () -> Unit,
) {
    Column(
        Modifier.width(RailGeometry.RAIL_WIDTH.dp).clip(railShape(side)).background(RailColor)
            .border(1.dp, SkopryColors.outline, railShape(side)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailButton("Apps", Icons.Filled.Apps, null, overlay == Overlay.APPS) { touch(); onOpen(Overlay.APPS) }
        RailButton("Menu do Mac", Icons.Filled.Menu, null, overlay == Overlay.MENUS, enabled = client.hasMenus) { touch(); onOpen(Overlay.MENUS) }
        RailButton("Tamanho do texto", null, "Aa", overlay == Overlay.SCALE) { touch(); onOpen(Overlay.SCALE) }
        Box(Modifier.width(28.dp).height(1.dp).background(SkopryColors.outline))
        RailButton("Teclado", Icons.Filled.Keyboard, null, keyboardUp) { touch(); onKeyboard() }
        RailButton("Ajustes", Icons.Filled.Tune, null, overlay == Overlay.SETTINGS) { touch(); onOpen(Overlay.SETTINGS) }
    }
}

@Composable
private fun RailButton(label: String, icon: ImageVector?, text: String?, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(RailGeometry.BUTTON.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(14.dp))
            .background(if (on) SkopryColors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (on) SkopryColors.accent else Color.White
        if (icon != null) Icon(icon, label, Modifier.size(24.dp), tint = tint)
        else Text(text.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowsRail(
    side: Side, client: SkopryClient, areaH: Float, menuFor: Long?, setMenuFor: (Long?) -> Unit, touch: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) touch() }
    val haptics = LocalHapticFeedback.current
    val rows = client.windows.size + 1
    Column(
        Modifier.width(RailGeometry.RAIL_WIDTH.dp).clip(railShape(side)).background(RailColor)
            .border(1.dp, SkopryColors.outline, railShape(side)).padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            Modifier.height(RailGeometry.windowListHeight(rows, areaH).dp), state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "desktop") {
                RailTile(selected = client.active == StreamSource.Desktop, label = "Desktop", onClick = {
                    touch(); client.select(StreamSource.Desktop)
                }, onLongClick = {}) {
                    Icon(Icons.Filled.DesktopMac, null, Modifier.size(22.dp), tint = if (client.active == StreamSource.Desktop) SkopryColors.accent else Color.White)
                }
            }
            items(client.listedWindows, key = { it.id }) { w ->
                val selected = (client.active as? StreamSource.Window)?.id == w.id
                Box {
                    RailTile(selected, "Stream ${w.title}", onClick = { touch(); client.select(StreamSource.Window(w.id)) }, onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress); touch(); setMenuFor(w.id)
                    }) { WindowThumb(client, w) }
                    WindowMenu(client, w, menuFor == w.id) { setMenuFor(null) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$rows", fontSize = 11.sp, color = SkopryColors.secondaryText)
    }
}

/** One 48 dp row of the windows rail holding a 44 × 34 thumbnail, ringed in the accent while it streams. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailTile(selected: Boolean, label: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(RailGeometry.BUTTON.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp, 34.dp).clip(RoundedCornerShape(8.dp)).background(SkopryColors.surfaceHigh)
                .border(if (selected) 2.dp else 1.dp, if (selected) SkopryColors.accent else SkopryColors.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun WindowThumb(client: SkopryClient, w: WindowInfo) {
    val thumb = client.thumbnails[w.id]
    if (thumb != null) Image(thumb, w.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else Text(w.appName.take(2), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, color = SkopryColors.secondaryText)
    client.icons[w.bundleID]?.let { icon ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { Image(icon, null, modifier = Modifier.padding(2.dp).size(14.dp)) }
    }
}
