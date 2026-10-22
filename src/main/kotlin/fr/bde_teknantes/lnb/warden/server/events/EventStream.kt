package fr.bde_teknantes.lnb.warden.server.events

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.server.AuthMode
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.api.disconnectReason
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.io.BufferedReader
import java.io.IOException
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Keeps `GET /api/warden/server/events` open, on its own thread, to kick players banned, unlinked or signed out
 * on the website.
 */
object EventStream {
    sealed interface Status {
        val since: Instant

        data class Connected(override val since: Instant) : Status
        data class Disconnected(override val since: Instant, val reason: String) : Status
    }

    private const val MAX_BACKOFF_MS = 30_000L

    /** The API pings after 25 quiet seconds: past this, the connection is presumed dead. */
    private const val SILENCE_MS = 75_000L

    @Volatile
    var status: Status = Status.Disconnected(Instant.now(), "not started")
        private set

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var lastEventId: String? = null

    @Volatile
    private var lastDataAt = 0L
    private var backoffMs = 1_000L

    fun start(server: MinecraftServer) {
        if (running) return
        running = true
        thread = Thread({ run(server) }, "LNB Warden events").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        closeReader()
    }

    /** Called every server tick: drops a silent connection, or one that `auth.mode = off` no longer wants. */
    fun onServerTick(event: ServerTickEvent.Post) {
        if (status !is Status.Connected) return
        if (ServerConfig.AUTH_MODE.get() == AuthMode.OFF || Util.getMillis() - lastDataAt > SILENCE_MS) closeReader()
    }

    private fun run(server: MinecraftServer) {
        while (running) {
            val reason = try {
                when {
                    ServerConfig.AUTH_MODE.get() == AuthMode.OFF -> "auth.mode is off"
                    !ServerConfig.isApiConfigured -> "api.url and api.token aren't set"
                    else -> {
                        listen(server)
                        "the stream ended"
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            if (!running) break
            onDisconnected(reason)

            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                break
            }
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun listen(server: MinecraftServer) {
        val request = ApiClient.request(ServerConfig.API_URL.get(), "/api/warden/server/events", ServerConfig.API_TOKEN.get())
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(30))
            .apply { lastEventId?.let { header("Last-Event-ID", it) } }
            .GET()
            .build()
        val response = ApiClient.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()}")
        }

        lastDataAt = Util.getMillis()
        backoffMs = 1_000L
        onConnected()

        response.body().bufferedReader().use { reader ->
            this.reader = reader
            var type = "message"
            var id: String? = null
            val data = StringBuilder()
            while (running) {
                val line = reader.readLine() ?: break
                lastDataAt = Util.getMillis()
                when {
                    line.isEmpty() -> {
                        if (id != null) lastEventId = id
                        if (data.isNotEmpty()) dispatch(server, type, data.toString().trimEnd('\n'))
                        type = "message"
                        id = null
                        data.clear()
                    }
                    line.startsWith(":") -> Unit
                    else -> {
                        val field = line.substringBefore(':')
                        val value = line.substringAfter(':', "").removePrefix(" ")
                        when (field) {
                            "event" -> type = value
                            "data" -> data.append(value).append('\n')
                            "id" -> id = value
                        }
                    }
                }
            }
        }
        reader = null
    }

    private fun dispatch(server: MinecraftServer, type: String, data: String) {
        val json = try {
            JsonParser.parseString(data) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return LnbWarden.LOGGER.warn("Ignoring a malformed LNB {} event", type)
        // The payload, possibly wrapped as {type, data}.
        val body = json.getAsJsonObject("data")?.takeIf { !json.has("uuids") } ?: json

        when (type) {
            "kick" -> kick(server, body)
            "settings" -> LnbWarden.LOGGER.info("LNB's Warden settings changed: {}", body)
            "unban" -> LnbWarden.LOGGER.info("LNB unbanned {}", body.get("uuids"))
            "ping" -> Unit
            else -> LnbWarden.LOGGER.debug("Ignoring LNB event {}", type)
        }
    }

    private fun kick(server: MinecraftServer, body: JsonObject) {
        val uuids = body.getAsJsonArray("uuids")?.mapNotNull { runCatching { UUID.fromString(it.asString) }.getOrNull() }.orEmpty()
        val reason = body.get("reason")?.asString
        val message: Component = when (reason) {
            "banned" -> ApiClient.GSON.fromJson(body.get("ban"), WardenServerApi.Ban::class.java).disconnectReason()
            "unlinked" -> DisconnectReasons.unlinked()
            "deviceRevoked" -> DisconnectReasons.deviceRevoked()
            else -> Component.translatable("multiplayer.disconnect.kicked")
        }

        server.execute {
            for (uuid in uuids) {
                val player = server.playerList.getPlayer(uuid) ?: continue
                LnbWarden.LOGGER.info("LNB kicks {}: {}", player.gameProfile.name, reason)
                player.connection.disconnect(message)
            }
        }
    }

    private fun onConnected() {
        val previous = status
        status = Status.Connected(Instant.now())
        if (previous is Status.Disconnected && previous.reason != "not started") {
            LnbWarden.LOGGER.info("Connected to LNB's event stream again")
        } else {
            LnbWarden.LOGGER.info("Connected to LNB's event stream")
        }
    }

    private fun onDisconnected(reason: String) {
        val previous = status
        // Once per state change: not again for each failed reconnection.
        if (previous is Status.Disconnected && previous.reason != "not started") {
            status = previous.copy(reason = reason)
            return
        }
        status = Status.Disconnected(Instant.now(), reason)
        if (reason == "auth.mode is off") LnbWarden.LOGGER.info("LNB's event stream is off: auth.mode is off")
        else LnbWarden.LOGGER.warn("LNB's event stream is disconnected ({}), reconnecting", reason)
    }

    private fun closeReader() {
        try {
            reader?.close()
        } catch (_: IOException) {
        }
    }
}
