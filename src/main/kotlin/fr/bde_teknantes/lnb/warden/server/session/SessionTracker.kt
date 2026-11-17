package fr.bde_teknantes.lnb.warden.server.session

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import fr.bde_teknantes.lnb.warden.server.playtime.ServerPlaytime
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The LNB session of each online player: kept alive with a heartbeat every minute, and ended when they leave.
 * Guests have none.
 *
 * The open sessions are also listed in a file, so that the next start can end those a crash left open.
 */
object SessionTracker {
    data class Heartbeat(val at: Instant, val error: String?)

    private const val HEARTBEAT_TICKS = 60 * 20
    private const val MAX_SESSIONS_PER_HEARTBEAT = 500

    private val sessions = ConcurrentHashMap<UUID, String>()
    private val kicked: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var ticks = 0
    private const val SHUTDOWN_WAIT_SECONDS = 5L
    private val file get() = FMLPaths.GAMEDIR.get().resolve("lnbwarden-sessions.txt")
    private val fileLock = Any()

    @Volatile
    var lastHeartbeat: Heartbeat? = null
        private set
    private var heartbeatFailing = false

    val count: Int
        get() = sessions.size

    fun track(uuid: UUID, sessionId: String) {
        kicked.remove(uuid)
        sessions[uuid] = sessionId
        persist()
    }

    fun uuidOf(sessionId: String): UUID? = sessions.entries.firstOrNull { it.value == sessionId }?.key

    private fun persist() = synchronized(fileLock) {
        try {
            Files.write(file, sessions.values.toList())
        } catch (e: IOException) {
            LnbWarden.LOGGER.warn("Couldn't save the open LNB sessions: {}", e.message)
        }
    }

    /** Ends the sessions that a crash left open, so LNB doesn't wait for the heartbeat timeout. */
    fun endLeftovers() {
        val leftovers = synchronized(fileLock) {
            try {
                if (Files.exists(file)) Files.readAllLines(file).filter { it.isNotBlank() }.also { Files.delete(file) } else emptyList()
            } catch (e: IOException) {
                LnbWarden.LOGGER.warn("Couldn't read the LNB sessions left open: {}", e.message)
                emptyList()
            }
        }
        if (leftovers.isEmpty()) return
        LnbWarden.LOGGER.info("Ending {} LNB session(s) left open by the last run", leftovers.size)
        leftovers.forEach { WardenServerApi.endSession(it, "quit") }
    }

    /** Ends every session as the server stops, and waits a few seconds for LNB to take it. */
    fun endAll() {
        val ids = sessions.values.toList()
        sessions.clear()
        persist()
        if (ids.isEmpty()) return
        val calls = ids.map { WardenServerApi.endSession(it, "quit") }
        try {
            CompletableFuture.allOf(*calls.toTypedArray()).get(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            LnbWarden.LOGGER.warn("Couldn't end every LNB session before stopping: {}", e.message)
        }
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
        persist()
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
                    onPlaytime(result.body.playtime.orEmpty())
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
        persist()
        LnbWarden.LOGGER.warn("LNB closed {} session(s), no longer tracking them: {}", closed.size, players)
    }

    /** The heartbeat carries the state of each session: a safety net for the events that were missed. */
    private fun onPlaytime(states: Map<String, WardenServerApi.Playtime>) {
        if (states.isEmpty()) return
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val byPlayer = states.mapNotNull { (sessionId, state) -> uuidOf(sessionId)?.let { it to state } }
        server.execute { byPlayer.forEach { (uuid, state) -> ServerPlaytime.onUpdate(server, uuid, state) } }
    }
}
