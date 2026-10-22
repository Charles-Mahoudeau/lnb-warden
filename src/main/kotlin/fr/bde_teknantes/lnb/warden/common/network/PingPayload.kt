package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client -> server. [sentAt] is the client's monotonic clock, echoed back in [PongPayload]. */
data class PingPayload(val sentAt: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PingPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PingPayload>(LnbWarden.id("ping"))

        val STREAM_CODEC: StreamCodec<ByteBuf, PingPayload> =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, PingPayload::sentAt, ::PingPayload)
    }
}
