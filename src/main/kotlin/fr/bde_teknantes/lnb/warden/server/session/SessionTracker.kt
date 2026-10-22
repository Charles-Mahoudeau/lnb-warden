package fr.bde_teknantes.lnb.warden.server.session

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The LNB session of each online player: kept alive with a heartbeat every minute, and ended when they leave.
 * Guests have none.
 */
object SessionTracker {
    data class Heartbeat(val at: Instant, val error: String?)

    private const val HEARTBEAT_TICKS = 60 * 20
    private const val MAX_SESSIONS_PER_HEARTBEAT = 500

    private val sessions = ConcurrentHashMap<UUID, String>()
    private val kicked: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var ticks = 0

    @Volatile
    var lastHeartbeat: Heartbeat? = null
        private set
    private var heartbeatFailing = false

    val count: Int
        get() = sessions.size

    fun track(uuid: UUID, sessionId: String) {
        kicked.remove(uuid)
        sessions[uuid] = sessionId
    }

    fun hasSession(uuid: UUID): Boolean = sessions.containsKey(uuid)

    /** Called by a mixin when the server disconnects a player in game, so their session ends as `kicked`. */
    @JvmStatic
    fun markKicked(uuid: UUID) {
        if (sessions.containsKey(uuid)) kicked.add(uuid)
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val uuid = event.entity.uuid
        val reason = if (kicked.remove(uuid)) "kicked" else "quit"
        val sessionId = sessions.remove(uuid) ?: return
        WardenServerApi.endSession(sessionId, reason).thenAccept { result ->
            if (result !is ApiResult.Ok) {
                // The API closes it anyway, 3 minutes after its last heartbeat.
                LnbWarden.LOGGER.warn("Couldn't end the LNB session of {}: {}", event.entity.gameProfile.name, result.describe())
            }
        }
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks < HEARTBEAT_TICKS) return
        ticks = 0
        if (sessions.isEmpty()) return
        sessions.values.toList().chunked(MAX_SESSIONS_PER_HEARTBEAT).forEach(::heartbeat)
    }

    private fun heartbeat(sessionIds: List<String>) {
        WardenServerApi.heartbeat(sessionIds).thenAccept { result ->
            synchronized(this) {
                if (result is ApiResult.Ok) {
                    onHeartbeat(result.body.unknown)
                } else {
                    lastHeartbeat = Heartbeat(Instant.now(), result.describe())
                    if (!heartbeatFailing) {
                        heartbeatFailing = true
                        LnbWarden.LOGGER.warn("LNB session heartbeat failed: {}", result.describe())
                    }
                }
            }
        }
    }

    private fun onHeartbeat(unknown: List<String>) {
        lastHeartbeat = Heartbeat(Instant.now(), null)
        if (heartbeatFailing) {
            heartbeatFailing = false
            LnbWarden.LOGGER.info("LNB session heartbeat works again")
        }
        if (unknown.isEmpty()) return

        // Kicks come from the event stream: these players stay, they just no longer have a session.
        val closed = unknown.toSet()
        val players = sessions.filterValues { it in closed }.keys
        sessions.keys.removeAll(players)
        LnbWarden.LOGGER.warn("LNB closed {} session(s), no longer tracking them: {}", closed.size, players)
    }
}
