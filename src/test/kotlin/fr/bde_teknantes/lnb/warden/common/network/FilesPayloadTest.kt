package fr.bde_teknantes.lnb.warden.common.network

import fr.bde_teknantes.lnb.warden.common.anticheat.FileKind
import fr.bde_teknantes.lnb.warden.common.anticheat.PackFacts
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilesPayloadTest {
    private fun sha(seed: Int) = "%0128x".format(seed)

    private fun roundTrip(payload: FilesReportPayload): FilesReportPayload {
        val buf = Unpooled.buffer()
        FilesReportPayload.STREAM_CODEC.encode(buf, payload)
        assertTrue(buf.readableBytes() < 32767)
        return FilesReportPayload.STREAM_CODEC.decode(buf).also { assertEquals(0, buf.readableBytes()) }
    }

    @Test
    fun `every kind of file round trips`() {
        val files = listOf(
            ReportedFile.of(FileKind.MOD, sha(1), 0xFFFFFFFFL, "sodium.jar", listOf("sodium", "nested"), "Sodium", "0.6.0", "neoforge"),
            ReportedFile.of(FileKind.RESOURCE_PACK, sha(2), 12, "pack.zip", facts = PackFacts(coreShaders = true, alteredTextures = false)),
            ReportedFile.of(FileKind.RESOURCE_PACK, sha(3), null, "folder", facts = PackFacts(coreShaders = false, alteredTextures = true)),
            ReportedFile.of(FileKind.SHADER, sha(4), 0, "shaders.zip"),
            ReportedFile.of(FileKind.CLIENT, sha(5), 7, "minecraft.jar", listOf("minecraft")),
        )
        val payload = FilesReportPayload(files, last = true)
        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `what the client read is cut to what the API takes`() {
        val file = ReportedFile.of(
            FileKind.MOD, sha(1).uppercase(), 1, "é".repeat(400), listOf(" a ", "", "a", "b"), name = "  ", version = "v".repeat(300),
            facts = PackFacts(true, true),
        )
        assertEquals(sha(1), file.sha512)
        assertEquals(255, file.filename.length)
        assertEquals(listOf("a", "b"), file.modIds)
        assertNull(file.name)
        assertEquals(128, file.version?.length)
        // Facts are only about resource packs.
        assertNull(file.facts)
        assertEquals(file, roundTrip(FilesReportPayload(listOf(file), true)).files.single())
    }

    @Test
    fun `a bad hash or fingerprint is refused`() {
        assertFailsWith<IllegalArgumentException> { ReportedFile.of(FileKind.MOD, "abc", 1, "x.jar") }
        assertFailsWith<IllegalArgumentException> { ReportedFile.of(FileKind.MOD, sha(1), -1, "x.jar") }
    }

    @Test
    fun `a long list is cut in parts that fit a payload`() {
        val files = List(700) { ReportedFile.of(FileKind.MOD, sha(it), it.toLong(), "é".repeat(255), listOf("mod$it"), "N".repeat(128)) }
        val parts = FilesReportPayload.split(files)
        assertTrue(parts.size > 1)
        assertTrue(parts.last().last)
        assertTrue(parts.dropLast(1).none { it.last })
        assertEquals(files, parts.flatMap { roundTrip(it).files })
    }

    @Test
    fun `an empty list is still one part`() {
        assertEquals(listOf(FilesReportPayload(emptyList(), last = true)), FilesReportPayload.split(emptyList()))
    }

    @Test
    fun `the other payloads round trip`() {
        val buf = Unpooled.buffer()
        val id = ByteArray(16) { it.toByte() }
        FilesRequestPayload.STREAM_CODEC.encode(buf, FilesRequestPayload(id))
        assertContentEquals(id, FilesRequestPayload.STREAM_CODEC.decode(buf).connectionId)

        val nonce = ByteArray(32) { (it * 3).toByte() }
        FilesChallengePayload.STREAM_CODEC.encode(buf, FilesChallengePayload(nonce))
        assertContentEquals(nonce, FilesChallengePayload.STREAM_CODEC.decode(buf).nonce)

        FilesChallengeResponsePayload.STREAM_CODEC.encode(buf, FilesChallengeResponsePayload(nonce))
        assertContentEquals(nonce, FilesChallengeResponsePayload.STREAM_CODEC.decode(buf).digest)
        assertEquals(0, buf.readableBytes())
    }
}
