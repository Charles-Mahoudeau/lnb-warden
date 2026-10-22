package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.client.network.ClientPayloadHandler
import fr.bde_teknantes.lnb.warden.server.network.ServerPayloadHandler
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Payloads must be registered on both sides so each side knows how to encode/decode them.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so mismatched client/server versions refuse to connect.
 */
object WardenNetwork {
    private const val PROTOCOL_VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)

        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC, ServerPayloadHandler::handlePing)

        // Wrapped in a lambda so the client-only handler class is only loaded when a packet
        // actually arrives, which never happens on a dedicated server.
        registrar.playToClient(PongPayload.TYPE, PongPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handlePong(payload, context)
        }
    }
}
