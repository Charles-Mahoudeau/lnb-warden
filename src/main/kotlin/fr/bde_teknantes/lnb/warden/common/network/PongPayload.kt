package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Server -> client reply to [PingPayload]. */
data class PongPayload(val sentAt: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PongPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PongPayload>(LnbWarden.id("pong"))

        val STREAM_CODEC: StreamCodec<ByteBuf, PongPayload> =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, PongPayload::sentAt, ::PongPayload)
    }
}
