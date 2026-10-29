package app.skopry.android.protocol

import java.nio.ByteBuffer
import java.util.Base64

/**
 * The Mac's sound on the device (kind 43; Sources/StreamProtocol/Audio.swift). Pure: parsing only. A payload's
 * first byte says what follows: 1 = format (JSON), 2 = packets (binary). Any other type is skipped by the caller.
 */
object AudioCodec {
    /** AAC-ELD: 48 kHz stereo, 480 or 512 frames a packet. */
    const val AAC_ELD = "aac-eld"

    /** The codecs this device plays, best first: the hello's `audio` field. */
    val PLAYS: List<String> = listOf(AAC_ELD)
}

/**
 * Kind 43, type 1: how to decode what follows. Every field is optional on the wire; the getters apply the codec's
 * defaults, and `epochValue` is the epoch the packets carry (0 when the format names none).
 */
class AudioFormat(
    val codec: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val framesPerPacket: Int?,
    val primingFrames: Int?,
    val bitrate: Int?,
    val epoch: Int?,
    val cookie: ByteArray?,
    val source: String?,
    val app: String?,
) {
    val effectiveCodec: String get() = codec ?: AudioCodec.AAC_ELD
    val rate: Int get() = sampleRate ?: 48_000
    val channelCount: Int get() = channels ?: 2
    val frames: Int get() = framesPerPacket ?: 480
    val priming: Int get() = primingFrames ?: 240
    val epochValue: Int get() = (epoch ?: 0) and 0xFFFF

    companion object {
        const val SOURCE_APP = "app"
        const val SOURCE_DESKTOP = "desktop"
        const val SOURCE_TEST = "test"
    }
}

/** A kind 43 payload, either type. Never built by the device; it only reads them. */
sealed class AudioMessage {
    class Format(val format: AudioFormat) : AudioMessage()

    /** `seq` is the first packet's sequence number; packet i of `packets` is `seq + i`. */
    class Packets(val epoch: Int, val seq: Long, val segmentStart: Boolean, val packets: List<ByteArray>) : AudioMessage()

    companion object {
        const val TYPE_FORMAT = 1
        const val TYPE_PACKETS = 2
        const val MAX_FORMAT_BYTES = 4096
        const val MAX_PACKETS_BYTES = 16384
        private const val PACKETS_HEADER = 9

        /**
         * The message, or null when the payload breaks a rule: an unknown type, a size past its cap, bad JSON, a
         * packets header cut short, a count of 0, a length past the end, bytes left over. Never throws.
         */
        fun parse(payload: ByteArray): AudioMessage? = try {
            if (payload.isEmpty()) null
            else when (payload[0].toInt() and 0xFF) {
                TYPE_FORMAT -> if (payload.size > MAX_FORMAT_BYTES) null else parseFormat(payload)
                TYPE_PACKETS -> if (payload.size > MAX_PACKETS_BYTES) null else parsePackets(payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        private fun parseFormat(p: ByteArray): AudioMessage {
            val o = org.json.JSONObject(String(p, 1, p.size - 1, Charsets.UTF_8))
            val cookie = o.optStringOrNull("cookie")?.let { Base64.getDecoder().decode(it) }
            return Format(
                AudioFormat(
                    codec = o.optStringOrNull("codec"),
                    sampleRate = o.optIntOrNull("sampleRate"),
                    channels = o.optIntOrNull("channels"),
                    framesPerPacket = o.optIntOrNull("framesPerPacket"),
                    primingFrames = o.optIntOrNull("primingFrames"),
                    bitrate = o.optIntOrNull("bitrate"),
                    epoch = o.optIntOrNull("epoch"),
                    cookie = cookie,
                    source = o.optStringOrNull("source"),
                    app = o.optStringOrNull("app"),
                ),
            )
        }

        private fun parsePackets(p: ByteArray): AudioMessage? {
            if (p.size < PACKETS_HEADER) return null
            val b = ByteBuffer.wrap(p)     // big endian
            b.get()                        // the type, checked by the caller
            val epoch = b.short.toInt() and 0xFFFF
            val seq = b.int.toLong() and 0xFFFFFFFFL
            val flags = b.get().toInt() and 0xFF
            val count = b.get().toInt() and 0xFF
            if (count == 0) return null
            val list = ArrayList<ByteArray>(count)
            repeat(count) {
                if (b.remaining() < 2) return null
                val len = b.short.toInt() and 0xFFFF
                if (b.remaining() < len) return null
                val bytes = ByteArray(len)
                b.get(bytes)
                list += bytes
            }
            if (b.hasRemaining()) return null
            return Packets(epoch, seq, segmentStart = flags and 1 != 0, packets = list)
        }
    }
}
