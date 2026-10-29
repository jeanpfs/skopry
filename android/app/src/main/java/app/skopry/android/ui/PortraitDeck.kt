package app.skopry.android.ui
import app.skopry.android.client.TrackpadSpeed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.skopry.android.client.Keys
import app.skopry.android.client.SkopryClient
import app.skopry.android.protocol.KeyMods
import app.skopry.android.protocol.QualityPreset
import app.skopry.android.protocol.SettingsChange
import app.skopry.android.protocol.StreamSource
import app.skopry.android.protocol.WindowInfo

private val DeckColor = SkopryColors.bar

/**
 * The upright screen on a tablet or an open Fold (proposal C of the portrait redesign): the picture on
 * top and one deck under it, where the thumbs are. The deck's tab row holds Apps and the Mac's menu at
 * its ends, four tabs (Trackpad, Janelas, Teclas, Ajustes) and, between them, a handle that sets its
 * height: dragged or tapped, closed to its tab row, about half the screen, or two thirds. The picture
 * keeps its 16:10 and takes the room the deck leaves. With the keyboard up the deck is its tab row, just
 * above the keyboard. Apps and the Mac's menu open as a sheet from the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitDeckScreen(
    client: SkopryClient,
    latch: Latch,
    overlay: Overlay,
    keyboardUp: Boolean,
    onOpen: (Overlay) -> Unit,
    onCloseOverlay: () -> Unit,
    onKeyboard: () -> Unit,
    onTapped: () -> Unit,
    onTouched: () -> Boolean,
    panel: @Composable (Modifier) -> Unit,
) {
    val d = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(DeckSize.MEDIUM) }
    var tab by remember { mutableStateOf(DeckTab.TRACKPAD) }
    var dragging by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val areaW = maxWidth.value
        val areaH = maxHeight.value
        val shown = if (keyboardUp) DeckSize.CLOSED else size
        val target = DeckGeometry.height(shown, areaH)
        val deckH = remember { Animatable(target) }
        LaunchedEffect(target, dragging) {
            if (!dragging) deckH.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }

        // The picture follows the deck's settled height, not each frame of its move: a SurfaceView resized
        // every frame tears. It is centred in what the deck leaves, 16:10.
        val (vw, vh) = DeckGeometry.video(areaW, areaH - target)
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height((areaH - target).dp), contentAlignment = Alignment.Center) {
            VideoPanel(client, Modifier.size(vw.dp, vh.dp), onTouched = onTouched, onTapped = onTapped)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(deckH.value.dp)
                .clip(SkopryShapes.sheetTop).background(DeckColor),
        ) {
            DeckTabRow(
                client = client, tab = tab, size = shown, keyboardUp = keyboardUp, overlay = overlay,
                onTab = { t -> tab = t; if (size == DeckSize.CLOSED) size = DeckSize.MEDIUM },
                onApps = { onOpen(Overlay.APPS) }, onMenu = { onOpen(Overlay.MENUS) },
                onHandleTap = { if (!keyboardUp) size = DeckGeometry.tapped(size) },
                onDragStart = { dragging = true },
                onDrag = { dy ->
                    if (!keyboardUp) {
                        val tall = DeckGeometry.height(DeckSize.TALL, areaH)
                        scope.launch { deckH.snapTo((deckH.value - dy / d).coerceIn(DeckGeometry.CLOSED, tall)) }
                    }
                },
                onDragEnd = {
                    if (!keyboardUp) size = DeckGeometry.nearest(deckH.value, areaH)
                    dragging = false
                },
            )
            if (shown != DeckSize.CLOSED) Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DeckTab.TRACKPAD -> TrackpadPane(client, latch, keyboardUp, onKeyboard)
                    DeckTab.WINDOWS -> WindowsPane(client)
                    DeckTab.KEYS -> KeysPane(client, latch, keyboardUp, onKeyboard)
                    DeckTab.SETTINGS -> SettingsPane(client) { client.disconnect() }
                }
            }
        }

        // Apps and the Mac's menu: a sheet from the bottom over the deck, the picture dimmed.
        if (overlay != Overlay.NONE) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = null, indication = null) { onCloseOverlay() })
            CompositionLocalProvider(LocalSheetPanels provides true) { panel(Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
private fun DeckTabRow(
    client: SkopryClient, tab: DeckTab, size: DeckSize, keyboardUp: Boolean, overlay: Overlay,
    onTab: (DeckTab) -> Unit, onApps: () -> Unit, onMenu: () -> Unit,
    onHandleTap: () -> Unit, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    // Apps and the first two tabs sit under the left thumb, the other two and Menu under the right one;
    // a left-handed person has them the other way round.
    val rightHanded = client.rightHanded
    val tabsA = listOf(DeckTab.TRACKPAD, DeckTab.WINDOWS)
    val tabsB = listOf(DeckTab.KEYS, DeckTab.SETTINGS)
    val left: @Composable RowScope.() -> Unit = {
        ActionButton("Apps", Icons.Filled.Apps, overlay == Overlay.APPS, true, onApps)
        TabGroup(tabsA, tab, Modifier.weight(2f), onTab)
    }
    val right: @Composable RowScope.() -> Unit = {
        TabGroup(tabsB, tab, Modifier.weight(2f), onTab)
        ActionButton("Menu", Icons.Filled.Menu, overlay == Overlay.MENUS, client.hasMenus, onMenu)
    }
    Row(
        Modifier.fillMaxWidth().height(DeckGeometry.TAB_ROW.dp).padding(start = 8.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rightHanded) { left(); Handle(size, Modifier.weight(1f), onHandleTap, onDragStart, onDrag, onDragEnd); right() }
        else { right(); Handle(size, Modifier.weight(1f), onHandleTap, onDragStart, onDrag, onDragEnd); left() }
    }
}

/** The handle between the tabs: a pill and three dots for the deck's three heights. Drag it, or tap it. */
@Composable
private fun Handle(
    size: DeckSize, modifier: Modifier,
    onTap: () -> Unit, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    Column(
        modifier.widthIn(min = 56.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onVerticalDrag = { change, dy -> change.consume(); onDrag(dy) },
                    onDragEnd = onDragEnd, onDragCancel = onDragEnd,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.42f)))
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DeckSize.entries.forEach { s ->
                val on = s == size
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (on) 0.75f else 0.2f)))
            }
        }
    }
}

