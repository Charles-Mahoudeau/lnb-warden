package fr.bde_teknantes.lnb.warden.common.network

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogPayloadsTest {
    @Test
    fun `notice round trips`() {
        val payload = ChangelogNoticePayload("0b1f", "Saison 4.2", "Nouveaux quartiers", "Saison 4.2")
        val buf = Unpooled.buffer()
        ChangelogNoticePayload.STREAM_CODEC.encode(buf, payload)
        assertEquals(payload, ChangelogNoticePayload.STREAM_CODEC.decode(buf))
        assertEquals(0, buf.readableBytes())
    }

    @Test
    fun `seen round trips`() {
        val buf = Unpooled.buffer()
        ChangelogSeenPayload.STREAM_CODEC.encode(buf, ChangelogSeenPayload("0b1f"))
        assertEquals(ChangelogSeenPayload("0b1f"), ChangelogSeenPayload.STREAM_CODEC.decode(buf))
    }

    @Test
    fun `oversized fields are refused`() {
        val failed = runCatching {
            ChangelogNoticePayload.STREAM_CODEC.encode(Unpooled.buffer(), ChangelogNoticePayload("id", "t".repeat(500), "", ""))
        }.isFailure
        assertTrue(failed)
    }
}
