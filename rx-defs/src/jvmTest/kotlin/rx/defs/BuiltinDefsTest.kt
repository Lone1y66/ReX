package rx.defs

import rx.protocol.EcuPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltinDefsTest {
    /** 儀表、示波與預設格固定用到的通道：每個平台都要有，且名稱一致。 */
    private val dashboard = mapOf(26 to "RPM", 51 to "Vehicle_Speed", 34 to "TPS_Percent", 29 to "Volt_Batt",
        33 to "T_Eng", 144 to "MapNum", 130 to "GearNum")

    @Test fun everyDeclaredPlatformHasBuiltinDefinitions() {
        for (p in EcuPlatform.entries) assertTrue(BuiltinDefs.has(p.controlFile), p.label)
        for (k in BuiltinDefs.KEYS) {
            val reg = BuiltinDefs.forKey(k)
            assertTrue(reg.values.size in 100..400, "$k: ${reg.values.size}")
            val defaults = assertNotNull(reg.defaults, k)
            assertTrue(defaults.size >= 60, "$k defaults ${defaults.size}")
            for (a in defaults) assertNotNull(reg.values[a], "$k default $a")
            for (d in reg.values.values) assertTrue(d.cellSize in 1..4 && d.absid in 0 until 0xC000, "$k ${d.name}")
            // 位元通道都掛在已收的位元容器下（故障碼頁靠它）
            for (b in reg.channels.filter { it.isBit }) assertTrue(reg.values[b.absid]?.isBitContainer == true, "$k ${b.name}")
            assertTrue(reg.channels.any { it.isBit && it.name.startsWith("Malf") }, "$k malfunction bits")
        }
    }

    @Test fun dashboardChannelsKeepTheirMeaningAcrossPlatforms() {
        val s3 = BuiltinDefs.forKey("S3")
        for ((a, n) in dashboard) assertEquals(n, s3.values[a]?.name)
        for (k in BuiltinDefs.KEYS) {
            val reg = BuiltinDefs.forKey(k)
            for ((a, _) in dashboard) assertEquals(s3.values[a]!!.name, reg.values[a]?.name, "$k absid $a")
        }
    }

    @Test fun unknownKeyFallsBackToS3() {
        assertEquals(BuiltinDefs.forKey("S3").channels.size, BuiltinDefs.forKey("nope").channels.size)
    }

    @Test fun compactRoundTripKeepsDefaults() {
        val reg = BuiltinDefs.forKey("X2")
        val back = ChannelRegistry.fromCompact(reg.toCompact())
        assertEquals(reg.defaults, back.defaults)
        assertEquals(reg.channels, back.channels)
        // 舊快取沒有 #default 行
        assertNull(ChannelRegistry.fromCompact("RXDEF1\tx\nRPM\t26\t2\t1.0\t0.0\t20000.0\t0.0\tWord\trpm\tStage_1\t0\t\t\n").defaults)
    }

    @Test fun parsesBothControlFileLayouts() {
        val long = """<ECUControlFile><Variables>
            <Var name="RPM" absid="26"><Vinf>26,2,1,0,20000,0,Word,rpm,Stage_1,0,_,_,_,_</Vinf><Ver c_version="7" status="Enable"/></Var>
            </Variables></ECUControlFile>"""
        val short = """<ECUControlFile><Variables>
            <Var n="Malf0" id="7"><D>7,1,1,0,255,0,Byte,NA,Flag,0,_,_,_,_</D><V c="73" st="En">10-4</V></Var>
            <Var n="Malf0.TPS" id="7"><D>7,1,1,0,1,0,Bit2,NA,Flag,0,_,_,_,_</D><V c="73" st="En">10-1</V></Var>
            </Variables></ECUControlFile>"""
        val a = ChannelRegistry.parse(long)
        assertEquals(listOf("RPM"), a.channels.map { it.name })
        assertEquals(true, a.channels[0].versions[7])
        val b = ChannelRegistry.parse(short)
        assertEquals(listOf("Malf0", "Malf0.TPS"), b.channels.map { it.name })
        assertEquals(2, b.channels[1].bitIndex)
        assertEquals(true, b.channels[0].versions[73])
        assertEquals(listOf(7), b.bitsOf.keys.toList())
    }
}
