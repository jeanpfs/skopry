package app.skopry.android.audio

import kotlin.math.min

/**
 * The playout rules for the Mac's sound, pure and JVM-testable (the ideas of iOSClient/AudioPlayout.swift,
 * without its clock model: AudioTrack gives a play head, not a media clock). It decides, it does not play:
 * the player asks and carries the answer out.
 *
 * - Sequence: a gap in the packet numbers is concealed with silence of the same length (capped at the
 *   catch-up limit); a packet already seen is skipped, so a repeated packet is never played twice.
 * - Segment start: the decoder's first output of a new segment is the codec's priming, dropped.
 * - Levels: playback starts once START_MS is queued, and it catches up by dropping the oldest frames when
 *   more than CAP_MS is queued (down to TARGET_MAX_MS). A dry queue stops playback until START_MS is queued again.
 */
class AudioPlayout(val sampleRate: Int) {
    companion object {
        const val START_MS = 60
        const val TARGET_MAX_MS = 120
        const val CAP_MS = 200
        const val FADE_MS = 7

        fun msToFrames(ms: Int, rate: Int): Int = (ms.toLong() * rate / 1000).toInt()
    }

    /** What to do with the packets of one message: skip the leading `skip` already seen, then insert `silenceFrames`. */
    data class PacketPlan(val skip: Int, val silenceFrames: Int)

    /** What the queue level asks for. */
    sealed class Level {
        object Wait : Level()
        object Start : Level()
        class CatchUp(val dropFrames: Int) : Level()
    }

    var epoch = -1
        private set
    var playing = false
        private set
    var packetsSeen = 0L
        private set
    var duplicates = 0L
        private set
    var concealedPackets = 0L
        private set
    var underruns = 0
        private set
    var droppedFrames = 0L
        private set

    private var nextSeq = -1L
    private var primingLeft = 0

    private val startFrames = msToFrames(START_MS, sampleRate)
    private val targetFrames = msToFrames(TARGET_MAX_MS, sampleRate)
    private val capFrames = msToFrames(CAP_MS, sampleRate)
    private val maxGapFrames = capFrames

    /** A new epoch is a new decoder: forget the sequence, the priming and the playing state. */
    fun newEpoch(epoch: Int) {
        this.epoch = epoch
        nextSeq = -1L
        primingLeft = 0
        playing = false
    }

    /** A message with the segment flag: the next decoded frames start with the codec's priming. */
    fun segmentStart(primingFrames: Int) {
        primingLeft = primingFrames
    }

    /** Plans a packets message (seq = its first packet's sequence number). */
    fun planPackets(seq: Long, count: Int, framesPerPacket: Int): PacketPlan {
        packetsSeen += count
        val end = seq + count
        if (nextSeq < 0) {
            nextSeq = end
            return PacketPlan(0, 0)
        }
        if (seq < nextSeq) {
            val skip = min(count.toLong(), nextSeq - seq).toInt()
            duplicates += skip
            if (end > nextSeq) nextSeq = end
            return PacketPlan(skip, 0)
        }
        val silence = if (seq > nextSeq) {
            concealedPackets += seq - nextSeq
            min((seq - nextSeq) * framesPerPacket, maxGapFrames.toLong()).toInt()
        } else 0
        nextSeq = end
        return PacketPlan(0, silence)
    }

    /** Drops the priming from decoded output of `frames`; returns how many frames to keep. */
    fun keepDecoded(frames: Int): Int {
        val drop = min(primingLeft, frames)
        primingLeft -= drop
        droppedFrames += drop
        return frames - drop
    }

    /** The queue level after writing: when to start, or how many oldest frames to discard to catch up. */
    fun level(queuedFrames: Long): Level {
        if (!playing) {
            if (queuedFrames >= startFrames) {
                playing = true
                return Level.Start
            }
            return Level.Wait
        }
        if (queuedFrames > capFrames) {
            val drop = (queuedFrames - targetFrames).toInt()
            droppedFrames += drop
            return Level.CatchUp(drop)
        }
        return Level.Wait
    }

    /** The queue ran dry while playing: count it and wait for START_MS again. */
    fun onDry() {
        if (playing) {
            underruns++
            playing = false
        }
    }
}

/** Linear fades on interleaved PCM16, the 5 to 10 ms ramps that keep a start, a stop or a cut from clicking. */
object PcmFade {
    /** Ramps the first `fadeFrames` of `frames` up from silence (in place). */
    fun fadeIn(pcm: ShortArray, channels: Int, frames: Int, fadeFrames: Int) {
        val n = min(fadeFrames, frames)
        for (f in 0 until n) scale(pcm, channels, f, (f + 1).toDouble() / n)
    }

    /** Ramps the last `fadeFrames` of `frames` down to silence (in place). */
    fun fadeOut(pcm: ShortArray, channels: Int, frames: Int, fadeFrames: Int) {
        val n = min(fadeFrames, frames)
        for (i in 0 until n) {
            val f = frames - n + i
            scale(pcm, channels, f, (n - i - 1).toDouble() / n)
        }
    }

    private fun scale(pcm: ShortArray, channels: Int, frame: Int, gain: Double) {
        for (c in 0 until channels) {
            val i = frame * channels + c
            pcm[i] = (pcm[i] * gain).toInt().toShort()
        }
    }
}
