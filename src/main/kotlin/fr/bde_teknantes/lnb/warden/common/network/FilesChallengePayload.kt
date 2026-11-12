package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.FileDigest
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Server -> client, in game: the client must answer with the [FileDigest] of its files for this [nonce]. */
class FilesChallengePayload(val nonce: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FilesChallengePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FilesChallengePayload>(LnbWarden.id("files_challenge"))

        val STREAM_CODEC: StreamCodec<ByteBuf, FilesChallengePayload> = StreamCodec.composite(
            ByteBufCodecs.byteArray(FileDigest.NONCE_SIZE),
            FilesChallengePayload::nonce,
            ::FilesChallengePayload,
        )
    }
}

/** Client -> server answer to [FilesChallengePayload]. */
class FilesChallengeResponsePayload(val digest: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FilesChallengeResponsePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FilesChallengeResponsePayload>(LnbWarden.id("files_challenge_response"))

        val STREAM_CODEC: StreamCodec<ByteBuf, FilesChallengeResponsePayload> = StreamCodec.composite(
            ByteBufCodecs.byteArray(FileDigest.SIZE),
            FilesChallengeResponsePayload::digest,
            ::FilesChallengeResponsePayload,
        )
    }
}
