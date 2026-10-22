package fr.bde_teknantes.lnb.warden.server.network

import fr.bde_teknantes.lnb.warden.common.network.PingPayload
import fr.bde_teknantes.lnb.warden.common.network.PongPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Handles client -> server payloads. Runs on the server thread. */
object ServerPayloadHandler {
    fun handlePing(payload: PingPayload, context: IPayloadContext) {
        context.reply(PongPayload(payload.sentAt))
    }
}
