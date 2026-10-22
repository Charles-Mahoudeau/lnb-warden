package fr.bde_teknantes.lnb.warden.client.network

import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.common.network.PongPayload
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Handles server -> client payloads. Runs on the client (render) thread. */
object ClientPayloadHandler {
    fun handlePong(payload: PongPayload, context: IPayloadContext) {
        val roundTripMs = Util.getMillis() - payload.sentAt
        context.player().displayClientMessage(
            Component.translatable("message.lnbwarden.pong", roundTripMs),
            ClientConfig.PONG_IN_ACTION_BAR.get(),
        )
    }
}
