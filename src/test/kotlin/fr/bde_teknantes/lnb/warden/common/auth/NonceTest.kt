package fr.bde_teknantes.lnb.warden.common.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NonceTest {
    @Test
    fun `encodes 32 bytes as unpadded base64url`() {
        val encoded = Nonce.encode(Nonce.generate())
        assertEquals(43, encoded.length)
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(encoded))
    }

    @Test
    fun `uses the url alphabet`() {
        assertEquals("-_8", Nonce.encode(byteArrayOf(0xFB.toByte(), 0xFF.toByte())))
    }
}
