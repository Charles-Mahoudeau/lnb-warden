package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.client.network.ClientPayloadHandler
import fr.bde_teknantes.lnb.warden.server.network.ServerPayloadHandler
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Payloads must be registered on both sides so each side knows how to encode/decode them.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so mismatched client/server versions refuse to connect.
 */
object WardenNetwork {
    private const val PROTOCOL_VERSION = "3"

    fun register(event: RegisterPayloadHandlersEvent) {
        // Optional, so clients without the mod can still connect: the auth task decides what to do with them
        // (refuse them, or let them in as guests when auth.mode is optional).
        val registrar = event.registrar(PROTOCOL_VERSION).optional()

        // Wrapped in a lambda so the client-only handler class is only loaded when a packet
        // actually arrives, which never happens on a dedicated server.
        registrar.configurationToClient(AuthChallengePayload.TYPE, AuthChallengePayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAuthChallenge(payload, context)
        }
        registrar.configurationToServer(
            AuthResponsePayload.TYPE,
            AuthResponsePayload.STREAM_CODEC,
            ServerPayloadHandler::handleAuthResponse,
        )
        registrar.playToClient(RealNamesPayload.TYPE, RealNamesPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleRealNames(payload, context)
        }
    }
}
