package fr.bde_teknantes.lnb.warden.common.anticheat

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.HexFormat

/** What a reported file is. Append only: sent as the ordinal. */
enum class FileKind(val id: String) {
    MOD("mod"),
    RESOURCE_PACK("resourcepack"),
    SHADER("shader"),

    /** The game, the loader and LNB Warden itself: logged, never scored. */
    CLIENT("client"),
}

/** What the client found inside a resource pack. Never read for the other kinds. */
data class PackFacts(
    /** The pack replaces some of the game's core shaders. */
    val coreShaders: Boolean,
    /** A block that hides ores (stone, deepslate...) or an ore has a texture with see-through pixels. */
    val alteredTextures: Boolean,
)

/**
 * One file the client has loaded, as it reports it to the server. Only hashes travel, never the file.
 *
 * Everything here is declared by the client, and capped to what LNB's API accepts.
 */
data class ReportedFile(
    val kind: FileKind,
    /** 128 lowercase hexadecimal characters. */
    val sha512: String,
    /** The CurseForge fingerprint, an unsigned 32-bit integer. Null for a folder. */
    val fingerprint: Long?,
    val filename: String,
    /** The mod IDs the file declares, with those of the jars nested in it. The first one is the file's own. */
    val modIds: List<String> = emptyList(),
    val name: String? = null,
    val version: String? = null,
    val loader: String? = null,
    val facts: PackFacts? = null,
    /** For a jar found inside another (jar-in-jar): the SHA-512 of the top-level file that holds it. */
    val nestedIn: String? = null,
) {
    init {
        require(nestedIn == null || SHA512.matches(nestedIn)) { "Not a SHA-512" }
        require(SHA512.matches(sha512)) { "Not a SHA-512" }
        require(fingerprint == null || fingerprint in 0..0xFFFFFFFFL) { "Not an unsigned 32-bit fingerprint" }
    }

    companion object {
        const val MAX_FILENAME = 255
        const val MAX_TEXT = 128
        const val MAX_MOD_IDS = 32

        private val SHA512 = Regex("[0-9a-f]{128}")
        private val HEX = HexFormat.of()

        private const val HAS_FINGERPRINT = 1
        private const val HAS_NAME = 2
        private const val HAS_VERSION = 4
        private const val HAS_LOADER = 8
        private const val HAS_FACTS = 16
        private const val CORE_SHADERS = 32
        private const val ALTERED_TEXTURES = 64
        private const val HAS_NESTED_IN = 128

        private val FILENAME = ByteBufCodecs.stringUtf8(MAX_FILENAME)
        private val TEXT = ByteBufCodecs.stringUtf8(MAX_TEXT)

        /** Builds a file from what the client read, cutting what is too long and dropping what is blank. */
        fun of(
            kind: FileKind,
            sha512: String,
            fingerprint: Long?,
            filename: String,
            modIds: List<String> = emptyList(),
            name: String? = null,
            version: String? = null,
            loader: String? = null,
            facts: PackFacts? = null,
            nestedIn: String? = null,
        ) = ReportedFile(
            kind,
            sha512.lowercase(),
            fingerprint,
            filename.take(MAX_FILENAME).ifEmpty { "?" },
            modIds.mapNotNull(::text).distinct().take(MAX_MOD_IDS),
            text(name),
            text(version),
            text(loader),
            facts.takeIf { kind == FileKind.RESOURCE_PACK },
            nestedIn?.lowercase(),
        )

        private fun text(value: String?): String? = value?.trim()?.take(MAX_TEXT)?.trim()?.ifEmpty { null }

        val STREAM_CODEC: StreamCodec<ByteBuf, ReportedFile> = StreamCodec.of(
            { buf, file ->
                buf.writeByte(file.kind.ordinal)
                buf.writeBytes(HEX.parseHex(file.sha512))
                var flags = 0
                if (file.fingerprint != null) flags = flags or HAS_FINGERPRINT
                if (file.name != null) flags = flags or HAS_NAME
                if (file.version != null) flags = flags or HAS_VERSION
                if (file.loader != null) flags = flags or HAS_LOADER
                if (file.facts != null) {
                    flags = flags or HAS_FACTS
                    if (file.facts.coreShaders) flags = flags or CORE_SHADERS
                    if (file.facts.alteredTextures) flags = flags or ALTERED_TEXTURES
                }
                if (file.nestedIn != null) flags = flags or HAS_NESTED_IN
                buf.writeByte(flags)
                if (file.fingerprint != null) buf.writeInt(file.fingerprint.toInt())
                FILENAME.encode(buf, file.filename)
                ByteBufCodecs.VAR_INT.encode(buf, file.modIds.size)
                file.modIds.forEach { TEXT.encode(buf, it) }
                file.name?.let { TEXT.encode(buf, it) }
                file.version?.let { TEXT.encode(buf, it) }
                file.loader?.let { TEXT.encode(buf, it) }
                file.nestedIn?.let { buf.writeBytes(HEX.parseHex(it)) }
            },
            { buf ->
                val kind = FileKind.entries.getOrNull(buf.readUnsignedByte().toInt())
                    ?: throw IllegalArgumentException("Unknown file kind")
                val sha512 = ByteArray(64).also(buf::readBytes).let(HEX::formatHex)
                val flags = buf.readUnsignedByte().toInt()
                val fingerprint = if (flags and HAS_FINGERPRINT != 0) buf.readUnsignedInt() else null
                val filename = FILENAME.decode(buf)
                val count = ByteBufCodecs.VAR_INT.decode(buf)
                require(count in 0..MAX_MOD_IDS) { "Too many mod IDs" }
                val modIds = List(count) { TEXT.decode(buf) }
                ReportedFile(
                    kind,
                    sha512,
                    fingerprint,
                    filename,
                    modIds,
                    name = if (flags and HAS_NAME != 0) TEXT.decode(buf) else null,
                    version = if (flags and HAS_VERSION != 0) TEXT.decode(buf) else null,
                    loader = if (flags and HAS_LOADER != 0) TEXT.decode(buf) else null,
                    facts = if (flags and HAS_FACTS != 0) {
                        PackFacts(flags and CORE_SHADERS != 0, flags and ALTERED_TEXTURES != 0)
                    } else {
                        null
                    },
                    nestedIn = if (flags and HAS_NESTED_IN != 0) ByteArray(64).also(buf::readBytes).let(HEX::formatHex) else null,
                )
            },
        )
    }
}
