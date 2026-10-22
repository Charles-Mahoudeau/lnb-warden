package fr.bde_teknantes.lnb.warden.common.network

import io.netty.buffer.Unpooled
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RealNamesPayloadTest {
    @Test
    fun `names and removals round trip`() {
        val names = mapOf(UUID.randomUUID() to "Charles M.", UUID.randomUUID() to null)
        val buf = Unpooled.buffer()
        RealNamesPayload.STREAM_CODEC.encode(buf, RealNamesPayload(names))
        assertEquals(names, RealNamesPayload.STREAM_CODEC.decode(buf).names)
        assertEquals(0, buf.readableBytes())
    }
}
