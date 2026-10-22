package fr.bde_teknantes.lnb.warden.server.api

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** The `Warden: Minecraft server` routes of LNB's API, authenticated with the server's token. */
object WardenServerApi {
    data class LoginBody(val ticket: String, val nonce: String, val uuid: String, val username: String) {
        override fun toString() = "LoginBody(uuid=$uuid, username=$username)"
    }

    data class Warning(val id: String, val body: String, val createdAt: String)
    data class Ban(val reason: String, val expiresAt: String?)
    data class Limit(val used: Int, val max: Int)

    /** `allowed` sets [sessionId], [linked] and [warnings]; `denied` sets [reason], and [ban] or [limit]. */
    data class LoginResult(
        val result: String,
        val sessionId: String?,
        val linked: Boolean?,
        val warnings: List<Warning>?,
        val reason: String?,
        val ban: Ban?,
        val limit: Limit?,
    )

    data class Player(val userId: String, val name: String)

    data class HeartbeatBody(val sessionIds: List<String>)

    /** Sessions the API has closed, such as after an outage longer than 3 minutes. */
    data class HeartbeatResult(val unknown: List<String>)

    data class EndSessionBody(val sessionId: String, val reason: String)

    fun login(body: LoginBody): CompletableFuture<ApiResult<LoginResult>> =
        call("login", "POST", "/api/warden/server/login", body)

    /** 404 `notLinked` when no LNB account has this Minecraft account. */
    fun player(uuid: UUID): CompletableFuture<ApiResult<Player>> =
        call("player lookup", "GET", "/api/warden/server/players/$uuid")

    fun heartbeat(sessionIds: List<String>): CompletableFuture<ApiResult<HeartbeatResult>> =
        call("heartbeat", "POST", "/api/warden/server/sessions/heartbeat", HeartbeatBody(sessionIds))

    /** [reason] is `kicked` when the server disconnected the player, `quit` otherwise. */
    fun endSession(sessionId: String, reason: String): CompletableFuture<ApiResult<Unit>> =
        call("session end", "POST", "/api/warden/server/sessions/end", EndSessionBody(sessionId, reason))

    private inline fun <reified T> call(
        what: String,
        method: String,
        path: String,
        body: Any? = null,
    ): CompletableFuture<ApiResult<T>> {
        if (!ServerConfig.isApiConfigured) {
            val result = ApiResult.Network(IllegalStateException("api.url and api.token aren't set in lnbwarden-server.toml"))
            ApiHealth.record(what, result)
            return CompletableFuture.completedFuture(result)
        }
        // Plain HTTP is allowed here: the server's admin chose api.url, and is warned at startup.
        return ApiClient.send<T>(ServerConfig.API_URL.get(), method, path, ServerConfig.API_TOKEN.get(), body, requireSecure = false)
            .thenApply { result -> result.also { ApiHealth.record(what, it) } }
    }
}
