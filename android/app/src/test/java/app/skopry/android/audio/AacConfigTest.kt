package app.skopry.android.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AacConfigTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** What the host's AAC-ELD encoder (AudioToolbox) really sends, 48 kHz stereo, 480 frames. */
    private val real = hex("0380808024000000048080801640140018000001f4000001f4000580808004f8e65000068080800102")

    @Test fun theRealCookieGivesItsFourByteAudioSpecificConfig() {
        assertArrayEquals(hex("f8e65000"), AacConfig.audioSpecificConfig(real))
    }

    @Test fun aBareAudioSpecificConfigPassesThroughUnchanged() {
        assertArrayEquals(hex("f8e65000"), AacConfig.audioSpecificConfig(hex("f8e65000")))
    }

    @Test fun aLengthOfOneByteInsteadOfFourPaddedOnesStillParses() {
        // The same descriptors with the short length form (ISO 14496-1 allows one to four bytes).
        val short = hex("0318") + real.copyOfRange(5, 8) + hex("0413") + real.copyOfRange(13, 26) + hex("0504") + hex("f8e65000")
        assertArrayEquals(hex("f8e65000"), AacConfig.audioSpecificConfig(short))
    }

    @Test fun aCutShortDescriptorGivesNullAtEveryLength() {
        // The config's last byte is at index 34: any cookie shorter than 35 bytes has lost part of it.
        for (n in 1 until 35) assertNull("cut at $n", AacConfig.audioSpecificConfig(real.copyOf(n)))
    }

    @Test fun anEmptyCookieGivesNullAndWrongInnerTagsGiveNull() {
        assertNull(AacConfig.audioSpecificConfig(ByteArray(0)))
        val wrongDecoder = real.copyOf().also { it[8] = 0x07 }
        assertNull(AacConfig.audioSpecificConfig(wrongDecoder))
        val wrongSpecific = real.copyOf().also { it[26] = 0x09 }
        assertNull(AacConfig.audioSpecificConfig(wrongSpecific))
    }
}
