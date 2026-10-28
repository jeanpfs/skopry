package app.skopry.android

import app.skopry.android.client.shotFileName
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ShotNameTest {
    @Test fun padsEveryFieldAndUsesTheZone() {
        val at = Instant.parse("2026-03-05T04:07:09Z")
        assertEquals("Skopry-20260305-040709.png", shotFileName(at, ZoneId.of("UTC")))
    }

    @Test fun localTimeCanCrossMidnightAndTheYear() {
        val at = Instant.parse("2026-12-31T23:30:00Z")
        assertEquals("Skopry-20270101-083000.png", shotFileName(at, ZoneId.of("Asia/Tokyo")))
        assertEquals("Skopry-20261231-183000.png", shotFileName(at, ZoneId.of("America/New_York")))
    }

    @Test fun usesTwentyFourHourClock() {
        assertEquals("Skopry-20260601-150102.png", shotFileName(Instant.parse("2026-06-01T15:01:02Z"), ZoneId.of("UTC")))
    }
}
