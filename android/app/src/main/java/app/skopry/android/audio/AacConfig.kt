package app.skopry.android.audio

/**
 * The AAC-ELD "cookie" the Mac sends is AudioToolbox's magic cookie: an MPEG-4 elementary stream descriptor
 * (ISO 14496-1: ES_Descriptor 0x03 > DecoderConfigDescriptor 0x04 > DecoderSpecificInfo 0x05 > the
 * AudioSpecificConfig). MediaCodec's decoder wants only that innermost AudioSpecificConfig as csd-0: given
 * the whole descriptor it fails with an unsupported-format error. A cookie that is not a descriptor (it does
 * not start with tag 0x03) is taken to be an AudioSpecificConfig already.
 */
object AacConfig {
    private const val TAG_ES = 0x03
    private const val TAG_DECODER_CONFIG = 0x04
    private const val TAG_DECODER_SPECIFIC = 0x05

    /** The AudioSpecificConfig inside [cookie], or [cookie] itself when it is not a descriptor; null when a descriptor is cut short. */
    fun audioSpecificConfig(cookie: ByteArray): ByteArray? {
        if (cookie.isEmpty()) return null
        if ((cookie[0].toInt() and 0xFF) != TAG_ES) return cookie
        var pos = 0
        val es = header(cookie, pos) ?: return null
        if (es.tag != TAG_ES) return null
        pos = es.bodyStart
        // ES_ID (2) and flags (1); the flags say what else precedes the nested descriptors.
        if (pos + 3 > cookie.size) return null
        val flags = cookie[pos + 2].toInt() and 0xFF
        pos += 3
        if (flags and 0x80 != 0) pos += 2                                        // streamDependenceFlag: dependsOn_ES_ID
        if (flags and 0x40 != 0) { if (pos >= cookie.size) return null; pos += 1 + (cookie[pos].toInt() and 0xFF) }   // URL_Flag
        if (flags and 0x20 != 0) pos += 2                                        // OCRstreamFlag
        val dec = header(cookie, pos) ?: return null
        if (dec.tag != TAG_DECODER_CONFIG) return null
        pos = dec.bodyStart + 13   // objectTypeIndication 1, streamType 1, bufferSizeDB 3, maxBitrate 4, avgBitrate 4
        val spec = header(cookie, pos) ?: return null
        if (spec.tag != TAG_DECODER_SPECIFIC) return null
        val end = spec.bodyStart + spec.length
        if (spec.length <= 0 || end > cookie.size) return null
        return cookie.copyOfRange(spec.bodyStart, end)
    }

    private class Header(val tag: Int, val length: Int, val bodyStart: Int)

    /** A descriptor's tag and its length (one to four bytes, seven bits each, the top bit saying more follow). */
    private fun header(b: ByteArray, at: Int): Header? {
        if (at < 0 || at >= b.size) return null
        val tag = b[at].toInt() and 0xFF
        var pos = at + 1
        var length = 0
        repeat(4) {
            if (pos >= b.size) return null
            val v = b[pos++].toInt() and 0xFF
            length = (length shl 7) or (v and 0x7F)
            if (v and 0x80 == 0) return Header(tag, length, pos)
        }
        return null
    }
}
