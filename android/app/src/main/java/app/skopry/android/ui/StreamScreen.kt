package app.skopry.android.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import app.skopry.android.client.Keys
import app.skopry.android.client.SkopryClient
import app.skopry.android.protocol.KeyMods
import app.skopry.android.protocol.StreamSource
import app.skopry.android.protocol.WindowInfo
import app.skopry.android.ui.input.KeyboardSink
import app.skopry.android.ui.input.TrackpadView

/** What is open over the stream: at most one panel at a time, as on iOS. */
enum class Overlay { NONE, APPS, SETTINGS, MENUS, SCALE }

/** The key row sideways: its 52 dp and a little air under it. */
private val SIDEWAYS_KEY_ROW = 58.dp

/** A text focus that shows up this soon after a window was picked or a session began came with it, not from a click. */
private const val SWITCH_QUIET_MS = 1500L

/** The key row's latching modifiers (⌃ ⌥ ⌘ ⇧), spent by one key, click or drag. */
class Latch {
    var mods by mutableStateOf(0L)
    fun toggle(bit: Long) { mods = mods xor bit }
    fun spend() { mods = 0L }
}

/**
 * The stream screen (StreamScreen.swift, PortraitStreamScreen.swift) in three arrangements:
 * sideways, the bar over the picture; a phone upright, the picture in a fixed 16:10 pane at the top,
 * then the buttons, the thumbnails, six keys and the trackpad; a tablet or an unfolded Fold upright,
 * the picture in the top half and the bar, a key row and the trackpad below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamScreen(client: SkopryClient) {
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var confirmLeave by remember { mutableStateOf(false) }
    val latch = remember { Latch() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(client.phase) {
        if (client.phase is SkopryClient.Phase.Streaming && client.shouldAskNotifications(context)) {
            client.askedNotifications()
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var sink by remember { mutableStateOf<KeyboardSink?>(null) }
    var keyboardWanted by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeVisible) { if (!imeVisible) keyboardWanted = false }
    // A panel's search field takes focus; once it closes, hardware keys go to the Mac again.
    LaunchedEffect(overlay, sink) { if (overlay == Overlay.NONE) sink?.requestFocus() }

    // A text field takes focus on the Mac: the keyboard comes up by itself, and goes with the focus. Only a
    // keyboard this raised goes away by itself, and one the person put away stays away until the next focus.
    // A focus that arrives with a switch (a window picked, a session just begun) is the app's own, not the
    // person clicking into a field: it waits for a tap on the picture (below) instead of raising the keyboard.
    var autoOpened by remember { mutableStateOf(false) }
    val imeNow by rememberUpdatedState(imeVisible)
    var switchedAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(client.active) { switchedAt = SystemClock.uptimeMillis() }
    LaunchedEffect(client.macTextFocused, sink) {
        val s = sink ?: return@LaunchedEffect
        if (client.macTextFocused) {
            val arrivedWithSwitch = SystemClock.uptimeMillis() - switchedAt < SWITCH_QUIET_MS
            if (!arrivedWithSwitch && !imeNow && !keyboardWanted) { overlay = Overlay.NONE; keyboardWanted = true; autoOpened = true; s.show() }
        } else if (autoOpened) {
            autoOpened = false; keyboardWanted = false; s.hide()
        }
    }
    // A terminal (Terminal, Warp, iTerm) keeps a text area focused the whole time it is in front, so its focus
    // never changes to say "the person clicked the prompt". A click is that: a tap on the picture, with a text
    // field focused and the keyboard away, brings it back. The wait lets the Mac's answer to this very click
    // arrive first (a click on a button moves the focus away from the text). Warp reports one text area for its
    // whole window, tabs included, so a click on a tab is told apart by the window having switched what it
    // shows by then (`TextFocus.switches`): that tap was not into the text.
    var tapped by remember { mutableIntStateOf(0) }
    var switchesAtTap by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapped) {
        if (tapped == 0) return@LaunchedEffect
        val before = switchesAtTap
        delay(250)
        val s = sink ?: return@LaunchedEffect
        if (client.autoKeyboard && client.macTextFocused && client.macTextSwitches == before && !imeNow && !keyboardWanted) {
            overlay = Overlay.NONE; keyboardWanted = true; autoOpened = true; s.show()
        }
    }

    fun toggleKeyboard() {
        val s = sink ?: return
        if (keyboardWanted || imeVisible) { keyboardWanted = false; s.hide() } else { overlay = Overlay.NONE; keyboardWanted = true; s.show() }
    }
    fun open(o: Overlay) {
        if (o != Overlay.NONE && keyboardWanted) { sink?.hide(); keyboardWanted = false }
        overlay = if (overlay == o) Overlay.NONE else o
    }

    // A touch on the picture while a panel is open closes the panel and clicks nothing.
    val closePanel: () -> Boolean = { if (overlay != Overlay.NONE) { overlay = Overlay.NONE; true } else false }
    // Sideways, a rail that is out goes with it: a touch on the picture puts everything away and clicks nothing.
    val rails = remember { RailsState() }
    val closeSideways: () -> Boolean = {
        if (overlay != Overlay.NONE || rails.anyOpen) { overlay = Overlay.NONE; rails.closeAll(); true } else false
    }

    BackHandler(enabled = true) {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            rails.anyOpen -> rails.closeAll()
            else -> confirmLeave = true
        }
    }

    val panelContent: @Composable (Modifier) -> Unit = { m ->
        when (overlay) {
            Overlay.APPS -> AppsPanel(client, m, onDone = { overlay = Overlay.NONE })
            Overlay.SETTINGS -> SettingsPanel(client, m, onDone = { overlay = Overlay.NONE }, onDisconnect = { overlay = Overlay.NONE; client.disconnect() })
            Overlay.MENUS -> MenusPanel(client, m, onDone = { overlay = Overlay.NONE })
            Overlay.SCALE -> ScalePanel(client, m, onDone = { overlay = Overlay.NONE })
            Overlay.NONE -> Unit
        }
    }

    val bar = BarActions(
        client = client, overlay = overlay, keyboardUp = keyboardWanted || imeVisible,
        onApps = { open(Overlay.APPS) }, onSettings = { open(Overlay.SETTINGS) }, onMenus = { open(Overlay.MENUS) },
        onScale = { open(Overlay.SCALE) }, onKeyboard = { toggleKeyboard() },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val portrait = maxHeight > maxWidth
        val wide = maxWidth >= 600.dp
        // The keyboard sink: invisible, it holds the soft keyboard and hardware keys.
        AndroidView(
            factory = { ctx -> KeyboardSink(ctx, client).also { s -> sink = s; s.post { s.requestFocus() } } },
            update = { s -> s.latched = { latch.mods }; s.spendLatch = { latch.spend() } },
            modifier = Modifier.size(1.dp),
        )
        when {
            // Sideways (a Fold open, a tablet, a phone on its side): no bar. The picture has the whole screen and
            // the controls are two rails on the side edges, under the thumbs.
            // The keyboard covers the picture's bottom instead of shrinking it: the Mac's text keeps its size, the
            // picture moves up to keep the point being typed at in view (ImeLift), and the rails stay above the keys.
            !portrait -> Box(Modifier.fillMaxSize()) {
                // With the keyboard up a row of the keys it lacks (esc, tab, ctrl, arrows) rides on top of it, as it does
                // upright; the picture rises above both.
                val keyRowPx = with(LocalDensity.current) { SIDEWAYS_KEY_ROW.roundToPx() }
                VideoPanel(client, Modifier.fillMaxSize(), onTouched = closeSideways, onTapped = { switchesAtTap = client.macTextSwitches; tapped++ },
                    covered = if (imeVisible) imeHeight + keyRowPx else 0)
                Column(Modifier.fillMaxSize().imePadding()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SideRails(
                            client = client, rails = rails, overlay = overlay, keyboardUp = keyboardWanted || imeVisible,
                            onOpen = { open(it) }, onKeyboard = { toggleKeyboard() },
                            panel = { panelContent(Modifier) },
                        )
                    }
                    if (imeVisible) Box(Modifier.fillMaxWidth().height(SIDEWAYS_KEY_ROW), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6111214))) {
                            KeyRow(client, latch, full = true, onKeyboard = { toggleKeyboard() }, keyboardUp = true)
                        }
                    }
                }
            }
            // Upright on a tablet or an open Fold: the picture on top and one deck under it, where the thumbs are.
            wide -> Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                PortraitDeckScreen(
                    client = client, latch = latch, overlay = overlay, keyboardUp = keyboardWanted || imeVisible,
                    onOpen = { open(it) }, onCloseOverlay = { overlay = Overlay.NONE }, onKeyboard = { toggleKeyboard() },
                    onTapped = { switchesAtTap = client.macTextSwitches; tapped++ }, onTouched = closePanel, panel = { panelContent(it) },
                )
            }
            else -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).navigationBarsPadding()) {
                // The picture in a fixed 16:10 pane: nothing below it moves when windows change.
                VideoPanel(client, Modifier.fillMaxWidth().padding(8.dp).aspectRatio(1.6f), onTouched = closePanel, onTapped = { switchesAtTap = client.macTextSwitches; tapped++ })
                PhoneButtons(bar)
                Strip(client, Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp))
                KeyRow(client, latch, full = false, onKeyboard = { toggleKeyboard() }, keyboardUp = keyboardWanted || imeVisible)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
                    // A phone's pad measures vertical motion against its width ÷ 1.6, as the picture is.
                    Trackpad(client, latch, Modifier.fillMaxSize(), span = maxWidth / 1.6f)
                }
            }
        }

        // Panels over the stream: sideways under the bar; a phone upright under the picture, so a
        // change shows in it; a tablet upright under the picture's half. A tap outside closes.
        val panelTop = if (portrait && !wide) maxWidth / 1.6f + 8.dp else 0.dp
        if (portrait && !wide && overlay != Overlay.NONE) Box(Modifier.fillMaxSize().padding(top = panelTop).background(Color(0x66000000))
            .clickable(interactionSource = null, indication = null) { overlay = Overlay.NONE })
        // Upright, the panel hangs from a corner; sideways the bubble's layer places it beside the bubble.
        if (portrait && !wide) Box(Modifier.fillMaxSize().padding(top = panelTop)) {
            panelContent(Modifier.align(if (overlay == Overlay.APPS) Alignment.TopStart else Alignment.TopEnd))
        }

        // The Mac's notices, the reconnect, the HUD.
        client.notice?.let { n ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xE6202226)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(n, fontSize = 14.sp)
            }
        }
        val reconnecting = client.phase as? SkopryClient.Phase.Connecting
        AnimatedVisibility(reconnecting != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator()
                    Text(client.status ?: "Reconnecting…", fontSize = 16.sp)
                    TextButton(onClick = { client.disconnect() }) { Text("Disconnect") }
                }
            }
        }
        if (client.showHud) client.stats?.let { s ->
            Text(
                "${s.fps} fps · age ${s.frameAgeMs} ms · dec ${client.decoder.decodeLatencyMs} ms · rtt ${s.rttMs}/${s.rttMaxMs} ms · ${client.videoSize?.let { "${it.first}×${it.second}" } ?: "–"}",
                fontSize = 11.sp, color = Color(0xFFB6FFB0),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0x99000000)).padding(4.dp),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Disconnect from ${client.macName}?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; client.disconnect() }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

class BarActions(
    val client: SkopryClient, val overlay: Overlay, val keyboardUp: Boolean,
    val onApps: () -> Unit, val onSettings: () -> Unit, val onMenus: () -> Unit, val onScale: () -> Unit,
    val onKeyboard: () -> Unit,
)

/** Row 1 of a phone upright: the bar's buttons, widened to the row. */
@Composable
private fun PhoneButtons(a: BarActions) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WideButton(Icons.Filled.Apps, "Apps", a.overlay == Overlay.APPS, a.onApps)
        WideButton(null, "Aa", a.overlay == Overlay.SCALE, a.onScale, label = a.client.textScale?.let { scaleLabel(it) } ?: "Aa")
        WideButton(Icons.Filled.Keyboard, "Keyboard", a.keyboardUp, a.onKeyboard)
        if (a.client.hasMenus) WideButton(Icons.Filled.Menu, "Menus", a.overlay == Overlay.MENUS, a.onMenus)
        WideButton(Icons.Filled.Tune, "Settings", a.overlay == Overlay.SETTINGS, a.onSettings)
    }
}

