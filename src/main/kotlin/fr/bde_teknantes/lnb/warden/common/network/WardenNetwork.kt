package fr.bde_teknantes.lnb.warden.common.network

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Payloads must be registered on both sides so each side knows how to encode/decode them.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so mismatched client/server versions refuse to connect.
 */
object WardenNetwork {
    private const val PROTOCOL_VERSION = "2"

    fun register(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL_VERSION)
    }
}
