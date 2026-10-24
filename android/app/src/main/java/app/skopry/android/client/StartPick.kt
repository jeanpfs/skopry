package app.skopry.android.client

import app.skopry.android.protocol.StreamSource
import app.skopry.android.protocol.WindowInfo
import app.skopry.android.ui.input.ZoomMemory

/** What a session opens on: where it was left when it resumes, else the favorite app's window when it is open, else whatever the Mac streams (the Desktop). */
object StartPick {
    /**
     * The window to ask for when a session begins, or null to leave things as they are. Only when nothing is
     * streamed yet or the Desktop is: a window already streamed (another device is watching it) is not taken
     * from them. Apps are told by bundle id (a window's id changes every time it opens); with several windows
     * of one, the first listed, the frontmost, wins.
     *
     * `resumeKey` is the `ZoomMemory` key of what the session showed when it ended without the person closing
     * it (the connection dropped, the app sat in the background): the Desktop stays the Desktop, an app comes
     * back if it is still open, and when it is not the favorite is tried.
     */
    fun choose(favoriteBundleID: String?, windows: List<WindowInfo>, active: StreamSource, resumeKey: String? = null): StreamSource.Window? {
        if (active is StreamSource.Window) return null
        if (resumeKey == ZoomMemory.DESKTOP) return null
        val resumed = resumeKey?.takeIf { it.startsWith(APP_PREFIX) }?.removePrefix(APP_PREFIX)
        for (bundle in listOf(resumed, favoriteBundleID)) {
            if (bundle.isNullOrEmpty()) continue
            windows.firstOrNull { it.bundleID == bundle }?.let { return StreamSource.Window(it.id) }
        }
        return null
    }

    private const val APP_PREFIX = "app:"
}
