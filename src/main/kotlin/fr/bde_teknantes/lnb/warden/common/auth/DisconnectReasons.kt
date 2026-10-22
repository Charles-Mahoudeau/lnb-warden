package fr.bde_teknantes.lnb.warden.common.auth

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.TranslatableContents

/**
 * Disconnect messages sent by the server.
 *
 * They carry an English fallback, so a client without the mod's language file can still read them.
 * The client recognises the keys in [NEEDS_LINK] to offer a Link button on the disconnect screen.
 */
object DisconnectReasons {
    private const val PREFIX = "disconnect.lnbwarden."

    const val NO_TOKEN = PREFIX + "no_token"
    const val TOKEN_REJECTED = PREFIX + "token_rejected"
    const val CLIENT_API_UNREACHABLE = PREFIX + "client_api_unreachable"
    const val UNTRUSTED_SERVER = PREFIX + "untrusted_server"
    const val LINK_DECLINED = PREFIX + "link_declined"
    const val NO_MOD = PREFIX + "no_mod"
    const val TIMED_OUT = PREFIX + "timed_out"
    const val API_UNREACHABLE = PREFIX + "api_unreachable"
    const val BANNED = PREFIX + "banned"
    const val BANNED_PERMANENT = PREFIX + "banned_permanent"
    const val LINKED_ELSEWHERE = PREFIX + "linked_elsewhere"
    const val ACCOUNT_LIMIT_REACHED = PREFIX + "account_limit_reached"
    const val NOT_LINKED = PREFIX + "not_linked"
    const val SCHOOL_NOT_ALLOWED = PREFIX + "school_not_allowed"
    const val INVALID_TICKET = PREFIX + "invalid_ticket"
    const val UNLINKED = PREFIX + "unlinked"
    const val DEVICE_REVOKED = PREFIX + "device_revoked"

    /** Messages that the player fixes by linking the mod or this Minecraft account. */
    val NEEDS_LINK = setOf(NO_TOKEN, TOKEN_REJECTED, LINK_DECLINED, NOT_LINKED, DEVICE_REVOKED)

    fun failure(failure: AuthFailure): MutableComponent = when (failure) {
        AuthFailure.NO_TOKEN ->
            message(NO_TOKEN, "Link the mod to your LNB account from the main menu.")
        AuthFailure.TOKEN_REJECTED ->
            message(TOKEN_REJECTED, "Your LNB link expired or was revoked. Link the mod again from the main menu.")
        AuthFailure.API_UNREACHABLE ->
            message(CLIENT_API_UNREACHABLE, "LNB's API couldn't be reached from your computer. Try again in a moment.")
        AuthFailure.UNTRUSTED_SERVER ->
            message(UNTRUSTED_SERVER, "LNB Warden didn't recognise this server's address. Join with the Multiplayer button of the main menu.")
        AuthFailure.LINK_DECLINED ->
            message(LINK_DECLINED, "You need to link this Minecraft account to play here.")
    }

    fun noMod(): MutableComponent = message(NO_MOD, "This server requires the LNB Warden mod.")

    fun timedOut(): MutableComponent = message(TIMED_OUT, "LNB Warden didn't answer in time. Try again.")

    fun apiUnreachable(): MutableComponent = message(API_UNREACHABLE, "LNB's API is unreachable, try again in a moment.")

    /** [until] is the formatted end date, or null for a permanent ban. */
    /** In red, with the end date and the reason in white so they stand out. */
    fun banned(reason: String, until: String?): MutableComponent {
        val reasonText = Component.literal(reason).withStyle(ChatFormatting.WHITE)
        return (if (until == null) {
            message(BANNED_PERMANENT, "You are permanently banned from LNB's server.\nReason: %s", reasonText)
        } else {
            val untilText = Component.literal(until).withStyle(ChatFormatting.WHITE)
            message(BANNED, "You are banned from LNB's server until %s.\nReason: %s", untilText, reasonText)
        }).withStyle(ChatFormatting.RED)
    }

    fun linkedElsewhere(username: String): MutableComponent =
        message(LINKED_ELSEWHERE, "The Minecraft account %s belongs to another LNB account.", username)

    fun accountLimitReached(max: Int): MutableComponent = message(
        ACCOUNT_LIMIT_REACHED,
        "You have reached your limit of %s Minecraft accounts. Unlink one on LNB's website to play with this one.",
        max,
    )

    fun notLinked(username: String): MutableComponent =
        message(NOT_LINKED, "The Minecraft account %s isn't linked to your LNB account. Join again to link it.", username)

    fun schoolNotAllowed(): MutableComponent =
        message(SCHOOL_NOT_ALLOWED, "Players from your school can't play on LNB's server.")

    fun invalidTicket(): MutableComponent = message(INVALID_TICKET, "Authentication failed, try again.")

    fun unlinked(): MutableComponent =
        message(UNLINKED, "This Minecraft account was unlinked from your LNB account.")

    fun deviceRevoked(): MutableComponent =
        message(DEVICE_REVOKED, "This computer was signed out of your LNB account. Link the mod again from the main menu.")

    /** The key of one of these messages, or null for any other component. */
    fun keyOf(component: Component): String? =
        (component.contents as? TranslatableContents)?.key?.takeIf { it.startsWith(PREFIX) }

    private fun message(key: String, fallback: String, vararg args: Any): MutableComponent =
        Component.translatableWithFallback(key, fallback, *args)
}
