package fr.bde_teknantes.lnb.warden.server.anticheat

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import net.minecraft.network.chat.Component

/** Turns the verdict of LNB's API into what the server does. The API decides, the server only applies. */
object Verdicts {
    sealed interface Outcome {
        /** `allow` and `flag`: nothing the player sees. */
        data object Pass : Outcome

        /** `warn`: the player stays, and is told which packs staff will look at. */
        data class Warn(val names: List<String>, val sha512s: Set<String>) : Outcome

        /** `hold`, `refuse`, `kick` and `ban`: the player is disconnected with [message]. */
        data class Remove(
            val action: String,
            /** The rule behind it, such as `fileRefused`. */
            val code: String,
            /** The first file behind it, if it is about files. */
            val sha512: String?,
            val message: Component,
            /** Set for `ban`: the reason of the ban to issue. */
            val banReason: String?,
        ) : Outcome
    }

    /** The most file names shown in one message. */
    private const val MAX_NAMES = 8
    private const val MAX_BAN_REASON = 500

    private val ACCOUNT_CODES = setOf("playerHeld", "playerZero", "manualHold")

    fun decide(verdict: WardenServerApi.Verdict?): Outcome {
        val action = verdict?.action ?: return Outcome.Pass
        // The reasons come most severe first: those with the verdict's action are the ones that decided it.
        val reasons = verdict.reasons.orEmpty().filter { it.action == action }
        val names = reasons.mapNotNull { it.name }.distinct().take(MAX_NAMES)
        val top = reasons.firstOrNull()
        val code = top?.code ?: action
        val onAccount = top == null || top.code in ACCOUNT_CODES

        return when (action) {
            "allow", "flag" -> Outcome.Pass
            "warn" -> Outcome.Warn(names, reasons.mapNotNullTo(HashSet()) { it.sha512 })
            "hold" -> Outcome.Remove(
                action, code, top?.sha512,
                if (onAccount) DisconnectReasons.anticheatAccountHeld() else DisconnectReasons.anticheatHeld(names),
                null,
            )
            "refuse" -> Outcome.Remove(action, code, top?.sha512, DisconnectReasons.anticheatRefused(names), null)
            "kick" -> Outcome.Remove(
                action, code, top?.sha512,
                if (onAccount) DisconnectReasons.anticheatUntrusted() else DisconnectReasons.anticheatBanned(names),
                null,
            )
            "ban" -> {
                val reason = banReason(names.joinToString(", ").ifEmpty { "?" })
                Outcome.Remove(action, code, top?.sha512, DisconnectReasons.banned(reason, null), reason)
            }
            else -> {
                LnbWarden.LOGGER.warn("Unknown LNB anticheat action {}: letting the player in", action)
                Outcome.Pass
            }
        }
    }

    private fun banReason(names: String): String {
        val template = ServerConfig.ANTICHEAT_BAN_REASON.get()
        val reason = if ("%s" in template) template.replace("%s", names) else "$template $names"
        return reason.trim().take(MAX_BAN_REASON).ifEmpty { names }
    }
}
