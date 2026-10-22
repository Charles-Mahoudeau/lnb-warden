package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.auth.AuthFailure
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client -> server answer to [AuthChallengePayload]: either a join [ticket], or why there is none. */
sealed class AuthResponsePayload : CustomPacketPayload {
    data class Ticket(val ticket: String) : AuthResponsePayload() {
        // The ticket is a credential until the server has used it.
        override fun toString() = "Ticket(…)"
    }

    data class Failure(val failure: AuthFailure) : AuthResponsePayload()

    override fun type(): CustomPacketPayload.Type<AuthResponsePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuthResponsePayload>(LnbWarden.id("auth_response"))

        private const val TICKET = 0
        private const val FAILURE = 1
        private const val MAX_TICKET_LENGTH = 200

        val STREAM_CODEC: StreamCodec<ByteBuf, AuthResponsePayload> = StreamCodec.of(
            { buf, payload ->
                when (payload) {
                    is Ticket -> {
                        buf.writeByte(TICKET)
                        ByteBufCodecs.stringUtf8(MAX_TICKET_LENGTH).encode(buf, payload.ticket)
                    }
                    is Failure -> {
                        buf.writeByte(FAILURE)
                        ByteBufCodecs.VAR_INT.encode(buf, payload.failure.ordinal)
                    }
                }
            },
            { buf ->
                when (val kind = buf.readByte().toInt()) {
                    TICKET -> Ticket(ByteBufCodecs.stringUtf8(MAX_TICKET_LENGTH).decode(buf))
                    FAILURE -> Failure(
                        AuthFailure.entries.getOrNull(ByteBufCodecs.VAR_INT.decode(buf))
                            ?: throw IllegalArgumentException("Unknown auth failure")
                    )
                    else -> throw IllegalArgumentException("Unknown auth response kind $kind")
                }
            },
        )
    }
}
