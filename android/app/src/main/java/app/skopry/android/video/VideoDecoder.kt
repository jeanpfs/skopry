package app.skopry.android.video

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import app.skopry.android.protocol.ParameterSets
import java.util.ArrayDeque

/**
 * HEVC from the Mac to a Surface (HEVCDisplayView's half): MediaCodec in async mode on its own
 * thread, each output rendered the moment it is decoded (latency beats smoothness, CLAUDE.md), the
 * Mac's length-prefixed NAL units turned into Annex B. New parameter sets (a new size, a restart)
 * make a new codec. While the screen's surface is gone (rotation, the app in the background) the
 * codec goes on decoding without showing, so it keeps its reference frames and the picture comes
 * back without waiting for a keyframe. (A parked SurfaceTexture is only what a codec is configured
 * on when it must start with no surface.)
 */
class VideoDecoder(private val events: Events) {
    interface Events {
        /** The picture's size changed (from the SPS), on the decoder thread. */
        fun onVideoSize(width: Int, height: Int)
        /** The codec cannot take this size; the client asks the Mac for Standard resolution. */
        fun onUnsupportedSize(width: Int, height: Int)
        fun onFrameRendered() {}
        /** Waiting for a keyframe in the middle of a stream (it dropped its backlog, or was made again with nothing to replay): ask the Mac for one. */
        fun onNeedsKeyframe() {}
    }

