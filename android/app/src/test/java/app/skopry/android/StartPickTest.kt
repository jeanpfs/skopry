package app.skopry.android

import app.skopry.android.client.StartPick
import app.skopry.android.protocol.StreamSource
import app.skopry.android.protocol.WindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartPickTest {
    private fun w(id: Long, bundle: String) = WindowInfo(id, "t$id", bundle, bundle, 800, 600)
    private val windows = listOf(w(1, "com.apple.Safari"), w(2, "dev.warp.Warp-Stable"), w(3, "dev.warp.Warp-Stable"))

    @Test fun theFavoritesFrontmostWindowIsOpened() {
        assertEquals(StreamSource.Window(2), StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.None))
        assertEquals(StreamSource.Window(2), StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.Desktop))
    }

    @Test fun anAppThatIsNotOpenLeavesTheDesktop() {
        assertNull(StartPick.choose("com.apple.TextEdit", windows, StreamSource.None))
        assertNull(StartPick.choose("dev.warp.Warp-Stable", emptyList(), StreamSource.None))
    }

    @Test fun noFavoriteChangesNothing() {
        assertNull(StartPick.choose(null, windows, StreamSource.None))
        assertNull(StartPick.choose("", windows, StreamSource.None))
    }

    @Test fun aWindowAlreadyStreamedIsNotTakenOver() {
        assertNull(StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.Window(1)))
    }

    @Test fun aResumedSessionOpensWhereItWasLeft() {
        assertEquals(StreamSource.Window(1), StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.None, "app:com.apple.Safari"))
    }

    @Test fun aResumedDesktopStaysTheDesktopEvenWithAFavorite() {
        assertNull(StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.None, "desktop"))
    }

    @Test fun aResumedAppThatClosedFallsBackToTheFavorite() {
        assertEquals(StreamSource.Window(2), StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.None, "app:com.apple.TextEdit"))
        assertNull(StartPick.choose(null, windows, StreamSource.None, "app:com.apple.TextEdit"))
    }

    @Test fun aResumeKeyNamingAWindowByIdIsIgnored() {
        assertEquals(StreamSource.Window(2), StartPick.choose("dev.warp.Warp-Stable", windows, StreamSource.None, "window:7"))
    }
}
