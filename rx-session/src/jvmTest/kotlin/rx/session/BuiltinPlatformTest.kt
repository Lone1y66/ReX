package rx.session

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import rx.defs.BuiltinDefs
import rx.protocol.EcuPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Built-in definitions follow the ECU platform detected on connection; no vendor file needed. */
class BuiltinPlatformTest {
    @Test fun tableFollowsDetectedPlatformBeforeArming() = runTest {
        val s3 = BuiltinDefs.forKey("S3")
        val x2 = BuiltinDefs.forKey("X2")
        val seen = ArrayList<EcuPlatform?>()
        val ecu = LogReplayTransport(backgroundScope, { _, _, _ -> 0L }, deviceType = EcuPlatform.MINI_XX.node,
            nowMs = { testScheduler.currentTime })
        val start = Presets.table(Presets.ALL_CHANNELS.copy(absids = Presets.allAbsids(s3)), s3)
        val want = Presets.table(Presets.ALL_CHANNELS.copy(absids = Presets.allAbsids(x2)), x2)
        val s = Session({ ecu }, start, 888888, backgroundScope, nanoTime = { testScheduler.currentTime * 1_000_000 },
            tableFor = { p -> seen += p; if (p?.controlFile == "X2") want else start })
        var rounds = 0
        s.addListener(object : SessionListener { override fun onRound(tNanos: Long, raw: LongArray) { if (raw.size == want.entries.size) rounds++ } })
        s.start()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(EcuPlatform.MINI_XX, s.platform.value)
        assertEquals(listOf<EcuPlatform?>(EcuPlatform.MINI_XX), seen)
        assertEquals(want.entries, s.table.entries)
        assertEquals(SessionState.Streaming, s.state.value)
        kotlin.test.assertTrue(rounds > 10, "rounds $rounds")
        s.stop()
    }

    @Test fun importedDefinitionsResolveTheFullTableByName() {
        val s3 = BuiltinDefs.forKey("S3")
        // An imported file without a default list: same names, shifted absids.
        val shifted = rx.defs.ChannelRegistry(null, s3.channels.map { it.copy(absid = it.absid + 1) })
        val all = Presets.allAbsids(shifted)
        val expected = Presets.XTUNE_151.absids.mapNotNull { s3.values[it] }.map { it.absid + 1 }
        assertEquals(expected, all)
    }
}
