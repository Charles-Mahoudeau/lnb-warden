package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server -> client, in game, once after joining: the newest changelog post this player hasn't seen. The client shows a
 * notice, and the post itself is fetched from LNB's public routes when the player opens it. [summary] and [versionTag] are
 * empty when the post has none.
 */
data class ChangelogNoticePayload(val id: String, val title: String, val summary: String, val versionTag: String) :
    CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ChangelogNoticePayload> = TYPE

    companion object {
        const val MAX_ID_LENGTH = 64
        const val MAX_TITLE_LENGTH = 200
        const val MAX_SUMMARY_LENGTH = 400
        const val MAX_TAG_LENGTH = 64

        val TYPE = CustomPacketPayload.Type<ChangelogNoticePayload>(LnbWarden.id("changelog_notice"))

        val STREAM_CODEC: StreamCodec<ByteBuf, ChangelogNoticePayload> = StreamCodec.of(
            { buf, payload ->
                ByteBufCodecs.stringUtf8(MAX_ID_LENGTH).encode(buf, payload.id)
                ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH).encode(buf, payload.title)
                ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).encode(buf, payload.summary)
                ByteBufCodecs.stringUtf8(MAX_TAG_LENGTH).encode(buf, payload.versionTag)
            },
            { buf ->
                ChangelogNoticePayload(
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_TAG_LENGTH).decode(buf),
                )
            },
        )
    }
}
