package fr.bde_teknantes.lnb.warden.client

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import fr.bde_teknantes.lnb.warden.client.auth.AuthState
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Tells the website the player is in the game's menus, for its social feature.
 * Paused on LNB's server, whose session already says the player is there.
 */
object Presence {
    private const val INTERVAL_MS = 60_000L
    private val SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(2)

    /** When the presence was last sent, or null to send it on the next tick. */
    private var lastSentAt: Long? = null
    private var sent = false
    private var onLnbServer = false
    private var failing = false

    fun tick() {
        val session = ClientAuth.session
        if (onLnbServer || session == null || ClientAuth.state !is AuthState.Linked) return
        val now = Util.getMillis()
        lastSentAt?.let { if (now - it < INTERVAL_MS) return }
        lastSentAt = now
        sent = true

        WardenClientApi.putPresence(session.token).thenAcceptAsync({ result ->
            when {
                result is ApiResult.Ok -> failing = false
                result is ApiResult.Http && result.status == 401 -> ClientAuth.onTokenRejected()
                // Checking the link turns the state to unreachable, with its retry backoff.
                result.isUnreachable -> ClientAuth.refresh()
                !failing -> {
                    failing = true
                    LnbWarden.LOGGER.warn("Couldn't update the LNB presence: {}", result.describe())
                }
            }
        }, Minecraft.getInstance())
    }

    fun onLoggingIn(event: ClientPlayerNetworkEvent.LoggingIn) {
        if (LnbServer.isLnb(Minecraft.getInstance().currentServer?.ip)) onLnbServer = true
    }

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        if (!onLnbServer) return
        onLnbServer = false
        // Resume right away.
        lastSentAt = null
    }

    /** Clears the presence as the game closes, waiting at most [SHUTDOWN_TIMEOUT] so it never delays shutdown. */
    fun onShutdown() {
        val session = ClientAuth.session ?: return
        if (!sent) return
        try {
            WardenClientApi.deletePresence(session.token, SHUTDOWN_TIMEOUT)
                .get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            LnbWarden.LOGGER.debug("Couldn't clear the LNB presence: {}", e.toString())
        }
    }
}
