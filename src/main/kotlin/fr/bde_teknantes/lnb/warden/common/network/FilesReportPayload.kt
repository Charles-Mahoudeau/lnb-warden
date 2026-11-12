package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client -> server, during configuration and in game: one part of the list of the client's files.
 *
 * A list is cut in parts because a payload sent to the server can't be larger than 32 KiB.
 * The parts of a list are sent in order, and the last one has [last] set.
 */
data class FilesReportPayload(val files: List<ReportedFile>, val last: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FilesReportPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FilesReportPayload>(LnbWarden.id("files_report"))

        /** The most files the API takes in one report. */
        const val MAX_FILES = 1000

        /** Well under the 32,767 bytes a server-bound payload may weigh. */
        const val MAX_PART_BYTES = 24_000

        private val FILES = ReportedFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES))

        val STREAM_CODEC: StreamCodec<ByteBuf, FilesReportPayload> = StreamCodec.composite(
            FILES, FilesReportPayload::files,
            ByteBufCodecs.BOOL, FilesReportPayload::last,
            ::FilesReportPayload,
        )

        /** Cuts [files] into parts that each fit a payload. Always at least one part, so an empty list is still an answer. */
        fun split(files: List<ReportedFile>): List<FilesReportPayload> {
            val parts = mutableListOf<List<ReportedFile>>()
            var part = mutableListOf<ReportedFile>()
            var size = 0
            val buf = Unpooled.buffer()
            for (file in files.take(MAX_FILES)) {
                buf.clear()
                ReportedFile.STREAM_CODEC.encode(buf, file)
                val weight = buf.readableBytes()
                if (part.isNotEmpty() && size + weight > MAX_PART_BYTES) {
                    parts += part
                    part = mutableListOf()
                    size = 0
                }
                part += file
                size += weight
            }
            parts += part
            buf.release()
            return parts.mapIndexed { index, list -> FilesReportPayload(list, last = index == parts.lastIndex) }
        }
    }
}
