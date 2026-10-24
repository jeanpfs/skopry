package app.skopry.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skopry.android.client.MenuState
import app.skopry.android.client.SkopryClient
import app.skopry.android.protocol.MacMenuItem
import app.skopry.android.protocol.QualityPreset
import app.skopry.android.protocol.SettingsChange
import app.skopry.android.protocol.StreamSource
import app.skopry.android.video.VideoDecoder

/** True where panels open as a full-width sheet from the bottom (the upright screen), not as a card. */
val LocalSheetPanels = compositionLocalOf { false }

@Composable
private fun PanelFrame(modifier: Modifier, title: String, onDone: () -> Unit, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val sheet = LocalSheetPanels.current
    Column(
        (if (sheet) modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        else modifier.padding(8.dp).widthIn(max = 380.dp).fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(18.dp)))
            .background(Color(0xFF1C1D20))
            .clickable(interactionSource = null, indication = null) {},   // taps inside never reach the scrim
    ) {
        Row(Modifier.fillMaxWidth().padding(start = if (back != null) 4.dp else 18.dp, end = 6.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
        content()
    }
}

/**
 * The Apps drawer: every installed app on the Mac (an app that is open goes to its window, a closed one is
 * launched and streams its first window). The Desktop and the windows already open are on the windows rail, so they are
 * not listed here again.
 */
@Composable
fun AppsPanel(client: SkopryClient, modifier: Modifier, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val launchable = client.apps
    PanelFrame(modifier, "Apps", onDone) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp))
            }
            val shown = launchable.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            if (shown.isEmpty()) item {
                Text(if (launchable.isEmpty()) "No apps yet." else "No app matches.", fontSize = 14.sp, color = SkopryColors.secondaryText,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
            items(shown, key = { "a${it.bundleID}" }) { a ->
                Row(Modifier.fillMaxWidth().clickable {
                    // An app that is open goes to its window (the Mac only streams a window an app has just opened); a closed one is launched.
                    val open = client.listedWindows.firstOrNull { it.bundleID == a.bundleID }
                    if (open != null) client.select(StreamSource.Window(open.id)) else client.launch(a)
                    onDone()
                }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(client, a.bundleID)
                    Spacer(Modifier.size(14.dp))
                    Text(a.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(client: SkopryClient, bundleID: String) {
    val icon = client.icons[bundleID]
    if (icon != null) Image(icon, null, Modifier.size(30.dp))
    else Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(SkopryColors.surfaceHigh))
}

@Composable
private fun Header(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SkopryColors.secondaryText, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
}

/** What Settings says first: the running stream, the away quality, a link that cannot keep up, the encoder. */
@Composable
internal fun SettingsStatus(client: SkopryClient) {
    val state = client.hostState
    val running = state?.stream
    Text(
        listOfNotNull(
            running?.let { "${it.width}×${it.height}" }, running?.let { "${it.fps} fps" }, running?.let { "${it.mbps} Mbps" },
            client.route, client.stats?.rttMs?.takeIf { it >= 0 }?.let { "$it ms" },
        ).joinToString(" · ").ifEmpty { "Connected" },
        fontSize = 13.sp, color = SkopryColors.secondaryText,
    )
    state?.away?.let { a ->
        Text(
            if (a.awayRunning) "Away: ${QualityPreset.shortTitle(a.awayBitrate, a.awayCaptureScale)}"
            else if (a.thisConnectionAway) "Home quality: ${QualityPreset.shortTitle(a.homeBitrate, a.homeCaptureScale)} (a device at home is connected)"
            else "Home quality",
            fontSize = 13.sp, color = SkopryColors.accent,
        )
    }
    state?.link?.takeIf { it.isBehind }?.let { link ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x33FFB020)).padding(12.dp)) {
            val cur = QualityPreset.name(link.bitrate)
            val sug = link.suggestedBitrate?.let { QualityPreset.shortTitle(it, link.suggestedCaptureScale ?: 1.0) }
            Text("The link can’t carry $cur." + (sug?.let { " $it is recommended." } ?: ""), fontSize = 14.sp)
            if (link.suggestedBitrate != null) TextButton(onClick = {
                client.changeSettings(SettingsChange(bitrate = link.suggestedBitrate, captureScale = link.suggestedCaptureScale))
            }) { Text("Use $sug") }
        }
    }
    if (state?.softwareEncoder == true) Note("${client.macName}’s hardware encoder is busy or not responding, so for now streams run at up to 60 fps at Standard.")
}

/**
 * The Settings panel (HostSettingsPanel.swift): the Mac's streaming settings as its menu shows them
 * (only a control's action ever sends, kind 17), this device's own, and Disconnect.
 */
@Composable
fun SettingsPanel(client: SkopryClient, modifier: Modifier, onDone: () -> Unit, onDisconnect: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val rev = client.settingsRevision
    val s = client.shownSettings
    val state = client.hostState
    PanelFrame(modifier, client.macName, onDone) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsStatus(client)
            if (s == null) {
                Text(if (client.hasSettings) "" else "Update Skopry on ${client.macName} to change its settings from here.", fontSize = 14.sp, color = SkopryColors.secondaryText)
            } else {
                Group("Quality")
                QualityPreset.entries.forEach { p ->
                    Choice(p.title + " — ${p.bitrate / 1_000_000} Mbps", s.bitrate == p.bitrate, client.ledger.isPending(app.skopry.android.client.SettingsLedger.Field.BITRATE) && s.bitrate == p.bitrate) {
                        client.changeSettings(SettingsChange(bitrate = p.bitrate))
                    }
                }
                if (QualityPreset.entries.none { it.bitrate == s.bitrate }) Text("Custom — ${QualityPreset.mbps(s.bitrate)} Mbps", fontSize = 14.sp, color = SkopryColors.secondaryText)
                Note("Ultra and Extreme need very fast Wi‑Fi; if the picture lags, step down.")
                Group("Resolution")
                Choice("Retina", s.captureScale >= 1.5) { client.chooseResolution(retina = true) }
                Choice("Standard", s.captureScale < 1.5) { client.chooseResolution(retina = false) }
                Note("The Mac sends at most this panel’s own pixels, so Retina costs only what this screen can show. " +
                    "A stream this device cannot decode drops to Standard by itself.")
                Group("Frame Rate")
                Choice("60 fps", s.maxFPS == 60) { client.changeSettings(SettingsChange(maxFPS = 60)) }
                Choice("120 fps", s.maxFPS == 120) { client.changeSettings(SettingsChange(maxFPS = 120)) }
                Group(" ")
                Toggle("Prioritize Encoding Speed", s.prioritizeSpeed) { client.changeSettings(SettingsChange(prioritizeSpeed = it)) }
                Toggle("Virtual Display", s.virtualDisplay, enabled = s.virtualDisplay || state?.virtualDisplayAvailable == true) {
                    client.changeSettings(SettingsChange(virtualDisplay = it))
                }
                state?.virtualDisplayNote?.let { Note(it) }
                s.directWireless?.let { dw ->
                    Toggle("Direct Wireless Connection", dw, enabled = client.route == null) { client.changeSettings(SettingsChange(directWireless = it)) }
                }
                if (state?.persistent == false) Note("Changes last until SkopryHost quits.")
            }
            Group("This ${deviceWord(androidx.compose.ui.platform.LocalContext.current)}")
            Text("Controls Under", fontSize = 13.sp, color = SkopryColors.secondaryText)
            Choice("Right Thumb", client.rightHanded) { client.useRightHand(true) }
            Choice("Left Thumb", !client.rightHanded) { client.useRightHand(false) }
            Note("Sideways, the controls rail sits on that edge and the windows on the other.")
            Toggle("Three-Finger Gestures", client.gesturesEnabled) { client.setGestures(it) }
            if (client.hostGestures < 1) Note("Update Skopry on ${client.macName} for Mission Control, Spaces and more with three fingers.")
            else Note("Swipe up: Mission Control. Down: App Exposé. Left and right: Spaces. Pinch: Apps. Spread: Desktop.")
            Toggle("Show Stream Stats", client.showHud) { client.setHud(it) }
            Toggle("Open Keyboard Automatically", client.autoKeyboard, enabled = client.hostTextFocus >= 1) { client.useAutoKeyboard(it) }
            Toggle("Sharper Enlargement (experimental)", client.sharpUpscale) { client.useSharpUpscale(it) }
            Note("Draws the picture through a bicubic filter where the screen enlarges it (Text Size 1×). Text edges are tighter; it adds about a frame of delay.")
            Note(
                if (client.hostTextFocus >= 1) "The keyboard opens when a text field takes focus on ${client.macName}, and closes when it loses it."
                else "Needs Skopry on ${client.macName} with text-focus support (this fork’s Mac app).",
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF6B5E), contentColor = SkopryColors.danger)) {
                Text("Disconnect")
            }
        }
    }
}

