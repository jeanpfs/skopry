package app.skopry.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayoutTest {
    private val rate = 48_000
    private fun ms(v: Int) = AudioPlayout.msToFrames(v, rate).toLong()

    @Test fun firstPacketsSetTheSequenceAndPlayNothingExtra() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        assertEquals(AudioPlayout.PacketPlan(0, 0), p.planPackets(10, 2, 480))
        assertEquals(AudioPlayout.PacketPlan(0, 0), p.planPackets(12, 1, 480))
    }

    @Test fun gapIsConcealedWithSilenceOfTheMissingLength() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.planPackets(0, 1, 480)
        val plan = p.planPackets(3, 1, 480)   // packets 1 and 2 lost
        assertEquals(0, plan.skip)
        assertEquals(2 * 480, plan.silenceFrames)
        assertEquals(2L, p.concealedPackets)
    }

    @Test fun hugeGapIsCappedAtTheCatchUpLimit() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.planPackets(0, 1, 480)
        val plan = p.planPackets(10_000, 1, 480)
        assertEquals(AudioPlayout.msToFrames(AudioPlayout.CAP_MS, rate), plan.silenceFrames)
    }

    @Test fun repeatedPacketsAreSkippedNotPlayedTwice() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.planPackets(5, 3, 480)              // 5,6,7
        val plan = p.planPackets(6, 3, 480)   // 6,7 seen, 8 new
        assertEquals(2, plan.skip)
        assertEquals(0, plan.silenceFrames)
        assertEquals(2L, p.duplicates)
    }

    @Test fun segmentStartDropsOnlyThePrimingFrames() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.segmentStart(240)
        assertEquals(0, p.keepDecoded(100))      // all 100 are priming
        assertEquals(100, p.keepDecoded(240))    // the last 140 priming frames dropped, 100 of this packet kept
        assertEquals(480, p.keepDecoded(480))
        assertEquals(240L, p.droppedFrames)
    }

    @Test fun newEpochForgetsSequenceAndPriming() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.planPackets(100, 1, 480)
        p.segmentStart(240)
        p.newEpoch(2)
        assertEquals(AudioPlayout.PacketPlan(0, 0), p.planPackets(0, 1, 480))
        assertEquals(480, p.keepDecoded(480))
    }

    @Test fun playbackStartsOnlyOnceStartLevelIsQueued() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        assertTrue(p.level(ms(59)) is AudioPlayout.Level.Wait)
        assertTrue(p.level(ms(60)) is AudioPlayout.Level.Start)
        assertTrue(p.playing)
        assertTrue(p.level(ms(100)) is AudioPlayout.Level.Wait)
    }

    @Test fun pastTheCapItDropsDownToTheTargetAndNotBelow() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.level(ms(60))
        val level = p.level(ms(250)) as AudioPlayout.Level.CatchUp
        assertEquals((ms(250) - ms(120)).toInt(), level.dropFrames)
        assertTrue(p.level(ms(200)) is AudioPlayout.Level.Wait)  // at the cap, not past it
    }

    @Test fun aDryQueueCountsAnUnderrunAndRebuffers() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.level(ms(60))
        p.onDry()
        assertFalse(p.playing)
        assertEquals(1, p.underruns)
        assertTrue(p.level(ms(30)) is AudioPlayout.Level.Wait)
        assertTrue(p.level(ms(60)) is AudioPlayout.Level.Start)
    }

    @Test fun dryWhileNotPlayingIsNotAnUnderrun() {
        val p = AudioPlayout(rate)
        p.newEpoch(1)
        p.onDry()
        assertEquals(0, p.underruns)
    }

    @Test fun fadeInRampsFromSilenceAndLeavesTheRestAlone() {
        val pcm = ShortArray(10 * 2) { 1000 }
        PcmFade.fadeIn(pcm, 2, 10, 4)
        assertEquals(250, pcm[0].toInt())     // frame 0: 1/4
        assertEquals(250, pcm[1].toInt())
        assertEquals(1000, pcm[2 * 4].toInt())  // frame 4 untouched
    }

    @Test fun fadeOutRampsToSilenceAtTheEnd() {
        val pcm = ShortArray(10 * 2) { 1000 }
        PcmFade.fadeOut(pcm, 2, 10, 4)
        assertEquals(1000, pcm[2 * 5].toInt())     // frame 5 untouched
        assertEquals(750, pcm[2 * 6].toInt())      // frame 6: 3/4
        assertEquals(0, pcm[2 * 9].toInt())        // last frame silent
    }

    @Test fun fadesNeverOverrunAShortBuffer() {
        val pcm = ShortArray(2 * 2) { 1000 }
        PcmFade.fadeIn(pcm, 2, 2, 100)
        PcmFade.fadeOut(pcm, 2, 2, 100)
        assertEquals(0, pcm[3].toInt())
    }
}
