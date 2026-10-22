package fr.bde_teknantes.lnb.warden.server

import com.electronwill.nightconfig.core.EnumGetMethod
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Server-side settings, stored in `config/lnbwarden-server.toml`.
 *
 * Registered as a COMMON config (not SERVER) on purpose: SERVER configs are synced to every
 * connecting client, which would leak the API token. Only logical-server code should read this.
 */
object ServerConfig {
    private val BUILDER = ModConfigSpec.Builder()
        .comment("Connection to LNB's API. Only read by the server, never sent to players.")
        .push("api")

    val API_URL: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment("Base URL of LNB's API.")
        .define("url", "")

    val API_TOKEN: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment("Secret token used to authenticate with LNB's API. Do not share this file.")
        .define("token", "")

    /** Read when each connection starts, so an edit applies to the next player who joins. */
    val AUTH_MODE: ModConfigSpec.EnumValue<AuthMode> = BUILDER
        .pop()
        .comment("How players are authenticated with LNB Warden.")
        .push("auth")
        .comment(
            "required: every player must be allowed by LNB's API.",
            "optional: players without the mod or a link join as guests, unless their username is linked to an LNB account.",
            "off: no authentication and no API calls, for development and private test servers.",
        )
        .defineEnum("mode", AuthMode.REQUIRED, EnumGetMethod.NAME_IGNORECASE)

    val SPEC: ModConfigSpec = BUILDER.pop().build()

    val isApiConfigured: Boolean
        get() = API_URL.get().isNotBlank() && API_TOKEN.get().isNotBlank()
}
