package fr.bde_teknantes.lnb.warden.client.auth

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Where LNB Warden keeps data that belongs to the OS user rather than to one game instance.
 * `-Dlnbwarden.dataDir=<path>` overrides it, to test several accounts on one machine.
 */
fun dataDir(
    override: String? = System.getProperty("lnbwarden.dataDir"),
    osName: String = System.getProperty("os.name", ""),
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
): Path {
    if (!override.isNullOrBlank()) return Path(override)

    val os = osName.lowercase()
    val base = when {
        os.startsWith("windows") -> env("APPDATA")?.takeIf { it.isNotBlank() }?.let(::Path) ?: Path(home)
        os.startsWith("mac") -> Path(home, "Library", "Application Support")
        else -> env("XDG_CONFIG_HOME")?.let(::Path)?.takeIf { it.isAbsolute } ?: Path(home, ".config")
    }
    return base.resolve("lnbwarden")
}
