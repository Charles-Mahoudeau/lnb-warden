package fr.bde_teknantes.lnb.warden.server.network

import fr.bde_teknantes.lnb.warden.common.network.AuthResponsePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesChallengeResponsePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesReportPayload
import fr.bde_teknantes.lnb.warden.server.anticheat.ServerAnticheat
import fr.bde_teknantes.lnb.warden.server.auth.ServerAuth
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Handles client -> server payloads. Runs on the server thread. */
object ServerPayloadHandler {
    fun handleAuthResponse(payload: AuthResponsePayload, context: IPayloadContext) {
        ServerAuth.onResponse(context.listener(), payload)
    }

    fun handleFilesReport(payload: FilesReportPayload, context: IPayloadContext) {
        ServerAnticheat.onReport(context.listener(), payload)
    }

    fun handleFilesChallengeResponse(payload: FilesChallengeResponsePayload, context: IPayloadContext) {
        ServerAnticheat.onChallengeResponse(context.listener(), payload)
    }
}
