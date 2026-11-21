package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client -> server, in game: the player opened the post of the notice, so LNB can stop offering it. */
data class ChangelogSeenPayload(val id: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ChangelogSeenPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ChangelogSeenPayload>(LnbWarden.id("changelog_seen"))

        val STREAM_CODEC: StreamCodec<ByteBuf, ChangelogSeenPayload> = StreamCodec.of(
            { buf, payload -> ByteBufCodecs.stringUtf8(ChangelogNoticePayload.MAX_ID_LENGTH).encode(buf, payload.id) },
            { buf -> ChangelogSeenPayload(ByteBufCodecs.stringUtf8(ChangelogNoticePayload.MAX_ID_LENGTH).decode(buf)) },
        )
    }
}
