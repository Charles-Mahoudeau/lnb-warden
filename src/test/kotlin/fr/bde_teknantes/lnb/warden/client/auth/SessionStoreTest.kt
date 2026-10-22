package fr.bde_teknantes.lnb.warden.client.auth

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class SessionStoreTest {
    @TempDir
    lateinit var temp: Path

    private val api = "https://lnb.example"
    private val session = Session(Session.VERSION, api, "lnbw_secret", "2027-01-20T21:00:00Z", "u1", "Charles M.")

    @Test
    fun `round trip`() {
        val store = SessionStore(temp.resolve("lnbwarden"))
        store.save(session)
        assertEquals(session, store.load(api, Instant.parse("2026-10-22T00:00:00Z")))
        assertEquals(session, store.load("$api/", Instant.parse("2026-10-22T00:00:00Z")))
    }

    @Test
    fun `file and directory are private`() {
        val dir = temp.resolve("lnbwarden")
        SessionStore(dir).save(session)
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("session.json"))))
        assertEquals(listOf(dir.resolve("session.json")), dir.listDirectoryEntries())
    }

    @Test
    fun `ignores another API`() {
        val store = SessionStore(temp)
        store.save(session)
        assertNull(store.load("http://localhost:3000"))
    }

    @Test
    fun `ignores an expired token`() {
        val store = SessionStore(temp)
        store.save(session)
        assertNull(store.load(api, Instant.parse("2027-01-21T00:00:00Z")))
    }

    @Test
    fun `ignores a malformed file`() {
        Files.writeString(temp.resolve("session.json"), "{\"version\": 1")
        assertNull(SessionStore(temp).load(api))
        Files.writeString(temp.resolve("session.json"), "{}")
        assertNull(SessionStore(temp).load(api))
    }

    @Test
    fun `delete removes the file`() {
        val store = SessionStore(temp)
        store.save(session)
        store.delete()
        assertNull(store.load(api))
    }

    @Test
    fun `toString hides the token`() {
        assertFalse("lnbw_secret" in session.toString())
        SessionStore(temp).save(session)
        // The file itself does hold it.
        assert("lnbw_secret" in temp.resolve("session.json").readText())
    }
}
