package fr.bde_teknantes.lnb.warden.server.playtime

import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The playtime state LNB gave for one online player, and when it came. The mod never computes a limit:
 * it only shows this state and, as a backup when the event stream is down, runs out its countdown.
 */
class PlaytimeView(val state: WardenServerApi.Playtime, private val receivedAtMs: Long) {
    /** Whether a limit applies to this player right now. LNB in `observe` mode, or an exemption, make it false. */
    val isLimited: Boolean get() = state.enforced == true && state.limited == true

    val isGrace: Boolean get() = isLimited && state.grace == true

    /** Whether the allowance is going down. */
    val isCounting: Boolean get() = isLimited && !isGrace && state.counting == true && state.remainingSeconds != null

    /** What is left of the allowance at [nowMs], worked out from when the state came (clocks are never compared). */
    fun remainingSeconds(nowMs: Long): Long? {
        val remaining = state.remainingSeconds ?: return null
        if (!isCounting) return remaining
        return (remaining - (nowMs - receivedAtMs) / 1000).coerceAtLeast(0)
    }

    /** When the local backup timer kicks the player, or null when nothing is counting down. */
    val deadlineMs: Long? get() = if (isCounting) receivedAtMs + state.remainingSeconds!! * 1000 else null

    companion object {
        /** Everything LNB shows is in Paris time. */
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("Europe/Paris"))

        /** An ISO 8601 date from LNB, in Paris time. Null stays null. */
        fun formatDate(iso: String?): String? =
            iso?.let { runCatching { DATE.format(Instant.parse(it)) }.getOrDefault(it) }
    }
}
