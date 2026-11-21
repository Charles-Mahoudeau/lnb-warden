package fr.bde_teknantes.lnb.warden.server.clearlag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClearSchedulePolicyTest {
    private val marks = listOf(60, 10, 5)

    private fun ClearSchedule.run(ticks: Long, interval: Int, marks: List<Int> = this@ClearSchedulePolicyTest.marks) =
        (1..ticks).map { tick(interval, marks) }

    @Test
    fun `fires after the interval`() {
        val schedule = ClearSchedule()
        val steps = schedule.run(120 * 20, 120)
        assertTrue(steps.last().fire)
        assertEquals(1, steps.count { it.fire })
    }

    @Test
    fun `warns once at each mark`() {
        val schedule = ClearSchedule()
        val warnings = schedule.run(120 * 20, 120).mapNotNull { it.warnMark }
        assertEquals(listOf(60, 10, 5), warnings)
    }

    @Test
    fun `marks longer than the interval are ignored`() {
        val schedule = ClearSchedule()
        val warnings = schedule.run(30 * 20, 30).mapNotNull { it.warnMark }
        assertEquals(listOf(10, 5), warnings)
    }

    @Test
    fun `a shrinking interval restarts the countdown at the first mark`() {
        val schedule = ClearSchedule()
        schedule.run(300 * 20, 600)
        val steps = schedule.run(60 * 20 + 1, 120)
        assertEquals(60, steps.first { it.warnMark != null }.warnMark)
        assertFalse(steps.dropLast(1).any { it.fire })
        assertTrue(steps.any { it.fire })
    }

    @Test
    fun `a growing interval announces the marks again`() {
        val schedule = ClearSchedule()
        schedule.run(115 * 20, 120)
        // 5 seconds left, then the lag ends and the interval grows.
        assertNull(schedule.tick(600, marks).warnMark)
        val warnings = schedule.run(600 * 20, 600).mapNotNull { it.warnMark }
        assertEquals(listOf(60, 10, 5), warnings)
    }

    @Test
    fun `reset starts a new cycle`() {
        val schedule = ClearSchedule()
        schedule.run(120 * 20, 120)
        schedule.reset()
        assertEquals(0, schedule.elapsedTicks)
        assertFalse(schedule.run(100 * 20, 120).any { it.fire })
    }

    @Test
    fun `no marks means a silent clear`() {
        val schedule = ClearSchedule()
        val steps = schedule.run(10 * 20, 10, emptyList())
        assertTrue(steps.none { it.warnMark != null })
        assertTrue(steps.last().fire)
    }
}
