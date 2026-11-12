package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.FileDigest
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server -> client, during configuration and in game: asks for the full list of the client's files.
 * [connectionId] is random and unique to this connection: the client binds its challenge answers to it.
 */
class FilesRequestPayload(val connectionId: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FilesRequestPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FilesRequestPayload>(LnbWarden.id("files_request"))

        val STREAM_CODEC: StreamCodec<ByteBuf, FilesRequestPayload> = StreamCodec.composite(
            ByteBufCodecs.byteArray(FileDigest.CONNECTION_ID_SIZE),
            FilesRequestPayload::connectionId,
            ::FilesRequestPayload,
        )
    }
}
