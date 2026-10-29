package app.skopry.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFormat as PcmFormat

/**
 * The AudioTrack the Mac's sound plays on: 48 kHz stereo PCM16, media usage, low latency. It is created paused:
 * the playout model decides when to play and when to pause (a dry queue). It asks for a transient may-duck
 * focus, so other apps lower their volume while the Mac's sound plays and get it back when it stops; it never
 * pauses them for good. Used from the audio thread only.
 */
class AudioOutput(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    /** Frames handed to the track since it started. */
    var written = 0L
        private set

    val isRunning get() = track != null

    fun start() {
        if (track != null) return
        requestFocus()
        val minBytes = AudioTrack.getMinBufferSize(RATE, PcmFormat.CHANNEL_OUT_STEREO, PcmFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setAudioFormat(
                PcmFormat.Builder()
                    .setEncoding(PcmFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(PcmFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minBytes, RATE * CHANNELS * 2 / 5))   // at least 200 ms of room
            .build()
        written = 0
    }

    fun play() {
        track?.takeIf { it.playState != AudioTrack.PLAYSTATE_PLAYING }?.play()
    }

    fun pause() {
        track?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.pause()
    }

    /** Frames of interleaved stereo PCM16 written but not yet played. */
    fun queuedFrames(): Long {
        val t = track ?: return 0
        val head = t.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
        return (written - head).coerceAtLeast(0)
    }

    /** Writes without blocking; returns the frames the track took. */
    fun write(pcm: ShortArray, frames: Int): Int {
        val t = track ?: return 0
        val n = t.write(pcm, 0, frames * CHANNELS, AudioTrack.WRITE_NON_BLOCKING)
        if (n <= 0) return 0
        val taken = n / CHANNELS
        written += taken
        return taken
    }

    fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.stop() }
        runCatching { t.release() }
        abandonFocus()
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }   // nothing to pause: the Mac's sound simply ducks
            .build()
        audio.requestAudioFocus(request)
        focus = request
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        const val RATE = 48_000
        const val CHANNELS = 2
    }
}