@Composable internal fun Group(t: String) = Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SkopryColors.secondaryText, modifier = Modifier.padding(top = 12.dp))
@Composable internal fun Note(t: String) = Text(t, fontSize = 12.sp, color = SkopryColors.tertiaryText, lineHeight = 16.sp)

@Composable
private fun Choice(label: String, selected: Boolean, pending: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (pending) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun Toggle(label: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f), color = if (enabled) Color.White else SkopryColors.tertiaryText)
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

/** The Aa control: how large the Mac renders a window's text here (Viewport.scale), or leave the window alone. */
@Composable
fun ScalePanel(client: SkopryClient, modifier: Modifier, onDone: () -> Unit) {
    PanelFrame(modifier, "Text Size", onDone) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp)) {
            Note("Sizes the Mac window to this screen so its text shows at this scale. Off leaves the window as it is. This is ${client.textScaleSubject()}'s size; each app remembers its own.")
            Spacer(Modifier.size(6.dp))
            Choice("Off", client.textScale == null) { client.chooseTextScale(null) }
            listOf(0.5, 0.75, 1.0, 1.25, 1.5).forEach { v -> Choice(scaleLabel(v), client.textScale == v) { client.chooseTextScale(v) } }
        }
    }
}

/**
 * The Mac's menus (MacMenuButton): the streamed app's menu bar, each menu read from the Mac as it
 * opens; an item chosen here is pressed there.
 */
