package fr.bde_teknantes.lnb.warden.client

import net.neoforged.neoforge.common.ModConfigSpec

/** Per-player preferences, stored in `config/lnbwarden-client.toml`. */
object ClientConfig {
    private val BUILDER = ModConfigSpec.Builder()

    val API_URL: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment("Base URL of LNB's API. Your LNB link is only valid for the API that issued it.")
        .define("apiUrl", "https://lnb.bde-teknantes.fr")

    val SERVER_ADDRESS: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment(
            "Where the Multiplayer button connects.",
            "Also the only server LNB Warden authenticates you on.",
        )
        .define("serverAddress", "mc.bde-teknantes.fr")

    val SPEC: ModConfigSpec = BUILDER.build()

    val apiUrl: String
        get() = API_URL.get().trim().trimEnd('/')
}