    private val thread = HandlerThread("skopry-decoder", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    private var codec: MediaCodec? = null
    private var sets: ParameterSets? = null
    private var screen: Surface? = null
    private var parkTexture: SurfaceTexture? = null
    private var parked: Surface? = null
    private var output: Surface? = null
    private val freeInputs = ArrayDeque<Int>()
    private val pending = ArrayDeque<Pending>()
    /** The stream since its last keyframe, to bring a decoder made again to the current picture at once (`GopCache`). */
    private val gop = GopCache()
    /** While the frames of a replay are being decoded: nothing is shown until the last of them, and the backlog check waits. */
    private var replaying = false
    private var replayUntilPts = 0L
    private var needKeyframe = true
    /** Some frame of this stream has arrived: a wait for a keyframe from here on is a wait in the middle of it, not the start of it, where the Mac sends one on its own. */
    private var streamSeen = false
    private val ask = KeyframeAsk()
    private val askTick = Runnable { if (needKeyframe) { if (ask.poll(SystemClock.elapsedRealtime())) events.onNeedsKeyframe(); scheduleAsk() } }

    /** The decoder can take nothing until the next keyframe. In the middle of a stream, ask the Mac for one (`KeyframeAsk` says when). */
    private fun waitForKeyframe() {
        needKeyframe = true
        if (!streamSeen) return
        if (ask.begin(SystemClock.elapsedRealtime())) events.onNeedsKeyframe()
        scheduleAsk()
    }

    private fun scheduleAsk() {
        handler.removeCallbacks(askTick)
        ask.nextDelay(SystemClock.elapsedRealtime())?.let { handler.postDelayed(askTick, it) }
    }
    private var configuredSize: Pair<Int, Int>? = null

    private class Pending(val data: ByteArray, val ptsUs: Long, val key: Boolean)

    @Volatile var decodedFrames = 0L
        private set

    fun setSurface(surface: Surface?) = handler.post { switchTo(surface) }

    /**
     * SurfaceHolder.Callback.surfaceDestroyed: nothing may render into the surface once the callback
     * returns, so the codec stops showing before it does (at most half a second).
     */
    fun detachSurfaceNow() {
        if (Looper.myLooper() == thread.looper) { switchTo(null); return }
        val done = java.util.concurrent.CountDownLatch(1)
        handler.postAtFrontOfQueue { switchTo(null); done.countDown() }
        done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Whether the decoder's pictures go to the screen: false while the app has no surface (it is in the background). */
    @Volatile private var rendering = true

    private fun switchTo(surface: Surface?) {
        screen = surface?.takeIf { it.isValid }
        val shown = screen
        if (shown == null) {
            // No surface: the codec goes on decoding without showing, so it is in step when a surface returns. Moving
            // it to a parked texture instead failed here (setOutputSurface threw on the Qualcomm decoder, the
            // rebuild after it could not configure, and three failures in 10 s were taken for "this size is not
            // supported": the Mac was asked for Standard resolution each time the app went to the background).
            rendering = false
            return
        }
        rendering = true
        if (shown === output) return
        val c = codec
        if (c != null) {
            try {
                c.setOutputSurface(shown)
                output = shown
            } catch (e: Exception) {
                Log.w(TAG, "setOutputSurface failed, rebuilding: ${e.message}")
                rebuild()
            }
        } else {
            output = shown
            if (sets != null) rebuild()
        }
    }

    /** Codec errors mid-stream: rebuilt, at most five times in 10 s; never read as "this size is unsupported". */
    private var errors = 0
    private var errorWindowStart = 0L

    fun onParameterSets(ps: ParameterSets) = handler.post {
        if (ps.sameAs(sets) && codec != null) return@post
        if (!ps.sameAs(sets)) gop.clear()              // another size or stream: its frames are of no use to this one
        sets = ps
        failures = 0
        rebuild()
    }

    fun onFrame(payload: ByteArray, keyframe: Boolean, hostTimestamp: Double) = handler.post {
        val ps = sets ?: return@post
        val annexB = ParameterSets.toAnnexB(payload, ps.nalLength) ?: return@post
        val frame = Pending(annexB, nextPts(hostTimestamp), keyframe)
        // Every frame of the stream is kept, whatever the codec is doing: a run with a hole would not decode.
        gop.add(GopCache.Frame(annexB, frame.ptsUs, keyframe))
        streamSeen = true
        if (codec == null) return@post
        if (needKeyframe && !keyframe) return@post
        needKeyframe = false
        ask.end(); handler.removeCallbacks(askTick)
        // A codec that stopped taking input for half a second has fallen behind for good: start
        // again from the next keyframe rather than show a picture that keeps lagging. (While it is
        // still allocating, frames wait: dropping them then costs a keyframe interval of black.)
        if (pending.size >= 30 && !replaying) {
            pending.clear()
            if (!keyframe) { waitForKeyframe(); return@post }
        }
        pending.add(frame)
        feed()
    }

    /** Presentation times strictly increasing, in µs from the Mac's clock. */
    private var lastPts = 0L
    private fun nextPts(hostTimestamp: Double): Long {
        val pts = maxOf((hostTimestamp * 1_000_000).toLong(), lastPts + 1)
        lastPts = pts
        return pts
    }

    private val latency = LatencyRing()
    private var latencySum = 0L
    private var latencyCount = 0
    @Volatile var decodeLatencyMs = -1
        private set

    private fun noteOutput(ptsUs: Long) {
        val ms = latency.rendered(ptsUs, System.nanoTime()) ?: return
        latencySum += ms
        latencyCount++
        if (latencyCount >= 30) {
            decodeLatencyMs = (latencySum / latencyCount).toInt()
            latencySum = 0; latencyCount = 0
        }
    }

    fun release() = handler.post {
        teardown()
        parked?.release(); parkTexture?.release()
        thread.quitSafely()
    }

    /** Forget the stream (a session ended): the next one starts from its parameter sets. */
    fun reset() = handler.post {
        teardown()
        gop.clear()
        sets = null
        configuredSize = null
        streamSeen = false
        ask.end(); handler.removeCallbacks(askTick)
    }

    private fun parkSurface(): Surface {
        parked?.let { return it }
        val t = SurfaceTexture(0).apply { setDefaultBufferSize(16, 16) }
        parkTexture = t
        return Surface(t).also { parked = it }
    }

    private fun teardown() {
        codec?.let { c -> runCatching { c.stop() }; runCatching { c.release() } }
        codec = null
        // A parked surface a released codec was on may still be connected to its texture: a new one each time.
        parked?.release(); parkTexture?.release(); parked = null; parkTexture = null
        freeInputs.clear()
        pending.clear()
        latency.clear()
        replaying = false; replayUntilPts = 0L
        needKeyframe = true
    }

    private fun rebuild() {
        teardown()
        val ps = sets ?: return
        if (app.skopry.android.BuildConfig.DEBUG) {
            Log.d(TAG, "parameter sets (nal ${ps.nalLength}): " + ps.sets.joinToString(" ") { s -> s.joinToString("") { "%02x".format(it) } })
        }
        val (w, h) = ps.pictureSize() ?: (1920 to 1080)
        if (configuredSize != (w to h)) {
            configuredSize = w to h
            events.onVideoSize(w, h)
        }
        val name = decoderFor(w, h)
        if (name == null) {
            Log.w(TAG, "no HEVC decoder takes ${w}x$h")
            events.onUnsupportedSize(w, h)
            return
        }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, w, h).apply {
            setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(ps.annexB()))
            if (Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)                 // real time
            setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            setInteger("vendor.low-latency.enable", 1)              // MediaTek, Qualcomm
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxOf(w * h, 2 * 1024 * 1024))
        }
        // A codec that keeps failing is not rebuilt for ever: three tries in 10 s, then the Mac is told.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - failWindowStart > 10_000) { failWindowStart = now; failures = 0 }
        if (failures >= 3) {
            Log.w(TAG, "decoder failed $failures times in 10 s at ${w}x$h; giving up until new parameter sets")
            events.onUnsupportedSize(w, h)
            return
        }
        val c = try {
            MediaCodec.createByCodecName(name)
        } catch (e: Exception) {
            Log.w(TAG, "createByCodecName($name): ${e.message}"); failures++; handler.postDelayed({ if (codec == null) rebuild() }, 500); return
        }
        c.setCallback(callbacks, handler)
        try {
            var target = screen?.takeIf { it.isValid } ?: parkSurface()
            try {
                c.configure(format, target, null, 0)
            } catch (e: Exception) {
                // A screen surface going away mid-resize, not the size: park, and take the screen back
                // when its next surface comes (setSurface).
                if (target === parked) throw e
                Log.w(TAG, "configure on the screen's surface failed (${e.message}); parking")
                c.reset()
                c.setCallback(callbacks, handler)
                target = parkSurface()
                c.configure(format, target, null, 0)
            }
            c.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            c.start()
            output = target
            codec = c
            val lowLatency = runCatching {
                c.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
            }.getOrDefault(false)
            val vendor = if (Build.VERSION.SDK_INT >= 31) runCatching { c.supportedVendorParameters.filter { it.contains("latency", true) || it.contains("low", true) } }.getOrDefault(emptyList()) else emptyList()
            Log.i(TAG, "decoder $name ${w}x$h started; low latency feature=$lowLatency vendor=$vendor")
            // Made again mid-stream: the frames since the last keyframe are decoded first, so the picture is current
            // at once and not after the Mac's next keyframe.
            gop.replay()?.let { run ->
                pending.clear()
                run.forEach { pending.add(Pending(it.data, it.ptsUs, it.key)) }
                needKeyframe = false
                replaying = true
                replayUntilPts = run.last().ptsUs
                Log.i(TAG, "replaying ${run.size} frames since the last keyframe")
                feed()
            }
            if (needKeyframe) waitForKeyframe()
        } catch (e: Exception) {
            Log.w(TAG, "configure $name ${w}x$h: ${e.message}")
            runCatching { c.release() }
            failures++
            handler.postDelayed({ if (codec == null) rebuild() }, 500)
        }
    }

    private var failures = 0
    private var failWindowStart = 0L

    private val callbacks = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (codec !== this@VideoDecoder.codec) return
            freeInputs.add(index); feed()
        }
        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (codec !== this@VideoDecoder.codec) return
            // A replay shows only its last frame: the ones before it are the past, decoded to get the codec's state right.
            runCatching { codec.releaseOutputBuffer(index, rendering && (!replaying && replayUntilPts == 0L || info.presentationTimeUs >= replayUntilPts)) }
            if (replayUntilPts != 0L && info.presentationTimeUs >= replayUntilPts) replayUntilPts = 0L
            noteOutput(info.presentationTimeUs)
            decodedFrames++
            events.onFrameRendered()
        }
        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.w(TAG, "codec error: ${e.diagnosticInfo} recoverable=${e.isRecoverable} transient=${e.isTransient}")
            if (codec !== this@VideoDecoder.codec) return
            if (e.isTransient) return
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - errorWindowStart > 10_000) { errorWindowStart = now; errors = 0 }
            if (++errors <= 5) handler.post { rebuild() } else Log.w(TAG, "codec keeps failing; waiting for new parameter sets")
        }
        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "decoder output ${format.getInteger(MediaFormat.KEY_WIDTH)}x${format.getInteger(MediaFormat.KEY_HEIGHT)}")
        }
    }

    private fun feed() {
        val c = codec ?: return
        if (replaying && pending.size < 8) replaying = false
        while (freeInputs.isNotEmpty() && pending.isNotEmpty()) {
            val index = freeInputs.poll()!!
            val f = pending.poll()!!
            try {
                val buf = c.getInputBuffer(index) ?: continue
                buf.clear()
                if (f.data.size > buf.capacity()) {
                    Log.w(TAG, "frame of ${f.data.size} bytes over the input buffer's ${buf.capacity()}")
                    c.queueInputBuffer(index, 0, 0, f.ptsUs, 0)
                    waitForKeyframe()
                    continue
                }
                buf.put(f.data)
                if (replayUntilPts == 0L) latency.queued(f.ptsUs, System.nanoTime())
                c.queueInputBuffer(index, 0, f.data.size, f.ptsUs, if (f.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            } catch (e: Exception) {
                Log.w(TAG, "queueInputBuffer: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SkopryVideo"

        /** The best decoder that takes this size: hardware first, then software. */
        fun decoderFor(w: Int, h: Int): String? {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) }
            }
            fun takes(info: MediaCodecInfo) = runCatching {
                val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities
                caps?.isSizeSupported(w, h) == true
            }.getOrDefault(false)
            val ok = infos.filter { takes(it) && !it.name.contains("secure", true) }
            return (ok.firstOrNull { it.isHardwareAccelerated } ?: ok.firstOrNull())?.name
        }

        /** The frame rate a hardware decoder here sustains at this size (its performance points); nil when it does not say. */
        fun maxRate(w: Int, h: Int): Int? = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.isHardwareAccelerated && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } }
                .mapNotNull { it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities }
                .filter { it.isSizeSupported(w, h) }
                .maxOfOrNull { caps ->
                    val points = caps.supportedPerformancePoints
                    if (points.isNullOrEmpty()) caps.getSupportedFrameRatesFor(w, h).upper.toInt()
                    else listOf(120, 90, 60, 30).firstOrNull { fps ->
                        points.any { it.covers(MediaCodecInfo.VideoCapabilities.PerformancePoint(w, h, fps)) }
                    } ?: 30
                }
        }.getOrNull()

        /** The largest picture a hardware decoder here takes, for the Settings panel's note. */
        fun largestHardwareSize(): Pair<Int, Int>? = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.isHardwareAccelerated && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } }
                .mapNotNull { it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities }
                .maxByOrNull { it.supportedWidths.upper * it.supportedHeights.upper }
                ?.let { it.supportedWidths.upper to it.supportedHeights.upper }
        }.getOrNull()
    }
}
