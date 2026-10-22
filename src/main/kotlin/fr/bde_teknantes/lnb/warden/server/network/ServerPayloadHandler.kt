package fr.bde_teknantes.lnb.warden.server.network

import fr.bde_teknantes.lnb.warden.common.network.AuthResponsePayload
import fr.bde_teknantes.lnb.warden.server.auth.ServerAuth
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Handles client -> server payloads. Runs on the server thread. */
object ServerPayloadHandler {
    fun handleAuthResponse(payload: AuthResponsePayload, context: IPayloadContext) {
        ServerAuth.onResponse(context.listener(), payload)
    }
}
