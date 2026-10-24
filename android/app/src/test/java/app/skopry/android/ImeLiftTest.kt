package app.skopry.android

import app.skopry.android.ui.ImeLift
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeLiftTest {
    @Test fun withoutAKeyboardNothingMoves() {
        assertEquals(0f, ImeLift.shift(900f, 1000f, 0f), 0f)
    }

    @Test fun aPointInTheUpperFreePartStays() {
        // 1000 high, keyboard 450: free 550; 80% of it is 440.
        assertEquals(0f, ImeLift.shift(300f, 1000f, 450f), 0f)
        assertEquals(0f, ImeLift.shift(440f, 1000f, 450f), 0f)
    }

    @Test fun aPointUnderTheKeyboardIsBroughtUpToTheFreePart() {
        assertEquals(460f - 0f, ImeLift.shift(900f, 1000f, 450f) + 0f, 10f) // 900 - 440 = 460, capped at 450
        assertEquals(450f, ImeLift.shift(900f, 1000f, 450f), 0f)
        assertEquals(60f, ImeLift.shift(500f, 1000f, 450f), 0f)
    }

    @Test fun neverMovesMoreThanTheKeyboardIsHigh() {
        assertEquals(450f, ImeLift.shift(5000f, 1000f, 450f), 0f)
    }

    @Test fun aKeyboardThatTakesTheWholePanelMovesNothing() {
        assertEquals(0f, ImeLift.shift(900f, 1000f, 1000f), 0f)
        assertEquals(0f, ImeLift.shift(900f, 0f, 100f), 0f)
    }
}
