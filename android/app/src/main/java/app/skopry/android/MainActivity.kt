package app.skopry.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.skopry.android.client.SkopryClient
import app.skopry.android.crypto.PairLink
import app.skopry.android.ui.ConnectScreen
import app.skopry.android.ui.SkopryTheme
import app.skopry.android.ui.StreamScreen

class MainActivity : ComponentActivity() {
    private lateinit var client: SkopryClient
    /** A skopry://pair link another app handed over: always confirmed first (iOS does the same). */
    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        client = SkopryClient.get(this)
        handle(intent)
        setContent {
            SkopryTheme {
                androidx.compose.material3.Surface(color = androidx.compose.ui.graphics.Color.Black, contentColor = androidx.compose.ui.graphics.Color.White) {
                    SkopryApp(client, pendingLink, onLinkAnswered = { pendingLink = null }, onStreaming = ::immersive)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        client.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) client.onStopped()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.data?.takeIf { it.scheme == "skopry" }?.let { pendingLink = it.toString() }
        if (!BuildConfig.DEBUG) return
        // DEBUG only: drive pairing and connecting from adb (the iOS harness's -SkopryPairURL and -SkopryConnect).
        if (intent.getBooleanExtra("forget", false)) client.savedMacs.forEach { client.forget(it) }
        intent.getStringExtra("pairUrl")?.let { Log.i("Skopry", "debug: pairing by link"); client.pairWithLink(it, intent.getStringExtra("pairAddress")) }
        val code = intent.getStringExtra("pairCode")
        val address = intent.getStringExtra("pairAddress")
        if (code != null && address != null && intent.getStringExtra("pairUrl") == null) client.pairWithCode(address, code)
        if (intent.getBooleanExtra("connect", false)) client.savedMacs.firstOrNull()?.let { client.connect(it) }
        if (intent.hasExtra("hud")) client.setHud(intent.getBooleanExtra("hud", false))
        intent.getStringExtra("serviceType")?.let { type ->
            client.debugDiscovery(type, intent.getStringExtra("homeOverride")?.let { app.skopry.android.net.Endpoint.parse(it, 0) })
        }
        intent.getStringExtra("tapHome")?.let { name -> client.homeMacs.firstOrNull { it.serviceName.startsWith(name) }?.let(client::tapHome) }
        intent.getStringExtra("homeCode")?.let { client.pairHomeCode(it) }
    }

    /** While streaming: no system bars (a swipe brings them back for a moment) and the screen stays on. */
    private fun immersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun SkopryApp(client: SkopryClient, pendingLink: String?, onLinkAnswered: () -> Unit, onStreaming: (Boolean) -> Unit) {
    val phase = client.phase
    val streaming = phase is SkopryClient.Phase.Streaming || (phase is SkopryClient.Phase.Connecting && phase.reconnecting)
    LaunchedEffect(streaming) { onStreaming(streaming) }
    if (streaming) StreamScreen(client) else ConnectScreen(client)

    if (pendingLink != null) {
        val name = (PairLink.parse(pendingLink) as? PairLink.Companion.Result.Ok)?.link?.name
        AlertDialog(
            onDismissRequest = onLinkAnswered,
            title = { Text(if (name != null) "Pair with $name?" else "Not a Skopry pairing link") },
            text = { Text(if (name != null) "A link asks this device to pair with $name. Pair only if you opened it from your own Mac." else "The link couldn’t be read.") },
            confirmButton = {
                if (name != null) TextButton(onClick = { onLinkAnswered(); client.pairWithLink(pendingLink) }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = onLinkAnswered) { Text("Cancel") } },
        )
    }
}
