package fr.bde_teknantes.lnb.warden.client.auth

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.LnbServer
import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import fr.bde_teknantes.lnb.warden.client.gui.ConfirmLinkScreen
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.auth.AuthFailure
import fr.bde_teknantes.lnb.warden.common.auth.Nonce
import fr.bde_teknantes.lnb.warden.common.network.AuthChallengePayload
import fr.bde_teknantes.lnb.warden.common.network.AuthResponsePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl
import net.minecraft.core.UUIDUtil
import net.neoforged.neoforge.network.handling.IPayloadContext

/** The client side of `lnbwarden:auth`: trades the device token for a join ticket bound to the server's nonce. */
object JoinHandler {
    fun onChallenge(payload: AuthChallengePayload, context: IPayloadContext) {
        val connection = context.connection()
        fun reply(response: AuthResponsePayload) {
            if (connection.isConnected) context.reply(response)
        }

        // Only LNB's server gets tickets: another server running this mod could replay them there.
        val ip = (context.listener() as? ClientCommonPacketListenerImpl)?.serverData?.ip
        if (!LnbServer.isLnb(ip)) {
            LnbWarden.LOGGER.warn("{} asked for an LNB join ticket, but it isn't LNB's server: refusing", ip)
            reply(AuthResponsePayload.Failure(AuthFailure.UNTRUSTED_SERVER))
            return
        }
        if (ClientAuth.session == null) {
            reply(AuthResponsePayload.Failure(AuthFailure.NO_TOKEN))
            return
        }

        val minecraft = Minecraft.getInstance()
        val username = minecraft.user.name
        ClientAuth.refresh().thenAccept { result ->
            val me = when {
                result is ApiResult.Ok -> result.body
                result is ApiResult.Http && result.status == 401 -> {
                    reply(AuthResponsePayload.Failure(AuthFailure.TOKEN_REJECTED))
                    return@thenAccept
                }
                else -> {
                    LnbWarden.LOGGER.warn("Couldn't check the LNB link before joining: {}", result.describe())
                    reply(AuthResponsePayload.Failure(AuthFailure.API_UNREACHABLE))
                    return@thenAccept
                }
            }

            // Usernames are case-sensitive, like the offline UUID derived from them.
            val uuid = UUIDUtil.createOfflinePlayerUUID(username).toString()
            if (me.accounts.any { it.uuid.equals(uuid, ignoreCase = true) }) {
                requestTicket(payload.nonce, username, link = false, ::reply)
                return@thenAccept
            }

            minecraft.setScreen(ConfirmLinkScreen(minecraft.screen, connection, username, me) { accepted ->
                if (accepted) requestTicket(payload.nonce, username, link = true, ::reply)
                else reply(AuthResponsePayload.Failure(AuthFailure.LINK_DECLINED))
            })
        }
    }

    private fun requestTicket(nonce: ByteArray, username: String, link: Boolean, reply: (AuthResponsePayload) -> Unit) {
        val session = ClientAuth.session ?: return reply(AuthResponsePayload.Failure(AuthFailure.NO_TOKEN))
        val body = WardenClientApi.JoinTicketBody(Nonce.encode(nonce), username, link)
        WardenClientApi.joinTicket(session.token, body).thenAcceptAsync({ result ->
            when {
                result is ApiResult.Ok -> reply(AuthResponsePayload.Ticket(result.body.ticket))
                result is ApiResult.Http && result.status == 401 -> {
                    ClientAuth.onTokenRejected()
                    reply(AuthResponsePayload.Failure(AuthFailure.TOKEN_REJECTED))
                }
                else -> {
                    LnbWarden.LOGGER.warn("Couldn't get an LNB join ticket: {}", result.describe())
                    reply(AuthResponsePayload.Failure(AuthFailure.API_UNREACHABLE))
                }
            }
        }, Minecraft.getInstance())
    }
}
