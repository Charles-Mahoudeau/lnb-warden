package fr.bde_teknantes.lnb.warden.server.clearlag

/**
 * When the next clear happens, and when to warn about it. Pure arithmetic on server ticks.
 *
 * The interval is given on every tick because it can change while counting down (the lag mode shortens it).
 * A cycle that shrinks below the first warning mark restarts its countdown at that mark, so a clear is never a surprise.
 */
class ClearSchedule {
    /** What one tick means: [warnMark] is the mark just reached, if any. */
    data class Step(val fire: Boolean, val warnMark: Int?, val remainingTicks: Long)

    var elapsedTicks = 0L
        private set
    private val warned = HashSet<Int>()

    fun reset() {
        elapsedTicks = 0
        warned.clear()
    }

    fun tick(intervalSeconds: Int, warningSeconds: List<Int>): Step {
        val intervalTicks = intervalSeconds.coerceAtLeast(1) * TPS
        // Only marks inside the interval make sense.
        val marks = warningSeconds.filter { it in 1 until intervalSeconds }.sortedDescending()
        elapsedTicks++

        var remaining = intervalTicks - elapsedTicks
        val first = marks.firstOrNull()
        if (first != null && warned.isEmpty() && remaining < first * TPS) {
            elapsedTicks = intervalTicks - first * TPS
            remaining = first * TPS
        }
        if (remaining <= 0) return Step(true, null, 0)

        // The interval grew back: marks that are ahead again can be announced again.
        warned.removeIf { it * TPS < remaining }
        var reached: Int? = null
        for (mark in marks) {
            if (remaining <= mark * TPS && warned.add(mark)) reached = mark
        }
        return Step(false, reached, remaining)
    }

    companion object {
        const val TPS = 20L
    }
}
