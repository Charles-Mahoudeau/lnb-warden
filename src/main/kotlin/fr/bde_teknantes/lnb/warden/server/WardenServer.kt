package fr.bde_teknantes.lnb.warden.server

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.server.events.EventStream
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

/** Starts and stops what a dedicated server keeps running against LNB's API. */
object WardenServer {
    fun onStarted(event: ServerStartedEvent) {
        val server = event.server
        // Singleplayer and LAN games never call the API.
        if (!server.isDedicatedServer) return

        if (!ServerConfig.isApiConfigured) {
            LnbWarden.LOGGER.warn("api.url and api.token aren't set in lnbwarden-server.toml: players can't be authenticated")
        } else if (!ApiClient.isSecure(ServerConfig.API_URL.get())) {
            LnbWarden.LOGGER.warn("api.url doesn't use HTTPS: the server's API token travels unencrypted")
        }
        EventStream.start(server)
    }

    fun onStopping(event: ServerStoppingEvent) {
        EventStream.stop()
    }
}
