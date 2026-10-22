package fr.bde_teknantes.lnb.warden.client.auth

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import fr.bde_teknantes.lnb.warden.LnbWarden
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** The device token and who it belongs to, as stored in `session.json`. */
data class Session(
    val version: Int,
    val apiUrl: String,
    val token: String,
    val expiresAt: String,
    val userId: String,
    val name: String,
) {
    val expiry: Instant?
        get() = try {
            Instant.parse(expiresAt)
        } catch (_: DateTimeParseException) {
            null
        }

    // Never print the token.
    override fun toString() = "Session(userId=$userId, name=$name, apiUrl=$apiUrl, expiresAt=$expiresAt)"

    companion object {
        const val VERSION = 1
    }
}

/** Reads and writes `session.json` in [dir], private to the OS user. */
class SessionStore(private val dir: Path) {
    private val file: Path = dir.resolve("session.json")

    /** The stored session, or null when there is none usable for [apiUrl]. */
    fun load(apiUrl: String, now: Instant = Instant.now()): Session? {
        if (!file.exists()) return null
        val session = try {
            GSON.fromJson(file.readText(), Session::class.java)
        } catch (e: IOException) {
            LnbWarden.LOGGER.warn("Couldn't read {}: {}", file, e.toString())
            return null
        } catch (e: JsonParseException) {
            LnbWarden.LOGGER.warn("Ignoring malformed {}", file)
            return null
        }

        // Gson leaves missing fields null, whatever the Kotlin types say.
        @Suppress("SENSELESS_COMPARISON")
        if (session == null || session.token == null || session.apiUrl == null || session.userId == null) return null
        if (session.apiUrl.trimEnd('/') != apiUrl.trimEnd('/')) {
            LnbWarden.LOGGER.info("Ignoring the LNB link for {}: the configured API is {}", session.apiUrl, apiUrl)
            return null
        }
        val expiry = session.expiry
        if (expiry != null && !expiry.isAfter(now)) return null
        return session
    }

    /** Writes [session] atomically, readable only by the OS user where the file system allows it. */
    fun save(session: Session) {
        val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()
        if (posix) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PRIVATE_DIR))
            Files.setPosixFilePermissions(dir, PRIVATE_DIR)
        } else {
            Files.createDirectories(dir)
        }

        val temp = if (posix) Files.createTempFile(dir, "session", ".tmp", PosixFilePermissions.asFileAttribute(PRIVATE_FILE))
        else Files.createTempFile(dir, "session", ".tmp")
        try {
            temp.writeText(GSON.toJson(session))
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun delete() {
        try {
            Files.deleteIfExists(file)
        } catch (e: IOException) {
            LnbWarden.LOGGER.warn("Couldn't delete {}: {}", file, e.toString())
        }
    }

    private companion object {
        val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val PRIVATE_DIR = PosixFilePermissions.fromString("rwx------")
        val PRIVATE_FILE = PosixFilePermissions.fromString("rw-------")
    }
}
