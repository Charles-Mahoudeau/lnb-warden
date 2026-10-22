package fr.bde_teknantes.lnb.warden.common.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Outcome of a call to LNB's API. Never throws: every failure is a value. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val body: T) : ApiResult<T>

    /** The API answered with an error status. [problem] is `data.problem` from the error body, when there is one. */
    data class Http(val status: Int, val problem: String?) : ApiResult<Nothing>

    /** The request didn't get an answer: DNS, connection refused, timeout, malformed body... */
    data class Network(val cause: Throwable) : ApiResult<Nothing>

    /** No answer, or an answer from a broken API. Both mean "try again later". */
    val isUnreachable: Boolean
        get() = this is Network || (this is Http && status >= 500)

    fun describe(): String = when (this) {
        is Ok -> "OK"
        is Http -> if (problem != null) "HTTP $status ($problem)" else "HTTP $status"
        is Network -> cause.message ?: cause.javaClass.simpleName
    }
}

class InsecureApiUrlException(url: String) :
    IllegalStateException("Refusing to send credentials to $url: use https, or http only on localhost")

/**
 * Shared HTTP access to LNB's API, for the client and the server.
 *
 * All calls are asynchronous: callers must hop back to the game thread before touching game state.
 * The bearer token is passed per call and never logged.
 */
object ApiClient {
    val GSON = Gson()

    val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(15)

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    /** HTTPS anywhere, or plain HTTP on the local machine for development. */
    fun isSecure(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> isLoopback(uri)
            else -> false
        }
    }

    private fun isLoopback(uri: URI): Boolean = uri.host?.lowercase() in LOOPBACK_HOSTS

    fun request(baseUrl: String, path: String, token: String?): HttpRequest.Builder {
        val uri = URI(baseUrl.trimEnd('/') + path)
        val builder = HttpRequest.newBuilder(uri)
            .header("Accept", "application/json")
            .timeout(DEFAULT_TIMEOUT)
        // Over plain HTTP, HTTP/2 tries an h2c upgrade, which Node's dev server answers by closing.
        if (isLoopback(uri)) builder.version(HttpClient.Version.HTTP_1_1)
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder
    }

    /**
     * Sends a JSON request and decodes the answer as [type], or as [Unit] for empty answers.
     * With [requireSecure], nothing is sent to a URL that fails [isSecure]: a request carries the device token,
     * or its answer does.
     */
    fun <T> send(
        baseUrl: String,
        method: String,
        path: String,
        token: String?,
        body: Any?,
        type: Class<T>,
        requireSecure: Boolean = true,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): CompletableFuture<ApiResult<T>> {
        if (requireSecure && !isSecure(baseUrl)) {
            return CompletableFuture.completedFuture(ApiResult.Network(InsecureApiUrlException(baseUrl)))
        }

        val request = try {
            val publisher = if (body == null) HttpRequest.BodyPublishers.noBody()
            else HttpRequest.BodyPublishers.ofString(GSON.toJson(body))
            request(baseUrl, path, token)
                .timeout(timeout)
                .apply { if (body != null) header("Content-Type", "application/json") }
                .method(method, publisher)
                .build()
        } catch (e: IllegalArgumentException) {
            // Malformed apiUrl.
            return CompletableFuture.completedFuture(ApiResult.Network(e))
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> decode(response, type) }
            .exceptionally { ApiResult.Network(it.cause ?: it) }
    }

    inline fun <reified T> send(
        baseUrl: String,
        method: String,
        path: String,
        token: String?,
        body: Any? = null,
        requireSecure: Boolean = true,
        timeout: Duration? = null,
    ): CompletableFuture<ApiResult<T>> =
        if (timeout == null) send(baseUrl, method, path, token, body, T::class.java, requireSecure)
        else send(baseUrl, method, path, token, body, T::class.java, requireSecure, timeout)

    private fun <T> decode(response: HttpResponse<String>, type: Class<T>): ApiResult<T> {
        val status = response.statusCode()
        if (status !in 200..299) return ApiResult.Http(status, problemOf(response.body()))

        @Suppress("UNCHECKED_CAST")
        if (type == Unit::class.java) return ApiResult.Ok(Unit as T)

        return try {
            ApiResult.Ok(GSON.fromJson(response.body(), type) ?: error("Empty body"))
        } catch (e: Exception) {
            ApiResult.Network(e)
        }
    }

    private fun problemOf(body: String?): String? = try {
        val json = JsonParser.parseString(body ?: return null)
        (json as? JsonObject)?.getAsJsonObject("data")?.get("problem")?.asString
    } catch (_: Exception) {
        null
    }
}
