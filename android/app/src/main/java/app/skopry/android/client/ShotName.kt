package app.skopry.android.client

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shotStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** The file name of a picture of the stream taken at `at`, in the zone `zone`: Skopry-YYYYMMDD-HHMMSS.png. */
fun shotFileName(at: Instant, zone: ZoneId): String = "Skopry-${shotStamp.format(at.atZone(zone))}.png"
