package fr.bde_teknantes.lnb.warden.client.network

import fr.bde_teknantes.lnb.warden.client.auth.JoinHandler
import fr.bde_teknantes.lnb.warden.common.network.AuthChallengePayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Handles server -> client payloads. Runs on the client (render) thread. */
object ClientPayloadHandler {
    fun handleAuthChallenge(payload: AuthChallengePayload, context: IPayloadContext) {
        JoinHandler.onChallenge(payload, context)
    }
}
