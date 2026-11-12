package fr.bde_teknantes.lnb.warden.client.anticheat

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class FileHasherTest {
    /** MurmurHash2 as it is usually written, over a whole array, to check the streaming one against. */
    private fun murmur2(data: ByteArray, seed: Int): Long {
        val m = 0x5bd1e995
        var h = seed xor data.size
        var i = 0
        while (data.size - i >= 4) {
            var k = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8) or
                ((data[i + 2].toInt() and 0xFF) shl 16) or ((data[i + 3].toInt() and 0xFF) shl 24)
            k *= m
            k = k xor (k ushr 24)
            k *= m
            h = (h * m) xor k
            i += 4
        }
        val left = data.size - i
        if (left >= 3) h = h xor ((data[i + 2].toInt() and 0xFF) shl 16)
        if (left >= 2) h = h xor ((data[i + 1].toInt() and 0xFF) shl 8)
        if (left >= 1) {
            h = h xor (data[i].toInt() and 0xFF)
            h *= m
        }
        h = h xor (h ushr 13)
        h *= m
        h = h xor (h ushr 15)
        return h.toLong() and 0xFFFFFFFFL
    }

    @Test
    fun `sha512 of a known input`() {
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            FileHasher.sha512("abc".toByteArray()),
        )
    }

    @Test
    fun `the fingerprint ignores whitespace`() {
        assertEquals(FileHasher.fingerprint("abcdefg".toByteArray()), FileHasher.fingerprint("a b\tc\nd\re f g ".toByteArray()))
        assertNotEquals(FileHasher.fingerprint("abcdefg".toByteArray()), FileHasher.fingerprint("abcdefh".toByteArray()))
    }

    @Test
    fun `the fingerprint is murmur2 with seed 1`() {
        for (size in listOf(0, 1, 2, 3, 4, 5, 7, 8, 1023, 70_000)) {
            // No whitespace bytes, so nothing is stripped.
            val data = ByteArray(size) { (33 + Random(size).nextInt(200)).toByte() }.map { if (it.toInt() == 32) 33 else it }.toByteArray()
            assertEquals(murmur2(data, 1), FileHasher.fingerprint(data), "size $size")
        }
    }

    @Test
    fun `a file is hashed like its bytes`(@TempDir dir: Path) {
        val bytes = Random(7).nextBytes(200_000)
        val file = dir.resolve("mod.jar").also { it.writeBytes(bytes) }
        val hashes = FileHasher.hash(file)
        assertEquals(FileHasher.sha512(bytes), hashes.sha512)
        assertEquals(FileHasher.fingerprint(bytes), hashes.fingerprint)
    }

    @Test
    fun `a changed file is hashed again`(@TempDir dir: Path) {
        val file = dir.resolve("mod.jar").also { it.writeText("one") }
        val first = FileHasher.hash(file)
        file.writeText("another")
        assertNotEquals(first, FileHasher.hash(file))
    }

    @Test
    fun `a folder is hashed by its paths and contents`(@TempDir dir: Path) {
        fun pack(name: String, path: String, content: String): Path {
            val root = dir.resolve(name)
            root.resolve(path).also { it.parent.createDirectories() }.writeText(content)
            return root
        }
        val a = FileHasher.hash(pack("a", "assets/x.txt", "hello"))
        assertNull(a.fingerprint)
        assertEquals(a.sha512, FileHasher.hash(pack("b", "assets/x.txt", "hello")).sha512)
        assertNotEquals(a.sha512, FileHasher.hash(pack("c", "assets/y.txt", "hello")).sha512)
        assertNotEquals(a.sha512, FileHasher.hash(pack("d", "assets/x.txt", "hullo")).sha512)
        Files.createDirectories(dir.resolve("empty"))
        assertNotEquals(a.sha512, FileHasher.hash(dir.resolve("empty")).sha512)
    }
}
