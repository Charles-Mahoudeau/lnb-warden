package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.changelog.Changelog
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * The public `Changelog` routes of LNB's API: published posts, no sign-in and no secret, so they work from the title screen
 * as well as in game. They don't know who has seen what; the server tells the client about that.
 */
object ChangelogApi {
    private const val PAGE_SIZE = 10

    /** Short, so a slow site shows an error rather than a screen that never loads. */
    private val TIMEOUT: Duration = Duration.ofSeconds(8)

    /** The newest posts, or the page after [before], a cursor from the previous page. */
    fun list(lang: String, before: String? = null): CompletableFuture<ApiResult<Changelog.PostList>> {
        val cursor = before?.let { "&before=" + URLEncoder.encode(it, StandardCharsets.UTF_8) }.orEmpty()
        return ApiClient.send(
            ClientConfig.apiUrl, "GET", "/api/changelog/public/posts?lang=$lang&limit=$PAGE_SIZE$cursor", null, timeout = TIMEOUT,
        )
    }

    fun post(lang: String, id: String): CompletableFuture<ApiResult<Changelog.Post>> =
        ApiClient.send(
            ClientConfig.apiUrl, "GET", "/api/changelog/public/posts/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "?lang=$lang",
            null, timeout = TIMEOUT,
        )
}
