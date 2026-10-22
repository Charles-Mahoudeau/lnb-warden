package fr.bde_teknantes.lnb.warden.client.auth

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DataDirTest {
    private val noEnv: (String) -> String? = { null }

    @Test
    fun `override wins`() {
        assertEquals(Path("/tmp/alt"), dataDir("/tmp/alt", "Linux", noEnv, "/home/p"))
    }

    @Test
    fun `windows uses APPDATA`() {
        val env = mapOf("APPDATA" to "C:\\Users\\p\\AppData\\Roaming")
        assertEquals(Path("C:\\Users\\p\\AppData\\Roaming", "lnbwarden"), dataDir(null, "Windows 11", env::get, "C:\\Users\\p"))
    }

    @Test
    fun `windows without APPDATA falls back to home`() {
        assertEquals(Path("/home/p", "lnbwarden"), dataDir(null, "Windows 11", noEnv, "/home/p"))
    }

    @Test
    fun `macOS uses Application Support`() {
        assertEquals(Path("/Users/p/Library/Application Support/lnbwarden"), dataDir(null, "Mac OS X", noEnv, "/Users/p"))
    }

    @Test
    fun `linux uses an absolute XDG_CONFIG_HOME`() {
        val env = mapOf("XDG_CONFIG_HOME" to "/xdg")
        assertEquals(Path("/xdg/lnbwarden"), dataDir(null, "Linux", env::get, "/home/p"))
    }

    @Test
    fun `linux ignores a relative XDG_CONFIG_HOME`() {
        val env = mapOf("XDG_CONFIG_HOME" to "relative")
        assertEquals(Path("/home/p/.config/lnbwarden"), dataDir(null, "Linux", env::get, "/home/p"))
    }
}
