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

    /** The LNB user behind an `allowed` login. [displayName] is their real name, as they chose to show it. */
    data class LoginPlayer(val userId: String, val displayName: String?)

    /**
     * `allowed` sets [sessionId], [linked] and [warnings], and [player] for LNB users;
     * `denied` sets [reason], and [ban] or [limit].
     */
    data class LoginResult(
        val result: String,
        val sessionId: String?,
        val linked: Boolean?,
        val warnings: List<Warning>?,
        val player: LoginPlayer?,
        val reason: String?,
        val ban: Ban?,
        val limit: Limit?,
    ) {
        /** The player's real name, null for guests. */
        val shortName: String? get() = player?.displayName
    }

    data class Player(val userId: String, val name: String)

    data class HeartbeatBody(val sessionIds: List<String>)

    /** Sessions the API has closed, such as after an outage longer than 3 minutes. */
    data class HeartbeatResult(val unknown: List<String>)

    data class EndSessionBody(val sessionId: String, val reason: String)

    data class BanBody(val reason: String, val durationSeconds: Long?) : ApiClient.WithNulls

    data class AnticheatFacts(val coreShaders: Boolean, val alteredTextures: Boolean)

    /** A file as the API takes it. Null fields are left out of the request. */
    data class AnticheatFile(
        val kind: String,
        val sha512: String,
        val fingerprint: Long?,
        val filename: String,
        val modId: String?,
        val name: String?,
        val version: String?,
        val loader: String?,
        val facts: AnticheatFacts?,
    )

    data class AnticheatReportBody(val sessionId: String, val files: List<AnticheatFile>)

    /** [sha512] and [name] are set when the reason is about a file. */
    data class VerdictReason(val code: String, val sha512: String?, val name: String?, val action: String)

    /**
     * What to do with the player: `allow`, `flag`, `warn`, `hold`, `refuse`, `kick` or `ban`, from least to most
     * severe. While LNB only observes, [action] is `allow` and [wouldBe] is what it would have been.
     */
    data class Verdict(val action: String, val wouldBe: String?, val reasons: List<VerdictReason>?)

    data class PlayerTrust(val score: Int, val band: String)

    /** [reportId] is null when LNB's anticheat is off: nothing was stored. [pending] files are still being looked up. */
    data class AnticheatReport(
        val reportId: String?,
        val mode: String?,
        val verdict: Verdict?,
        val player: PlayerTrust?,
        val pending: Int,
    )

    data class AnticheatEvent(val id: String, val kind: String, val data: Map<String, Any>)
    data class AnticheatEventsBody(val sessionId: String, val events: List<AnticheatEvent>)
    data class AnticheatEvents(val player: PlayerTrust?, val verdict: Verdict?)

    data class AnticheatActionBody(
        val sessionId: String,
        val reportId: String?,
        val action: String,
        val reason: String,
        val sha512: String?,
    ) : ApiClient.WithNulls

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

    /** A permanent ban of the LNB account behind [uuid], when [durationSeconds] is null. */
    fun ban(uuid: UUID, reason: String, durationSeconds: Long? = null): CompletableFuture<ApiResult<Unit>> =
        call("ban", "POST", "/api/warden/server/players/$uuid/bans", BanBody(reason, durationSeconds))

    /** The files a player's client loaded. The answer says what to do with the player. */
    fun anticheatReport(body: AnticheatReportBody): CompletableFuture<ApiResult<AnticheatReport>> =
        call("anticheat report", "POST", "/api/warden/server/anticheat/reports", body)

    /** Things that cost the player trust. [AnticheatEvent.id] makes the call safe to repeat. */
    fun anticheatEvents(body: AnticheatEventsBody): CompletableFuture<ApiResult<AnticheatEvents>> =
        call("anticheat event", "POST", "/api/warden/server/anticheat/events", body)

    /** Tells LNB what the server did to a player. */
    fun anticheatAction(body: AnticheatActionBody): CompletableFuture<ApiResult<Unit>> =
        call("anticheat action", "POST", "/api/warden/server/anticheat/actions", body)

    /** The latest answer to the reports of a session. 404 `noReport` when it has none. */
    fun anticheatSession(sessionId: String): CompletableFuture<ApiResult<AnticheatReport>> =
        call("anticheat session", "GET", "/api/warden/server/anticheat/sessions/$sessionId")

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
            .thenApply { result ->
                // A feature LNB turned off answers 503: the API itself is fine.
                if (!result.isDisabled) ApiHealth.record(what, result)
                result
            }
    }
}
