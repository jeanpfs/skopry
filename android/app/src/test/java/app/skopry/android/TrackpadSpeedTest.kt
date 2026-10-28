package app.skopry.android

import app.skopry.android.client.TrackpadSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadSpeedTest {
    @Test fun multipliersAreTheSpecifiedOnesInOrder() {
        assertEquals(
            listOf("Slowest" to 0.5, "Slow" to 0.75, "Normal" to 1.0, "Fast" to 1.5, "Fastest" to 2.0),
            TrackpadSpeed.entries.map { it.title to it.multiplier },
        )
        assertTrue(TrackpadSpeed.entries.zipWithNext().all { (a, b) -> a.multiplier < b.multiplier })
    }

    @Test fun everyLevelSurvivesStoringItsName() {
        TrackpadSpeed.entries.forEach { assertEquals(it, TrackpadSpeed.parse(it.name)) }
    }

    @Test fun missingUnknownOrOldValuesAreNormal() {
        listOf(null, "", "normal", "FASTEST ", "1.5", "Turbo").forEach { assertEquals("for $it", TrackpadSpeed.NORMAL, TrackpadSpeed.parse(it)) }
    }
}
