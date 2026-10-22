package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.Optional
import java.util.UUID

/**
 * Server -> client, in game: the real names of online players, shown under their name tag and in the tab list.
 * Merged into what the client already knows; a null name removes the player's.
 */
class RealNamesPayload(val names: Map<UUID, String?>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RealNamesPayload> = TYPE

    companion object {
        const val MAX_NAME_LENGTH = 64
        private const val MAX_PLAYERS = 10_000

        val TYPE = CustomPacketPayload.Type<RealNamesPayload>(LnbWarden.id("real_names"))

        val STREAM_CODEC: StreamCodec<ByteBuf, RealNamesPayload> = ByteBufCodecs.map(
            ::HashMap,
            UUIDUtil.STREAM_CODEC,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH)),
            MAX_PLAYERS,
        ).map(
            { names -> RealNamesPayload(names.mapValues { (_, name) -> name.orElse(null) }) },
            { payload -> HashMap(payload.names.mapValues { (_, name) -> Optional.ofNullable(name) }) },
        )
    }
}
