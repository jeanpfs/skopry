package app.skopry.android.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import app.skopry.android.protocol.AudioFormat
import app.skopry.android.protocol.AudioMessage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * The Mac's sound: decodes kind 43 (AAC-ELD, MediaCodec) on its own thread and plays it on an AudioTrack through
 * the playout model. Errors never leave this class: a decoder that fails drops its epoch's audio and the next
 * format starts it again; the video is not touched. Not tied to an Activity: the client starts and stops it.
 *
 * `onStatus` gets "Playing the sound of …" while audio flows, null when it stops; it runs on the audio thread.
 */
class AudioPlayer(context: Context, private val onStatus: (String?) -> Unit) {
    private val thread = HandlerThread("skopry-audio", Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val output = AudioOutput(context)
    private val playout = AudioPlayout(AudioOutput.RATE)

    private var codec: MediaCodec? = null
    private var format: AudioFormat? = null
    private var fadeNext = false
    private var discard = 0
    private var mutedFlag = false

    /** Counters for the stream-stats line; read from any thread, approximate on purpose. */
    @Volatile var packetsReceived = 0L
        private set
    @Volatile var framesDecoded = 0L
        private set
    @Volatile var decodeErrors = 0
        private set
    @Volatile var active = false
        private set
    @Volatile var underruns = 0
        private set
    @Volatile var droppedFrames = 0L
        private set

    fun receive(payload: ByteArray) {
        if (mutedFlag) return
        handler.post { handle(payload) }
    }

    /** Mute is this device's own: muting stops the audio and skips decoding until it is off. */
    fun setMuted(on: Boolean) {
        mutedFlag = on
        if (on) stop()
    }

    /** Stops and releases the decoder and the track. Safe to call at any time; the next format restarts it. */
    fun stop() {
        handler.post {
            releaseDecoder()
            output.stop()
            playout.newEpoch(-1)
            discard = 0
            fadeNext = false
            publish(false)
            onStatus(null)
        }
    }

    fun release() {
        stop()
        thread.quitSafely()
    }

    /** One line for the stats HUD, or null while no sound plays. */
    fun statsLine(): String? = if (!active) null else
        "sound: $packetsReceived pkts · $framesDecoded fr · underruns $underruns · drops $droppedFrames · errors $decodeErrors"

    private fun handle(payload: ByteArray) {
        when (val m = AudioMessage.parse(payload)) {
            is AudioMessage.Format -> startEpoch(m.format)
            is AudioMessage.Packets -> packets(m)
            null -> Unit
        }
    }

    private fun startEpoch(f: AudioFormat) {
        if (codec != null && f.epochValue == playout.epoch) return   // the same stream repeating its format
        releaseDecoder()
        playout.newEpoch(f.epochValue)
        fadeNext = true
        discard = 0
        try {
            val c = MediaCodec.createDecoderByType(MIME)
            val mf = MediaFormat.createAudioFormat(MIME, f.rate, f.channelCount)
            mf.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
            // AudioToolbox's cookie is a whole ES descriptor; the decoder wants the AudioSpecificConfig inside it.
            f.cookie?.let { cookie ->
                val asc = AacConfig.audioSpecificConfig(cookie) ?: throw IllegalArgumentException("the cookie is not an AAC-ELD configuration")
                mf.setByteBuffer("csd-0", ByteBuffer.wrap(asc))
            }
            c.configure(mf, null, null, 0)
            c.start()
            codec = c
            format = f
            output.start()
            publish(true)
            onStatus(label(f))
        } catch (e: Exception) {
            Log.w(TAG, "audio decoder did not start: ${e.message}")
            fail()
        }
    }

    private fun packets(m: AudioMessage.Packets) {
        val c = codec ?: return
        val f = format ?: return
        if (m.epoch != playout.epoch) return
        packetsReceived += m.packets.size
        try {
            if (m.segmentStart) {
                c.flush()
                playout.segmentStart(f.priming)
                fadeNext = true
            }
            // A dry queue while playing: pause until START_MS is queued again (the track plays silence meanwhile).
            if (playout.playing && output.queuedFrames() == 0L) {
                playout.onDry()
                output.pause()
                fadeNext = true
            }
            val plan = playout.planPackets(m.seq, m.packets.size, f.frames)
            if (plan.silenceFrames > 0) writeFrames(ShortArray(plan.silenceFrames * AudioOutput.CHANNELS), plan.silenceFrames)
            for (i in plan.skip until m.packets.size) {
                val idx = c.dequeueInputBuffer(0)
                if (idx < 0) {
                    droppedFrames += f.frames
                    continue
                }
                val buf = c.getInputBuffer(idx) ?: continue
                buf.clear()
                buf.put(m.packets[i])
                val pts = (m.seq + i) * f.frames * 1_000_000L / f.rate
                c.queueInputBuffer(idx, 0, m.packets[i].size, pts, 0)
            }
            drain(c, f)
            afterWrite()
        } catch (e: Exception) {
            Log.w(TAG, "audio decode failed: ${e.message}")
            fail()
        }
    }

    private fun drain(c: MediaCodec, f: AudioFormat) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = c.dequeueOutputBuffer(info, 0)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (idx < 0) continue   // the output format changed: the PCM layout is fixed by the format message
            val out = c.getOutputBuffer(idx)
            if (out != null && info.size > 0) {
                out.position(info.offset)
                out.limit(info.offset + info.size)
                val samples = ShortArray(info.size / 2)
                out.order(ByteOrder.nativeOrder()).asShortBuffer().get(samples)
                playDecoded(samples, f)
            }
            c.releaseOutputBuffer(idx, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    /** Priming dropped, catch-up discards applied, the fade-in at a start, then into the track. */
    private fun playDecoded(samples: ShortArray, f: AudioFormat) {
        val ch = f.channelCount
        val frames = samples.size / ch
        framesDecoded += frames
        var from = frames - playout.keepDecoded(frames)
        val skipNow = min(discard, frames - from)
        discard -= skipNow
        from += skipNow
        val keep = frames - from
        if (keep <= 0) return
        val pcm = samples.copyOfRange(from * ch, frames * ch)
        if (fadeNext) {
            PcmFade.fadeIn(pcm, ch, keep, fadeFrames())
            fadeNext = false
        }
        writeFrames(pcm, keep)
    }

    private fun writeFrames(pcm: ShortArray, frames: Int) {
        val taken = output.write(pcm, frames)
        if (taken < frames) droppedFrames += frames - taken
    }

    /** The queue level after writing: start the track at START_MS, or discard the oldest to catch up. */
    private fun afterWrite() {
        when (val level = playout.level(output.queuedFrames())) {
            AudioPlayout.Level.Start -> output.play()
            is AudioPlayout.Level.CatchUp -> discard += level.dropFrames
            AudioPlayout.Level.Wait -> Unit
        }
        underruns = playout.underruns
    }

    /** A decoder that cannot run: this epoch's audio goes; the video is none of its business. */
    private fun fail() {
        decodeErrors++
        releaseDecoder()
        output.stop()
        playout.newEpoch(-1)
        discard = 0
        fadeNext = false
        publish(false)
        onStatus(null)
    }

    private fun releaseDecoder() {
        codec?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        codec = null
        format = null
    }

    private fun publish(on: Boolean) {
        active = on
    }

    private fun fadeFrames() = AudioPlayout.msToFrames(AudioPlayout.FADE_MS, AudioOutput.RATE)

    private fun label(f: AudioFormat): String = when {
        f.app != null -> "Playing the sound of ${f.app}"
        else -> "Playing the sound of the Mac"
    }

    companion object {
        private const val TAG = "SkopryAudio"
        private const val MIME = "audio/mp4a-latm"
    }
}
