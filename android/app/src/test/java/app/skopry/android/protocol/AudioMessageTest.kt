package app.skopry.android.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AudioMessageTest {
    private fun format(json: String): ByteArray = byteArrayOf(1) + json.toByteArray(Charsets.UTF_8)

    /** type 2 · epoch · seq · flags · count · (length · bytes)* */
    private fun packets(epoch: Int, seq: Long, flags: Int, vararg packets: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(2)
        o.write((epoch shr 8) and 0xFF); o.write(epoch and 0xFF)
        for (s in 3 downTo 0) o.write(((seq shr (8 * s)) and 0xFF).toInt())
        o.write(flags)
        o.write(packets.size)
        for (p in packets) {
            o.write((p.size shr 8) and 0xFF); o.write(p.size and 0xFF)
            o.write(p)
        }
        return o.toByteArray()
    }

    private fun parsedFormat(payload: ByteArray): AudioFormat {
        val m = AudioMessage.parse(payload)
        assertTrue(m is AudioMessage.Format)
        return (m as AudioMessage.Format).format
    }

    private fun parsedPackets(payload: ByteArray): AudioMessage.Packets {
        val m = AudioMessage.parse(payload)
        assertTrue(m is AudioMessage.Packets)
        return m as AudioMessage.Packets
    }

    @Test fun validFormatReadsEveryField() {
        val f = parsedFormat(format(
            """{"codec":"aac-eld","sampleRate":48000,"channels":2,"framesPerPacket":480,"primingFrames":240,
               "bitrate":64000,"epoch":7,"cookie":"AAAA","source":"app","app":"Safari"}""",
        ))
        assertEquals("aac-eld", f.codec)
        assertEquals(48_000, f.rate)
        assertEquals(2, f.channelCount)
        assertEquals(480, f.frames)
        assertEquals(240, f.priming)
        assertEquals(64_000, f.bitrate)
        assertEquals(7, f.epochValue)
        assertArrayEquals(byteArrayOf(0, 0, 0), f.cookie)
        assertEquals("app", f.source)
        assertEquals("Safari", f.app)
    }

    @Test fun emptyFormatTakesTheDefaults() {
        val f = parsedFormat(format("{}"))
        assertEquals("aac-eld", f.effectiveCodec)
        assertEquals(48_000, f.rate)
        assertEquals(2, f.channelCount)
        assertEquals(480, f.frames)
        assertEquals(240, f.priming)
        assertEquals(0, f.epochValue)
        assertNull(f.cookie)
    }

    @Test fun unknownKeysAreIgnored() {
        val f = parsedFormat(format("""{"future":{"x":[1,2]},"codec":"aac-eld","extra":true}"""))
        assertEquals("aac-eld", f.codec)
    }

    @Test fun formatOverTheCapIsDropped() {
        val big = "{\"app\":\"" + "x".repeat(4096) + "\"}"
        assertNull(AudioMessage.parse(format(big)))
    }

    @Test fun badJsonIsDroppedNotThrown() {
        assertNull(AudioMessage.parse(format("{not json")))
    }

    @Test fun oneAndThreePacketsParse() {
        val one = parsedPackets(packets(1, 9, 0, byteArrayOf(1, 2, 3)))
        assertEquals(1, one.packets.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), one.packets[0])
        val three = parsedPackets(packets(1, 9, 0, byteArrayOf(1), byteArrayOf(2, 2), byteArrayOf(3, 3, 3)))
        assertEquals(3, three.packets.size)
        assertArrayEquals(byteArrayOf(3, 3, 3), three.packets[2])
    }

    @Test fun zeroCountIsDropped() {
        assertNull(AudioMessage.parse(packets(1, 0, 0)))
    }

    @Test fun lengthPastTheEndIsDropped() {
        val good = packets(1, 0, 0, byteArrayOf(1, 2, 3, 4))
        val cut = good.copyOf(good.size - 1)
        assertNull(AudioMessage.parse(cut))
    }

    @Test fun trailingBytesAreDropped() {
        assertNull(AudioMessage.parse(packets(1, 0, 0, byteArrayOf(1)) + byteArrayOf(9)))
    }

    @Test fun headerCutShortIsDropped() {
        assertNull(AudioMessage.parse(byteArrayOf(2, 0, 1, 0, 0)))
    }

    @Test fun unknownTypeAndEmptyPayloadAreSkipped() {
        assertNull(AudioMessage.parse(byteArrayOf(9, 1, 2)))
        assertNull(AudioMessage.parse(ByteArray(0)))
    }

    @Test fun epochAndSeqAreBigEndianUnsigned() {
        val p = parsedPackets(packets(65_535, 0xFFFF_FFFFL, 0, byteArrayOf(5)))
        assertEquals(65_535, p.epoch)
        assertEquals(0xFFFF_FFFFL, p.seq)
    }

    @Test fun onlyFlagBitZeroMarksASegmentStart() {
        assertTrue(parsedPackets(packets(1, 0, 0x01, byteArrayOf(1))).segmentStart)
        assertTrue(parsedPackets(packets(1, 0, 0xFF, byteArrayOf(1))).segmentStart)
        assertTrue(!parsedPackets(packets(1, 0, 0xFE, byteArrayOf(1))).segmentStart)
        assertTrue(!parsedPackets(packets(1, 0, 0, byteArrayOf(1))).segmentStart)
    }
}
