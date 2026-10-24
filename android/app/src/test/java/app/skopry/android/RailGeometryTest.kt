package app.skopry.android

import app.skopry.android.ui.RailGeometry
import app.skopry.android.ui.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailGeometryTest {
    @Test fun theControlsAreUnderTheDominantThumbAndTheWindowsUnderTheOther() {
        assertEquals(Side.RIGHT, RailGeometry.controlsSide(rightHanded = true))
        assertEquals(Side.LEFT, RailGeometry.windowsSide(rightHanded = true))
        assertEquals(Side.LEFT, RailGeometry.controlsSide(rightHanded = false))
        assertEquals(Side.RIGHT, RailGeometry.windowsSide(rightHanded = false))
    }

    @Test fun theRailsRestAThumbBelowTheMiddleAndCentreAboveTheKeyboard() {
        assertEquals(412f, RailGeometry.centerY(704f, keyboardUp = false), 0.001f)
        assertEquals(212f, RailGeometry.centerY(424f, keyboardUp = true), 0.001f)
        // The same fraction on any height, so a Fold's cover screen keeps the proportion.
        assertEquals(360f * 412f / 704f, RailGeometry.centerY(360f, keyboardUp = false), 0.001f)
    }

    @Test fun aDrawerIsCentredOnTheRailsHeightAndNeverLeavesTheScreen() {
        // Room: centred.
        assertEquals(412f - 150f, RailGeometry.drawerTop(412f, 300f, 704f), 0.001f)
        // A tall one near the bottom is pushed up to keep 16 dp under it.
        assertEquals(704f - 16f - 640f, RailGeometry.drawerTop(412f, 640f, 704f), 0.001f)
        // One taller than the room allows is held at the top margin instead of going negative.
        assertEquals(32f, RailGeometry.drawerTop(212f, 500f, 424f), 0.001f)
    }

    @Test fun aDrawerSitsBesideItsRailOnTheRightSideForTheRightThumb() {
        assertEquals(932f - 64f - 360f, RailGeometry.drawerLeft(Side.RIGHT, 932f, 360f), 0.001f)
        assertEquals(64f, RailGeometry.drawerLeft(Side.LEFT, 932f, 360f), 0.001f)
    }

    @Test fun aRailIsKeptEightDpInsideTheArea() {
        assertEquals(8f, RailGeometry.railTop(20f, 275f, 704f), 0.001f)
        assertEquals(704f - 8f - 275f, RailGeometry.railTop(700f, 275f, 704f), 0.001f)
        // Taller than the area (cannot be, the list is capped): it still does not start above 8.
        assertEquals(8f, RailGeometry.railTop(100f, 900f, 704f), 0.001f)
    }

    @Test fun theWindowListScrollsPast316DpAndFitsASmallScreen() {
        assertEquals(52f, RailGeometry.windowListHeight(1, 704f), 0.001f)
        assertEquals(RailGeometry.WINDOW_LIST_MAX, RailGeometry.windowListHeight(20, 704f), 0.001f)
        // A phone on its side, 360 dp: the list leaves room for the count and padding.
        val small = RailGeometry.windowListHeight(20, 360f)
        assertTrue(small < RailGeometry.WINDOW_LIST_MAX)
        assertTrue(small + 16f + 32f <= RailGeometry.maxRailHeight(360f))
        // Even a very short area (100 dp) keeps one row to touch.
        assertEquals(RailGeometry.BUTTON, RailGeometry.windowListHeight(5, 100f), 0.001f)
    }

    @Test fun aSwipeInFromTheEdgeOpensOnlyPastTheThresholdAndOnlyInward() {
        val d = 2.625f
        assertTrue(RailGeometry.swipeOpens(Side.LEFT, 30f * d, d))
        assertTrue(RailGeometry.swipeOpens(Side.RIGHT, -30f * d, d))
        assertFalse("too short", RailGeometry.swipeOpens(Side.LEFT, 10f * d, d))
        assertFalse("outward on the left", RailGeometry.swipeOpens(Side.LEFT, -40f * d, d))
        assertFalse("outward on the right", RailGeometry.swipeOpens(Side.RIGHT, 40f * d, d))
    }

    @Test fun everyTouchTargetIsAtLeastWhatAFingerNeeds() {
        assertTrue(RailGeometry.BUTTON >= 48f)
        assertTrue(RailGeometry.HANDLE_WIDTH >= 48f && RailGeometry.HANDLE_TOUCH_HEIGHT >= 48f)
    }
}
