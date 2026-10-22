package fr.bde_teknantes.lnb.warden.server.auth

import com.mojang.authlib.GameProfile
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.auth.AuthFailure
import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.common.auth.Nonce
import fr.bde_teknantes.lnb.warden.common.network.AuthChallengePayload
import fr.bde_teknantes.lnb.warden.common.network.AuthResponsePayload
import fr.bde_teknantes.lnb.warden.server.AuthMode
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.api.disconnectReason
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import java.util.function.Consumer

/**
 * `lnbwarden:auth`: checks one connecting player with LNB's API before they spawn.
 *
 * Sends a nonce, waits for the client's join ticket (or why it has none), and asks the API whether to let the
 * player in. Everything after [run] happens on the server thread.
 */
class AuthTask(
    private val listener: ServerConfigurationPacketListenerImpl,
    private val server: MinecraftServer,
    /** Read once, when the connection started. */
    private val mode: AuthMode,
) : ICustomConfigurationTask {
    private val profile: GameProfile = listener.owner
    private val nonce = Nonce.generate()

    /** In [Util.getMillis] time. */
    @Volatile
    private var deadline = Long.MAX_VALUE
    private var answered = false
    private var done = false

    val isConnected: Boolean
        get() = listener.getConnection().isConnected

    override fun type(): ConfigurationTask.Type = TYPE

    override fun run(sender: Consumer<CustomPacketPayload>) {
        if (!listener.hasChannel(AuthChallengePayload.TYPE)) {
            // Without the mod (or with another version of it), the client could never answer.
            server.execute {
                answered = true
                refuseOrGuest(DisconnectReasons.noMod(), Component.translatable("chat.lnbwarden.guest.no_mod"))
            }
            return
        }
        deadline = Util.getMillis() + TIMEOUT_MS
        sender.accept(AuthChallengePayload(nonce))
    }

    /** Called every server tick. */
    fun tick() {
        if (answered || done || Util.getMillis() < deadline) return
        answered = true
        LnbWarden.LOGGER.info("{} didn't answer the LNB authentication in time", profile.name)
        refuseOrGuest(DisconnectReasons.timedOut(), Component.translatable("chat.lnbwarden.guest.no_answer"))
    }

    fun onResponse(response: AuthResponsePayload) {
        if (answered) return
        answered = true
        when (response) {
            is AuthResponsePayload.Ticket -> login(response.ticket)
            is AuthResponsePayload.Failure -> onFailure(response.failure)
        }
    }

    private fun onFailure(failure: AuthFailure) {
        if (failure == AuthFailure.UNTRUSTED_SERVER) {
            LnbWarden.LOGGER.warn(
                "{}'s LNB Warden doesn't recognise this server's address: they joined through another address than its serverAddress",
                profile.name,
            )
        }
        val reason = when (failure) {
            AuthFailure.NO_TOKEN, AuthFailure.TOKEN_REJECTED -> "chat.lnbwarden.guest.not_linked"
            AuthFailure.API_UNREACHABLE -> "chat.lnbwarden.guest.client_api_unreachable"
            AuthFailure.UNTRUSTED_SERVER -> "chat.lnbwarden.guest.untrusted_server"
            AuthFailure.LINK_DECLINED -> "chat.lnbwarden.guest.link_declined"
        }
        refuseOrGuest(DisconnectReasons.failure(failure), Component.translatable(reason))
    }

    private fun login(ticket: String) {
        val body = WardenServerApi.LoginBody(ticket, Nonce.encode(nonce), profile.id.toString(), profile.name)
        WardenServerApi.login(body).thenAcceptAsync({ result ->
            if (!isConnected) return@thenAcceptAsync
            when {
                result is ApiResult.Ok -> onLogin(result.body)
                result.isUnreachable || (result is ApiResult.Http && result.status == 401) -> onApiUnreachable()
                else -> {
                    // 400 invalidUuid: the server isn't in offline mode, or the username isn't valid for LNB.
                    LnbWarden.LOGGER.error("LNB refused the login of {} ({}): {}", profile.name, profile.id, result.describe())
                    refuse(DisconnectReasons.invalidTicket())
                }
            }
        }, server)
    }

    private fun onLogin(login: WardenServerApi.LoginResult) {
        if (login.result == "allowed") {
            LnbWarden.LOGGER.info("LNB allowed {} (session {})", profile.name, login.sessionId)
            allow(
                PendingJoin(
                    sessionId = login.sessionId,
                    warnings = login.warnings.orEmpty(),
                    linked = login.linked == true,
                    shortName = login.shortName,
                    guestReason = null,
                )
            )
            return
        }

        LnbWarden.LOGGER.info("LNB denied {}: {}", profile.name, login.reason)
        refuse(
            when (login.reason) {
                "banned" -> login.ban.disconnectReason()
                "linkedElsewhere" -> DisconnectReasons.linkedElsewhere(profile.name)
                "accountLimitReached" -> DisconnectReasons.accountLimitReached(login.limit?.max ?: 0)
                "notLinked" -> DisconnectReasons.notLinked(profile.name)
                "schoolNotAllowed" -> DisconnectReasons.schoolNotAllowed()
                else -> {
                    if (login.reason == "invalidTicket") {
                        LnbWarden.LOGGER.warn("LNB rejected the join ticket of {}", profile.name)
                    } else {
                        LnbWarden.LOGGER.warn("Unknown LNB denial reason {} for {}", login.reason, profile.name)
                    }
                    DisconnectReasons.invalidTicket()
                }
            }
        )
    }

    private fun onApiUnreachable() {
        when (mode) {
            AuthMode.OPTIONAL -> allowAsGuest(Component.translatable("chat.lnbwarden.guest.api_unreachable"))
            else -> refuse(DisconnectReasons.apiUnreachable())
        }
    }

    /**
     * No ticket. In `required` mode, refuse with [refusal]. In `optional` mode, let the player in as a guest
     * for [guestReason], unless their username is linked to an LNB account: then anyone could play under it.
     */
    private fun refuseOrGuest(refusal: Component, guestReason: Component) {
        if (mode != AuthMode.OPTIONAL) {
            refuse(refusal)
            return
        }
        WardenServerApi.player(profile.id).thenAcceptAsync({ result ->
            if (!isConnected) return@thenAcceptAsync
            when {
                result is ApiResult.Ok -> {
                    LnbWarden.LOGGER.info("{} is linked to an LNB account but didn't authenticate", profile.name)
                    refuse(refusal)
                }
                result is ApiResult.Http && result.status == 404 -> allowAsGuest(guestReason)
                else -> allowAsGuest(Component.translatable("chat.lnbwarden.guest.api_unreachable"))
            }
        }, server)
    }

    private fun allowAsGuest(reason: Component) {
        LnbWarden.LOGGER.info("{} joins as a guest: {}", profile.name, reason.string)
        allow(PendingJoin(sessionId = null, warnings = emptyList(), linked = false, shortName = null, guestReason = reason))
    }

    private fun allow(pending: PendingJoin) {
        if (done) return
        done = true
        ServerAuth.onAllowed(profile, pending)
        listener.finishCurrentTask(TYPE)
    }

    private fun refuse(reason: Component) {
        if (done) return
        done = true
        listener.disconnect(reason)
    }

    companion object {
        val TYPE = ConfigurationTask.Type(LnbWarden.id("auth").toString())

        /** The confirmation screen can take a while. */
        private const val TIMEOUT_MS = 2 * 60 * 1000L
    }
}
