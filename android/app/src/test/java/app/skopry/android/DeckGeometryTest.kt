package app.skopry.android

import app.skopry.android.ui.DeckGeometry
import app.skopry.android.ui.DeckSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckGeometryTest {
    private val fold = 932f   // the open Fold upright, 704 × 932 dp

    @Test fun theThreeHeightsAreTheDesignsOnTheFold() {
        assertEquals(76f, DeckGeometry.height(DeckSize.CLOSED, fold), 0.001f)
        assertEquals(492f, DeckGeometry.height(DeckSize.MEDIUM, fold), 0.01f)
        assertEquals(640f, DeckGeometry.height(DeckSize.TALL, fold), 0.01f)
    }

    @Test fun aShortAreaNeverLetsAnOpenDeckBeShorterThanAClosedOne() {
        // Above the keyboard on a small screen: 100 dp of room.
        for (s in DeckSize.entries) assertTrue(DeckGeometry.height(s, 100f) >= DeckGeometry.CLOSED)
    }

    @Test fun theHeightsAreOrdered() {
        assertTrue(DeckGeometry.height(DeckSize.CLOSED, fold) < DeckGeometry.height(DeckSize.MEDIUM, fold))
        assertTrue(DeckGeometry.height(DeckSize.MEDIUM, fold) < DeckGeometry.height(DeckSize.TALL, fold))
    }

    @Test fun aDraggedDeckSettlesOnTheNearestHeight() {
        assertEquals(DeckSize.CLOSED, DeckGeometry.nearest(90f, fold))
        assertEquals(DeckSize.MEDIUM, DeckGeometry.nearest(400f, fold))
        assertEquals(DeckSize.MEDIUM, DeckGeometry.nearest(560f, fold))
        assertEquals(DeckSize.TALL, DeckGeometry.nearest(580f, fold))
        assertEquals(DeckSize.TALL, DeckGeometry.nearest(900f, fold))
        // Between closed and medium the midpoint decides.
        assertEquals(DeckSize.CLOSED, DeckGeometry.nearest(283f, fold))
        assertEquals(DeckSize.MEDIUM, DeckGeometry.nearest(285f, fold))
    }

    @Test fun aTapOnTheHandleOpensClosesAndBringsATallDeckDown() {
        assertEquals(DeckSize.MEDIUM, DeckGeometry.tapped(DeckSize.CLOSED))
        assertEquals(DeckSize.CLOSED, DeckGeometry.tapped(DeckSize.MEDIUM))
        assertEquals(DeckSize.MEDIUM, DeckGeometry.tapped(DeckSize.TALL))
    }

    @Test fun theMediumDeckLeavesTheWholePictureAndATallOneShrinksIt() {
        // 704 wide: 440 tall is the 16:10 picture; the room above a medium deck is 440.
        val medium = DeckGeometry.video(704f, 932f - 492f)
        assertEquals(704f, medium.first, 0.5f)
        assertEquals(440f, medium.second, 0.5f)
        // Closed: more room than the picture needs, so it stays 704 × 440.
        val closed = DeckGeometry.video(704f, 932f - 76f)
        assertEquals(440f, closed.second, 0.5f)
        // Tall: 292 of room, the picture shrinks and keeps 16:10 (467 × 292).
        val tall = DeckGeometry.video(704f, 932f - 640f)
        assertEquals(292f, tall.second, 0.5f)
        assertEquals(467.2f, tall.first, 0.5f)
        assertEquals(1.6f, tall.first / tall.second, 0.001f)
    }

    @Test fun noRoomMeansNoPictureNotANegativeOne() {
        val none = DeckGeometry.video(704f, -20f)
        assertEquals(0f, none.first, 0.001f)
        assertEquals(0f, none.second, 0.001f)
    }
}