@Composable
private fun RowScope.WideButton(icon: ImageVector?, description: String, lit: Boolean, onClick: () -> Unit, label: String? = null) {
    Box(
        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp))
            .background(if (lit) SkopryColors.accent.copy(alpha = 0.25f) else SkopryColors.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, description, tint = if (lit) SkopryColors.accent else Color.White, modifier = Modifier.size(22.dp))
            else Text(label ?: description, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (lit) SkopryColors.accent else Color.White)
            Text(description, fontSize = 10.sp, color = SkopryColors.secondaryText, maxLines = 1)
        }
    }
}

fun scaleLabel(s: Double) = when (s) { 0.5 -> "½×"; 0.75 -> "¾×"; 1.0 -> "1×"; 1.25 -> "1¼×"; 1.5 -> "1½×"; else -> "${s}×" }

/** The Desktop first, then the windows' thumbnails, in the Mac's order; a tap streams one, a long press shows its window's buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Strip(client: SkopryClient, modifier: Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item(key = "desktop") { DesktopTile(client) }
        items(client.listedWindows, key = { it.id }) { w -> Thumb(client, w) }
    }
}

/** The whole Mac desktop as the strip's first square: lit while it streams, which it does when a session starts. */
@Composable
private fun DesktopTile(client: SkopryClient) {
    val active = client.active == StreamSource.Desktop
    Box(
        Modifier.fillMaxHeight().aspectRatio(1.45f).clip(RoundedCornerShape(8.dp))
            .border(if (active) 2.dp else 1.dp, if (active) SkopryColors.accent else SkopryColors.outline, RoundedCornerShape(8.dp))
            .background(SkopryColors.surfaceHigh)
            .clickable(onClickLabel = "Stream the Desktop") { client.select(StreamSource.Desktop) },
    ) {
        Icon(
            Icons.Filled.DesktopMac, "Desktop", Modifier.align(Alignment.Center).size(28.dp),
            tint = if (active) SkopryColors.accent else Color.White,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(client: SkopryClient, w: WindowInfo) {
    val haptics = LocalHapticFeedback.current
    var lights by remember { mutableStateOf(false) }
    val active = (client.active as? StreamSource.Window)?.id == w.id
    Box {
        Box(
            Modifier.fillMaxHeight().aspectRatio(1.45f).clip(RoundedCornerShape(8.dp))
                .border(if (active) 2.dp else 1.dp, if (active) SkopryColors.accent else SkopryColors.outline, RoundedCornerShape(8.dp))
                .background(SkopryColors.surfaceHigh)
                .combinedClickable(
                    onClick = { client.select(StreamSource.Window(w.id)) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); lights = true },
                    onClickLabel = "Stream ${w.title}",
                ),
        ) {
            val thumb = client.thumbnails[w.id]
            if (thumb != null) Image(thumb, w.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(w.appName, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp, vertical = 8.dp))
            client.icons[w.bundleID]?.let { icon ->
                Image(icon, null, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(16.dp))
            }
        }
        WindowMenu(client, w, lights) { lights = false }
    }
}

/** A window's long-press menu: its Close, Minimize and Full Screen, as the Mac's traffic lights. */
@Composable
fun WindowMenu(client: SkopryClient, w: WindowInfo, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(w.title.ifEmpty { w.appName }, fontSize = 13.sp, color = SkopryColors.secondaryText, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).width(220.dp))
        val favorite = w.bundleID.isNotEmpty() && client.favoriteApp == w.bundleID
        if (w.bundleID.isNotEmpty()) DropdownMenuItem(
            leadingIcon = { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null, Modifier.size(18.dp), tint = SkopryColors.accent) },
            text = { Text(if (favorite) "Don't open ${w.appName} first" else "Open ${w.appName} first") },
            onClick = { onDismiss(); client.useFavoriteApp(if (favorite) null else w.bundleID) },
        )
        DropdownMenuItem(leadingIcon = { Dot(SkopryColors.close) }, text = { Text("Close Window") }, onClick = { onDismiss(); client.windowCommand(w.id, "close") })
        DropdownMenuItem(leadingIcon = { Dot(SkopryColors.minimize) }, text = { Text("Minimize") }, onClick = { onDismiss(); client.windowCommand(w.id, "minimize") })
        DropdownMenuItem(leadingIcon = { Dot(SkopryColors.zoom) }, text = { Text("Full Screen") }, onClick = { onDismiss(); client.windowCommand(w.id, "fullScreen") })
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(12.dp).clip(CircleShape).background(c))