@Composable
private fun TabGroup(tabs: List<DeckTab>, selected: DeckTab, modifier: Modifier, onTab: (DeckTab) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(SkopryColors.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { t -> TabButton(t, t == selected, Modifier.weight(1f).widthIn(max = 104.dp)) { onTab(t) } }
    }
}

private fun tabIcon(t: DeckTab): ImageVector = when (t) {
    DeckTab.TRACKPAD -> Icons.Filled.TouchApp
    DeckTab.WINDOWS -> Icons.Filled.ViewCarousel
    DeckTab.KEYS -> Icons.Filled.KeyboardAlt
    DeckTab.SETTINGS -> Icons.Filled.Tune
}

@Composable
private fun TabButton(t: DeckTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) SkopryColors.surfaceHigh else Color.Transparent)
            .clickable(onClickLabel = t.label, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tabIcon(t), null, Modifier.size(20.dp), tint = if (selected) SkopryColors.accent else SkopryColors.secondaryText)
        Text(t.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (selected) Color.White else SkopryColors.secondaryText)
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(52.dp, 56.dp).clip(RoundedCornerShape(14.dp)).background(if (on) SkopryColors.surfaceHigh else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val tint = if (enabled) Color.White else SkopryColors.tertiaryText
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

// MARK: Trackpad

@Composable
private fun TrackpadPane(client: SkopryClient, latch: Latch, keyboardUp: Boolean, onKeyboard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 14.dp)) {
        KeyRow(client, latch, full = true, onKeyboard = onKeyboard, keyboardUp = keyboardUp)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Trackpad(client, latch, Modifier.fillMaxSize(), span = null)
            Text("TRACKPAD", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SkopryColors.secondaryText, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
            Text(
                "Toque: clique  ·  Segurar: clique direito  ·  2 dedos: rolar  ·  3–4 dedos: gestos do Mac",
                fontSize = 12.sp, color = SkopryColors.secondaryText, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

// MARK: Windows

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowsPane(client: SkopryClient) {
    val haptics = LocalHapticFeedback.current
    var menuFor by remember { mutableStateOf<Long?>(null) }
    LazyVerticalGrid(
        GridCells.Adaptive(180.dp), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val n = client.windows.size
            Text("Desktop + $n ${if (n == 1) "janela" else "janelas"}", fontSize = 13.sp, color = SkopryColors.secondaryText)
        }
        item(key = "desktop") {
            WindowCard(selected = client.active == StreamSource.Desktop, title = "Desktop", subtitle = "Tela inteira do Mac",
                onClick = { client.select(StreamSource.Desktop) }, onLongClick = {}) {
                Icon(Icons.Filled.DesktopMac, null, Modifier.size(40.dp), tint = SkopryColors.accent)
            }
        }
        items(client.listedWindows, key = { it.id }) { w ->
            val selected = (client.active as? StreamSource.Window)?.id == w.id
            Box {
                WindowCard(selected, w.appName.ifEmpty { w.title }, w.title,
                    onClick = { client.select(StreamSource.Window(w.id)) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menuFor = w.id }) { DeckThumb(client, w) }
                WindowMenu(client, w, menuFor == w.id) { menuFor = null }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, onLongClick: () -> Unit, thumb: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Stream $title"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).background(SkopryColors.surfaceHigh)
                .border(if (selected) 2.dp else 1.dp, if (selected) SkopryColors.accent else SkopryColors.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            thumb()
            if (selected) Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(SkopryColors.accent))
                Text("Transmitindo", fontSize = 11.sp, color = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = SkopryColors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeckThumb(client: SkopryClient, w: WindowInfo) {
    val thumb = client.thumbnails[w.id]
    if (thumb != null) Image(thumb, w.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else Text(w.appName, fontSize = 12.sp, color = SkopryColors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
    client.icons[w.bundleID]?.let { icon ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { Image(icon, null, modifier = Modifier.padding(6.dp).size(22.dp)) }
    }
}

// MARK: Keys

@Composable
private fun KeysPane(client: SkopryClient, latch: Latch, keyboardUp: Boolean, onKeyboard: () -> Unit) {
    fun press(key: Int) { client.press(key, latch.mods); latch.spend() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigKey("esc", "⎋", false, Modifier.weight(1f).height(56.dp)) { press(Keys.ESCAPE) }
            BigKey("tab", "⇥", false, Modifier.weight(1f).height(56.dp)) { press(Keys.TAB) }
            BigKey("ctrl", "⌃", latch.mods and KeyMods.CONTROL != 0L, Modifier.weight(1f).height(56.dp)) { latch.toggle(KeyMods.CONTROL) }
            BigKey("opt", "⌥", latch.mods and KeyMods.OPTION != 0L, Modifier.weight(1f).height(56.dp)) { latch.toggle(KeyMods.OPTION) }
            BigKey("cmd", "⌘", latch.mods and KeyMods.COMMAND != 0L, Modifier.weight(1f).height(56.dp)) { latch.toggle(KeyMods.COMMAND) }
            BigKey("shift", "⇧", latch.mods and KeyMods.SHIFT != 0L, Modifier.weight(1f).height(56.dp)) { latch.toggle(KeyMods.SHIFT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArrowKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Esquerda") { press(Keys.LEFT) }
            ArrowKey(Icons.Filled.KeyboardArrowUp, "Cima") { press(Keys.UP) }
            ArrowKey(Icons.Filled.KeyboardArrowDown, "Baixo") { press(Keys.DOWN) }
            ArrowKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Direita") { press(Keys.RIGHT) }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (client.active == StreamSource.Desktop) BigKey("Spotlight", "⌘␣", false, Modifier.width(140.dp).height(44.dp)) {
                    client.press(Keys.SPACE, KeyMods.COMMAND); latch.spend()
                }
                BigKey(if (keyboardUp) "Ocultar teclado" else "Teclado", null, keyboardUp, Modifier.width(140.dp).height(44.dp)) { onKeyboard() }
            }
        }
    }
}

@Composable
private fun BigKey(label: String, glyph: String?, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (lit) SkopryColors.primary else SkopryColors.surfaceHigh).clickable(onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val tint = Color.White
        if (glyph != null) Text(glyph, fontSize = 18.sp, color = tint)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun ArrowKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(SkopryColors.surfaceHigh).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(36.dp), tint = Color.White) }
}

// MARK: Settings

/** A row of choices: one selected, each at least 48 dp tall. */
@Composable
private fun <T> Seg(options: List<Triple<T, String, String?>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SkopryColors.surface).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label, sub) ->
            val on = value == selected
            Column(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(if (on) SkopryColors.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (on) SkopryColors.accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(value) }.padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (on) SkopryColors.accent else Color.White)
                if (sub != null) Text(sub, fontSize = 11.sp, maxLines = 1, color = SkopryColors.secondaryText)
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SkopryColors.secondaryText)
        content()
    }
}

@Composable
private fun SettingsPane(client: SkopryClient, onDisconnect: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val rev = client.settingsRevision
    val s = client.shownSettings
    val state = client.hostState
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsStatus(client)
        SettingGroup("Tamanho do texto (Aa)") {
            Seg(
                listOf<Triple<Double?, String, String?>>(
                    Triple(null, "Off", null), Triple(0.5, "½×", null), Triple(0.75, "¾×", null),
                    Triple(1.0, "1×", null), Triple(1.25, "1¼×", null), Triple(1.5, "1½×", null),
                ),
                client.textScale,
            ) { client.chooseTextScale(it) }
        }
        if (s != null) {
            SettingGroup("Qualidade") {
                Seg(QualityPreset.entries.map { Triple(it.bitrate, it.title, "${it.bitrate / 1_000_000} Mbps") }, s.bitrate) {
                    client.changeSettings(SettingsChange(bitrate = it))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingGroup("Resolução", Modifier.weight(1f)) {
                    Seg(listOf(Triple(true, "Retina", null), Triple(false, "Standard", null)), s.captureScale >= 1.5) { client.chooseResolution(it) }
                }
                SettingGroup("Taxa de quadros", Modifier.weight(1f)) {
                    Seg(listOf(Triple(60, "60 fps", null), Triple(120, "120 fps", null)), s.maxFPS) { client.changeSettings(SettingsChange(maxFPS = it)) }
                }
                SettingGroup("Controles sob o polegar", Modifier.weight(1f)) {
                    Seg(listOf(Triple(false, "Esquerdo", null), Triple(true, "Direito", null)), client.rightHanded) { client.useRightHand(it) }
                }
            }
        } else Text(
            if (client.hasSettings) "" else "Update Skopry on ${client.macName} to change its settings from here.",
            fontSize = 14.sp, color = SkopryColors.secondaryText,
        )
        SettingGroup("Trackpad") {
            Seg(TrackpadSpeed.entries.map { Triple(it, it.title, null) }, client.trackpadSpeed) { client.setTrackpadSpeed(it) }
        }
        SettingGroup("Sound") {
            val sendAudio = client.shownSettings?.sendAudio
            Toggle("Send Audio", sendAudio == true, enabled = sendAudio != null) { client.changeSettings(SettingsChange(sendAudio = it)) }
            if (sendAudio == null) Text("Update Skopry on ${client.macName} for sound.", fontSize = 13.sp, color = SkopryColors.secondaryText)
            state?.audioNote?.let { Text(it, fontSize = 13.sp, color = SkopryColors.secondaryText) }
            Toggle("Mute", client.audioMuted) { client.useAudioMuted(it) }
            client.audioStatus?.let { Text(it, fontSize = 13.sp, color = SkopryColors.secondaryText) }
        }
        SettingGroup("Picture") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { client.savePicture() }, modifier = Modifier.weight(1f).height(48.dp), shape = SkopryShapes.control, colors = ButtonDefaults.buttonColors(containerColor = SkopryColors.surface)) { Text("Save Picture") }
                Button(onClick = { client.sharePicture() }, modifier = Modifier.weight(1f).height(48.dp), shape = SkopryShapes.control, colors = ButtonDefaults.buttonColors(containerColor = SkopryColors.surface)) { Text("Share…") }
            }
        }
        SettingGroup("Transmissão e entrada") {
            val toggles = buildList<@Composable () -> Unit> {
                if (s != null) {
                    add { Toggle("Prioritize Encoding Speed", s.prioritizeSpeed) { client.changeSettings(SettingsChange(prioritizeSpeed = it)) } }
                    add {
                        Toggle("Virtual Display", s.virtualDisplay, enabled = s.virtualDisplay || state?.virtualDisplayAvailable == true) {
                            client.changeSettings(SettingsChange(virtualDisplay = it))
                        }
                    }
                    s.directWireless?.let { dw -> add { Toggle("Direct Wireless Connection", dw, enabled = client.route == null) { client.changeSettings(SettingsChange(directWireless = it)) } } }
                }
                add { Toggle("Three-Finger Gestures", client.gesturesEnabled) { client.setGestures(it) } }
                add { Toggle("Reverse Scrolling", client.reverseScroll) { client.useReverseScroll(it) } }
                add { Toggle("Show Stream Stats", client.showHud) { client.setHud(it) } }
                add { Toggle("Open Keyboard Automatically", client.autoKeyboard, enabled = client.hostTextFocus >= 1) { client.useAutoKeyboard(it) } }
                add { Toggle("Sharper Enlargement (experimental)", client.sharpUpscale) { client.useSharpUpscale(it) } }
            }
            toggles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { Box(Modifier.weight(1f)) { it() } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Button(
            onClick = onDisconnect, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = SkopryShapes.control, colors = ButtonDefaults.buttonColors(containerColor = SkopryColors.live.copy(alpha = 0.15f), contentColor = SkopryColors.danger),
        ) { Text("Disconnect") }
    }
}
