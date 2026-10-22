package fr.bde_teknantes.lnb.warden.client

import net.neoforged.neoforge.common.ModConfigSpec

/** Per-player preferences, stored in `config/lnbwarden-client.toml`. */
object ClientConfig {
    private val BUILDER = ModConfigSpec.Builder()

    val PONG_IN_ACTION_BAR: ModConfigSpec.BooleanValue = BUILDER
        .comment("Show ping results in the action bar instead of the chat.")
        .define("pongInActionBar", true)

    val SPEC: ModConfigSpec = BUILDER.build()
}