@Composable
fun MenusPanel(client: SkopryClient, modifier: Modifier, onDone: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val rev = client.menuRevision
    val path = remember { mutableStateListOf<MacMenuItem>() }
    val state = client.menuState
    val current = path.lastOrNull()
    PanelFrame(modifier, current?.title ?: (state.app ?: "Menus"), onDone, back = if (path.isEmpty()) null else ({ path.removeAt(path.lastIndex) })) {
        val opened = current?.id?.let { state.menus[it] }
        // A menu this panel shows that the client no longer holds (a reconnect): ask for it again.
        androidx.compose.runtime.LaunchedEffect(current, opened == null) { if (current != null && opened == null) client.openMenu(current) }
        val items = opened as? MenuState.Menu.Items
        val rows: List<MacMenuItem>? = if (current == null) state.top else items?.items
        val note = if (current == null) state.topNote else (opened as? MenuState.Menu.Failed)?.why ?: items?.note
        // A stale read (the app did not answer in time): shown, never chosen from.
        val stale = if (current == null) state.topStale else items?.stale == true
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            note?.let { n -> item { Text(n, fontSize = 13.sp, color = SkopryColors.secondaryText, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) } }
            if (rows == null && note == null) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            items(rows ?: emptyList()) { item ->
                if (item.separator) { HorizontalDivider(color = SkopryColors.outline, modifier = Modifier.padding(vertical = 4.dp)); return@items }
                val enabled = item.enabled && !(stale && current != null && !item.submenu)
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled || current == null) {
                        if (current == null || item.submenu) { path.add(item); client.openMenu(item) }
                        else { client.chooseMenuItem(item); onDone() }
                    }.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(22.dp)) { item.mark?.let { Text(it, color = SkopryColors.accent, fontSize = 14.sp) } }
                    Text(item.title ?: "", fontSize = 15.sp, color = if (enabled) Color.White else SkopryColors.tertiaryText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    item.key?.let { Text(it, fontSize = 13.sp, color = SkopryColors.secondaryText) }
                    if (current == null || item.submenu) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SkopryColors.secondaryText)
                }
            }
            (current?.id?.let { state.menus[it] } as? MenuState.Menu.Items)?.more?.let { more ->
                item { Note("…and $more more on the Mac.") }
            }
        }
    }
}
