package fr.bde_teknantes.lnb.warden.common.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiClientTest {
    @Test
    fun `https is secure`() {
        assertTrue(ApiClient.isSecure("https://lnb.bde-teknantes.fr"))
    }

    @Test
    fun `http is only allowed on loopback`() {
        assertTrue(ApiClient.isSecure("http://localhost:3000"))
        assertTrue(ApiClient.isSecure("http://127.0.0.1:3000"))
        assertTrue(ApiClient.isSecure("http://[::1]:3000"))
        assertFalse(ApiClient.isSecure("http://lnb.bde-teknantes.fr"))
        assertFalse(ApiClient.isSecure("http://localhost.evil.example"))
        assertFalse(ApiClient.isSecure("ftp://localhost"))
        assertFalse(ApiClient.isSecure("not a url"))
    }

    @Test
    fun `nothing is sent to an insecure URL`() {
        val result = ApiClient.send<Unit>("http://lnb.example", "GET", "/api/warden/client/me", "lnbw_x").get()
        assertIs<ApiResult.Network>(result)
        assertIs<InsecureApiUrlException>(result.cause)
    }

    @Test
    fun `extra headers are sent, but never replace the client's own`() {
        val seen = HashMap<String, String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen["units"] = exchange.requestHeaders.getFirst("X-Units")
            seen["auth"] = exchange.requestHeaders.getFirst("Authorization")
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}"
            val result = ApiClient.send<Unit>(url, "GET", "/x", "secret", headers = mapOf("X-Units" to "cents", "Authorization" to "Bearer other"))
                .get(5, TimeUnit.SECONDS)
            assertIs<ApiResult.Ok<Unit>>(result)
            assertEquals("cents", seen["units"])
            assertEquals("Bearer secret", seen["auth"])
        } finally {
            server.stop(0)
        }
    }
}
