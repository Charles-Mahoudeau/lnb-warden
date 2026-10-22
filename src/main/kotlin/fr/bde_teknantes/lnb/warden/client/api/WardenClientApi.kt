package fr.bde_teknantes.lnb.warden.client.api

import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** The `Warden: clients` routes of LNB's API, authenticated with the device token. */
object WardenClientApi {
    data class LinkRequestBody(val deviceName: String)

    data class LinkRequest(
        val requestId: String,
        val pollSecret: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
        val expiresAt: String,
        val expiresIn: Double,
        val interval: Double,
    ) {
        override fun toString() = "LinkRequest(requestId=$requestId, expiresAt=$expiresAt)"
    }

    data class PollBody(val pollSecret: String) {
        override fun toString() = "PollBody(…)"
    }

    data class PollUser(val id: String, val name: String)

    /** [token], [expiresAt] and [user] are only set when [status] is `approved`. */
    data class PollResult(val status: String, val token: String?, val expiresAt: String?, val user: PollUser?) {
        override fun toString() = "PollResult(status=$status, user=$user)"
    }

    data class MeUser(val id: String, val name: String, val shortName: String, val showRealName: Boolean)
    /** [lastSeenAt] is null until the account first joins the server. */
    data class Account(
        val uuid: String,
        val username: String,
        val lastSeenAt: String?,
        val online: Boolean,
        val playtimeSeconds: Double,
    )
    data class Limit(val used: Int, val max: Int)
    data class Ban(val reason: String, val expiresAt: String?)
    data class Device(val id: String, val name: String, val expiresAt: String)

    data class Me(
        val user: MeUser,
        val accounts: List<Account>,
        val limit: Limit,
        val activeBan: Ban?,
        val device: Device,
    )

    data class JoinTicketBody(val nonce: String, val username: String, val link: Boolean)

    data class JoinTicket(val ticket: String, val expiresAt: String) {
        override fun toString() = "JoinTicket(expiresAt=$expiresAt)"
    }

    private data class PresenceBody(val state: String)

    /** What the player sees in their device list on the website. Never the hostname. */
    val deviceName: String
        get() = "LNB Warden on ${System.getProperty("os.name", "an unknown OS")}".take(64)

    fun createLinkRequest(): CompletableFuture<ApiResult<LinkRequest>> =
        ApiClient.send(ClientConfig.apiUrl, "POST", "/api/warden/client/link-requests", null, LinkRequestBody(deviceName))

    fun poll(request: LinkRequest): CompletableFuture<ApiResult<PollResult>> =
        ApiClient.send(
            ClientConfig.apiUrl,
            "POST",
            "/api/warden/client/link-requests/${request.requestId}/poll",
            null,
            PollBody(request.pollSecret),
        )

    fun me(token: String): CompletableFuture<ApiResult<Me>> =
        ApiClient.send(ClientConfig.apiUrl, "GET", "/api/warden/client/me", token)

    fun joinTicket(token: String, body: JoinTicketBody): CompletableFuture<ApiResult<JoinTicket>> =
        ApiClient.send(ClientConfig.apiUrl, "POST", "/api/warden/client/join-tickets", token, body)

    fun putPresence(token: String): CompletableFuture<ApiResult<Unit>> =
        ApiClient.send(ClientConfig.apiUrl, "PUT", "/api/warden/client/presence", token, PresenceBody("menu"))

    fun deletePresence(token: String, timeout: Duration): CompletableFuture<ApiResult<Unit>> =
        ApiClient.send(ClientConfig.apiUrl, "DELETE", "/api/warden/client/presence", token, timeout = timeout)

    fun signOut(token: String): CompletableFuture<ApiResult<Unit>> =
        ApiClient.send(ClientConfig.apiUrl, "POST", "/api/warden/client/sign-out", token)
}