/**
 * The key row (PortraitStreamScreen's KeyRow): esc, tab and the latching modifiers; with room, the
 * arrows, the keyboard key and, while the Desktop streams, Spotlight (⌘Space).
 */
@Composable
internal fun KeyRow(client: SkopryClient, latch: Latch, full: Boolean, onKeyboard: () -> Unit, keyboardUp: Boolean) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Cap("esc") { client.press(Keys.ESCAPE, latch.mods); latch.spend() }
        Cap("tab") { client.press(Keys.TAB, latch.mods); latch.spend() }
        Cap("ctrl", lit = latch.mods and KeyMods.CONTROL != 0L) { latch.toggle(KeyMods.CONTROL) }
        Cap("opt", lit = latch.mods and KeyMods.OPTION != 0L) { latch.toggle(KeyMods.OPTION) }
        Cap("cmd", lit = latch.mods and KeyMods.COMMAND != 0L) { latch.toggle(KeyMods.COMMAND) }
        Cap("shift", lit = latch.mods and KeyMods.SHIFT != 0L) { latch.toggle(KeyMods.SHIFT) }
        if (full) {
            CapIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") { client.press(Keys.LEFT, latch.mods); latch.spend() }
            CapIcon(Icons.Filled.KeyboardArrowDown, "Down") { client.press(Keys.DOWN, latch.mods); latch.spend() }
            CapIcon(Icons.Filled.KeyboardArrowUp, "Up") { client.press(Keys.UP, latch.mods); latch.spend() }
            CapIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") { client.press(Keys.RIGHT, latch.mods); latch.spend() }
            CapIcon(Icons.Filled.Keyboard, if (keyboardUp) "Hide the keyboard" else "Show the keyboard", lit = keyboardUp, onClick = onKeyboard)
            if (client.active == StreamSource.Desktop) CapIcon(Icons.Filled.Search, "Spotlight") { client.press(Keys.SPACE, KeyMods.COMMAND); latch.spend() }
        }
    }
}

@Composable
internal fun RowScope.Cap(label: String, lit: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
            .background(if (lit) SkopryColors.accent else SkopryColors.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 14.sp, color = if (lit) Color(0xFF0B1A3D) else Color.White, fontWeight = FontWeight.Medium) }
}

@Composable
internal fun RowScope.CapIcon(icon: ImageVector, description: String, lit: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
            .background(if (lit) SkopryColors.accent else SkopryColors.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (lit) Color(0xFF0B1A3D) else Color.White) }
}

@Composable
internal fun Trackpad(client: SkopryClient, latch: Latch, modifier: Modifier, span: Dp?) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    AndroidView(
        factory = { ctx -> TrackpadView(ctx, client) },
        update = { v ->
            v.verticalSpan = span?.let { with(density) { it.toPx() } }
            v.latched = { latch.mods }
            v.spendLatch = { latch.spend() }
        },
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF16171A)),
    )
}
