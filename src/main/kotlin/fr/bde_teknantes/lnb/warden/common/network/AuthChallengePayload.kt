package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Server -> client, during configuration. [nonce] is 32 random bytes the client's join ticket must be bound to. */
class AuthChallengePayload(val nonce: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AuthChallengePayload> = TYPE

    companion object {
        const val NONCE_SIZE = 32

        val TYPE = CustomPacketPayload.Type<AuthChallengePayload>(LnbWarden.id("auth_challenge"))

        val STREAM_CODEC: StreamCodec<ByteBuf, AuthChallengePayload> =
            StreamCodec.composite(ByteBufCodecs.byteArray(NONCE_SIZE), AuthChallengePayload::nonce, ::AuthChallengePayload)
    }
}
