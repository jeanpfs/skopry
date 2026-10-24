package app.skopry.android

import app.skopry.android.client.TextScales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextScalesTest {
    @Test fun anAppNeverChosenForFollowsTheDefault() {
        val s = TextScales(1.0)
        assertEquals(1.0, s.scaleFor("dev.warp.Warp-Stable")!!, 0.0)
        assertEquals(1.0, s.scaleFor(null)!!, 0.0)
    }

    @Test fun aChoiceBelongsToTheAppItWasMadeFor() {
        val s = TextScales(1.0)
        s.choose("dev.warp.Warp-Stable", 0.75)
        assertEquals(0.75, s.scaleFor("dev.warp.Warp-Stable")!!, 0.0)
        assertEquals(1.0, s.scaleFor("com.opera.Opera")!!, 0.0)    // another app is untouched
        assertEquals(1.0, s.scaleFor(null)!!, 0.0)                 // and so is the Desktop
    }

    @Test fun theDesktopSetsTheDefaultForAppsWithoutAChoice() {
        val s = TextScales(1.0)
        s.choose("dev.warp.Warp-Stable", 0.75)
        s.choose(null, 1.25)
        assertEquals(1.25, s.scaleFor("com.opera.Opera")!!, 0.0)
        assertEquals(0.75, s.scaleFor("dev.warp.Warp-Stable")!!, 0.0)
    }

    @Test fun offIsAChoiceToo() {
        val s = TextScales(1.0)
        s.choose("com.apple.TextEdit", null)
        assertNull(s.scaleFor("com.apple.TextEdit"))               // Off for it, not "follow the default"
        assertEquals(1.0, s.scaleFor("dev.warp.Warp-Stable")!!, 0.0)
    }

    @Test fun theChoicesSurviveTheirJson() {
        val s = TextScales(1.0)
        s.choose("dev.warp.Warp-Stable", 0.75); s.choose("com.apple.TextEdit", null)
        val back = TextScales.parse(1.0, s.toJson())
        assertEquals(0.75, back.scaleFor("dev.warp.Warp-Stable")!!, 0.0)
        assertNull(back.scaleFor("com.apple.TextEdit"))
        assertEquals(1.0, back.scaleFor("com.opera.Opera")!!, 0.0)
    }

    @Test fun garbageJsonIsNoChoices() {
        assertEquals(0.5, TextScales.parse(0.5, "not json").scaleFor("x")!!, 0.0)
        assertEquals(0.5, TextScales.parse(0.5, null).scaleFor("x")!!, 0.0)
    }
}
