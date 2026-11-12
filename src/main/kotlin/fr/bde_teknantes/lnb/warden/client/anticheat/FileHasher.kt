package fr.bde_teknantes.lnb.warden.client.anticheat

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * The two hashes LNB looks a file up with: its SHA-512 (Modrinth) and its CurseForge fingerprint.
 * Results are kept in memory for as long as the file's size and date don't change.
 */
object FileHasher {
    /** [fingerprint] is null for a folder: CurseForge only knows files. */
    data class Hashes(val sha512: String, val fingerprint: Long?)

    private data class Stamp(val size: Long, val modified: Long)

    private val cache = ConcurrentHashMap<Path, Pair<Stamp, Hashes>>()
    private val HEX = HexFormat.of()
    private const val BUFFER = 1 shl 16

    fun hash(path: Path): Hashes {
        val stamp = if (path.isRegularFile()) Stamp(path.fileSize(), path.getLastModifiedTime().toMillis()) else folderStamp(path)
        cache[path]?.let { (cached, hashes) -> if (cached == stamp) return hashes }
        val hashes = if (path.isRegularFile()) hashFile(path) else Hashes(folderSha512(path), null)
        cache[path] = stamp to hashes
        return hashes
    }

    private fun hashFile(path: Path): Hashes {
        // The fingerprint starts from the number of bytes it reads, so they are counted on the first pass.
        val sha512 = MessageDigest.getInstance("SHA-512")
        var length = 0
        val buffer = ByteArray(BUFFER)
        path.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sha512.update(buffer, 0, read)
                for (i in 0 until read) if (!isSkipped(buffer[i])) length++
            }
        }
        val murmur = Murmur2(length)
        path.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                for (i in 0 until read) if (!isSkipped(buffer[i])) murmur.update(buffer[i])
            }
        }
        return Hashes(HEX.formatHex(sha512.digest()), murmur.finish())
    }

    fun sha512(bytes: ByteArray): String = HEX.formatHex(MessageDigest.getInstance("SHA-512").digest(bytes))

    /** CurseForge's fingerprint: MurmurHash2 with seed 1, over the file without its whitespace bytes. */
    fun fingerprint(bytes: ByteArray): Long {
        val kept = bytes.filterNot(::isSkipped)
        val murmur = Murmur2(kept.size)
        kept.forEach(murmur::update)
        return murmur.finish()
    }

    /** Tab, line feed, carriage return and space. */
    private fun isSkipped(byte: Byte): Boolean = byte.toInt() == 9 || byte.toInt() == 10 || byte.toInt() == 13 || byte.toInt() == 32

    /** A folder has no bytes of its own: its hash covers the path and the content of each file in it, in order. */
    private fun folderSha512(folder: Path): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(BUFFER)
        for (file in filesOf(folder)) {
            digest.update(file.relativeTo(folder).invariantSeparatorsPathString.toByteArray())
            digest.update(0)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.update(0)
        }
        return HEX.formatHex(digest.digest())
    }

    private fun folderStamp(folder: Path): Stamp {
        var size = 0L
        var modified = 0L
        for (file in filesOf(folder)) {
            // The count is part of the size, so adding an empty file changes the stamp.
            size += file.fileSize() + 1
            modified = maxOf(modified, file.getLastModifiedTime().toMillis())
        }
        return Stamp(size, modified)
    }

    private fun filesOf(folder: Path): List<Path> =
        Files.walk(folder).use { paths -> paths.filter { it.isRegularFile() }.sorted().toList() }

    private class Murmur2(length: Int) {
        private var h = SEED xor length
        private var word = 0
        private var filled = 0

        fun update(byte: Byte) {
            word = word or ((byte.toInt() and 0xFF) shl (8 * filled))
            if (++filled < 4) return
            var k = word * M
            k = k xor (k ushr R)
            k *= M
            h = (h * M) xor k
            word = 0
            filled = 0
        }

        fun finish(): Long {
            if (filled > 0) {
                h = h xor word
                h *= M
            }
            h = h xor (h ushr 13)
            h *= M
            h = h xor (h ushr 15)
            return h.toLong() and 0xFFFFFFFFL
        }

        private companion object {
            const val SEED = 1
            const val M = 0x5bd1e995
            const val R = 24
        }
    }
}
