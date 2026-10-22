package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.common.auth.AuthFailure
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthResponsePayloadTest {
    private fun roundTrip(payload: AuthResponsePayload): AuthResponsePayload {
        val buf = Unpooled.buffer()
        AuthResponsePayload.STREAM_CODEC.encode(buf, payload)
        return AuthResponsePayload.STREAM_CODEC.decode(buf).also { assertEquals(0, buf.readableBytes()) }
    }

    @Test
    fun `ticket round trip`() {
        val payload = AuthResponsePayload.Ticket("lnbt_abc")
        assertEquals(payload, roundTrip(payload))
        assertFalse("lnbt_abc" in payload.toString())
    }

    @Test
    fun `every failure round trips`() {
        for (failure in AuthFailure.entries) {
            assertEquals(AuthResponsePayload.Failure(failure), roundTrip(AuthResponsePayload.Failure(failure)))
        }
    }

    @Test
    fun `challenge round trip`() {
        val nonce = ByteArray(AuthChallengePayload.NONCE_SIZE) { it.toByte() }
        val buf = Unpooled.buffer()
        AuthChallengePayload.STREAM_CODEC.encode(buf, AuthChallengePayload(nonce))
        assertContentEquals(nonce, AuthChallengePayload.STREAM_CODEC.decode(buf).nonce)
    }
}
