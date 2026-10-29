package app.skopry.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skopry.android.client.SkopryClient
import app.skopry.android.protocol.StreamSource

/**
 * The Apps tray, like the Mac's Launchpad and Dock: a rounded shelf over the picture with a search field
 * and a grid of every installed app on the Mac, icon over name, a dot under the ones that are open. An app
 * that is open goes to its window (the Mac only streams a window an app has just opened), a closed one is
 * launched and streams its first window. The Desktop and the windows already open are on the windows rail,
 * so they are not listed here again.
 */
@Composable
fun AppsPanel(client: SkopryClient, modifier: Modifier, onDone: () -> Unit) {
    val sheet = LocalSheetPanels.current
    var query by remember { mutableStateOf("") }
    val launchable = client.apps
    val open = client.listedWindows.map { it.bundleID }.toSet()
    val shown = launchable.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val shape = if (sheet) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(28.dp)
    Column(
        (if (sheet) modifier.fillMaxWidth().heightIn(max = 600.dp)
        else modifier.padding(8.dp).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 640.dp))
            .clip(shape)
            .background(SkopryColors.bar)
            .border(1.dp, SkopryColors.outline, shape)
            .clickable(interactionSource = null, indication = null) {},   // taps inside never reach the scrim
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = SkopryColors.secondaryText) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text("Done") }
        }
        if (shown.isEmpty()) {
            Text(
                if (launchable.isEmpty()) "No apps yet." else "No app matches.", fontSize = 14.sp, color = SkopryColors.secondaryText,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(82.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { "a${it.bundleID}" }) { a ->
                    val running = a.bundleID in open
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            // An app that is open goes to its window; a closed one is launched.
                            val window = client.listedWindows.firstOrNull { it.bundleID == a.bundleID }
                            if (window != null) client.select(StreamSource.Window(window.id)) else client.launch(a)
                            onDone()
                        }.padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TrayIcon(client, a.bundleID, a.name)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            a.name, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        )
                        Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (running) SkopryColors.secondaryText else Color.Transparent))
                    }
                }
            }
        }
    }
}

/** The app's icon at tray size, or its first letter on a tile until the Mac's icon arrives. */
@Composable
private fun TrayIcon(client: SkopryClient, bundleID: String, name: String) {
    val icon = client.icons[bundleID]
    if (icon != null) Image(icon, null, Modifier.size(58.dp))
    else Box(Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)).background(SkopryColors.surfaceHigh), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = SkopryColors.secondaryText)
    }
}
