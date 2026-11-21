package fr.bde_teknantes.lnb.warden.server.clearlag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClearRulesTest {
    @Test
    fun `parses ids and tags`() {
        assertEquals(IdPattern(false, "minecraft:diamond"), IdPattern.parse(" minecraft:diamond "))
        assertEquals(IdPattern(true, "c:ores"), IdPattern.parse("#c:ores"))
        assertNull(IdPattern.parse("diamond"))
        assertNull(IdPattern.parse("a:b:c"))
        assertNull(IdPattern.parse("#"))
    }

    @Test
    fun `parses caps`() {
        assertEquals(ChunkCap("minecraft:item", 100), ChunkCap.parse("minecraft:item=100"))
        assertNull(ChunkCap.parse("minecraft:item=0"))
        assertNull(ChunkCap.parse("minecraft:item=many"))
        assertNull(ChunkCap.parse("minecraft:item"))
        assertNull(ChunkCap.parse("#c:ores=5"))
    }

    @Test
    fun `removes the oldest first`() {
        assertEquals(emptyList(), ChunkCaps.excess(listOf(5, 1), 2))
        assertEquals(listOf(2, 0), ChunkCaps.excess(listOf(50, 10, 90, 20), 2))
    }
}
