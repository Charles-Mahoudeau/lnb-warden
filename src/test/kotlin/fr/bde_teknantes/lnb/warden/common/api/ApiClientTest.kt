package fr.bde_teknantes.lnb.warden.common.api

import kotlin.test.Test
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
}
