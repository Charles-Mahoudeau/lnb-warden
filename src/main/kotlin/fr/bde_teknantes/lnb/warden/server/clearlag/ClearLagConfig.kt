package fr.bde_teknantes.lnb.warden.server.clearlag

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * ClearLag settings, stored in `config/lnbwarden-clearlag.toml`. NeoForge reloads the file when it changes,
 * and every value is read when it is used, so an edit applies without a restart.
 */
object ClearLagConfig {
    private val BUILDER = ModConfigSpec.Builder()

    val ENABLED: ModConfigSpec.BooleanValue = BUILDER
        .comment(
            "ClearLag removes dropped items, experience orbs and hostile mobs at regular intervals, and keeps",
            "each chunk under a limit of entities. Only a dedicated server runs it.",
        )
        .define("enabled", true)

    val INTERVAL_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("Time between two clears.")
        .push("schedule")
        .defineInRange("intervalSeconds", 600, 10, 86_400)

    val LAG_MSPT: ModConfigSpec.DoubleValue = BUILDER
        .comment(
            "Lag mode: while the server's average tick takes longer than this many milliseconds, clears come every",
            "lagIntervalSeconds instead. 50 is the limit of 20 TPS. 0 turns the lag mode off.",
        )
        .defineInRange("lagMspt", 45.0, 0.0, 1000.0)

    val LAG_INTERVAL_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("Time between two clears while the server lags. Shorter than intervalSeconds to have an effect.")
        .defineInRange("lagIntervalSeconds", 120, 10, 86_400)

    val WARNING_SECONDS: ModConfigSpec.ConfigValue<List<Int>> = BUILDER
        .comment("Chat warnings, as the number of seconds left before a clear. Marks longer than the interval are skipped.")
        .define("warningSeconds", listOf(60, 30, 10, 5, 3, 2, 1)) { list ->
            list is List<*> && list.all { it is Int && it > 0 }
        }

    val ACTION_BAR_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("The countdown shows in the action bar during these last seconds. 0 turns it off.")
        .defineInRange("actionBarSeconds", 10, 0, 3600)

    val ANNOUNCE_RESULT: ModConfigSpec.BooleanValue = BUILDER
        .comment("Whether the chat tells what a clear removed.")
        .define("announceResult", true)

    val CLEAR_ITEMS: ModConfigSpec.BooleanValue = BUILDER
        .pop()
        .comment("What a clear removes.")
        .push("targets")
        .define("items", true)

    val CLEAR_XP_ORBS: ModConfigSpec.BooleanValue = BUILDER.define("experienceOrbs", true)

    val CLEAR_HOSTILE_MOBS: ModConfigSpec.BooleanValue = BUILDER
        .comment("Monsters. Passive mobs are never cleared, only limited per chunk if you list them in chunkLimits.")
        .define("hostileMobs", true)

    val MIN_ITEM_AGE_SECONDS: ModConfigSpec.IntValue = BUILDER
        .pop()
        .comment("What a clear and the chunk limits spare.")
        .push("protection")
        .comment("Items younger than this are spared, so what a player just dropped or what a death left isn't swept.")
        .defineInRange("minItemAgeSeconds", 30, 0, 3600)

    val KEEP_ITEMS: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("Items that are never cleared: ids (minecraft:diamond) or tags (#c:ores).")
        .define("keepItems", listOf("minecraft:netherite_ingot", "minecraft:elytra", "minecraft:nether_star", "#c:storage_blocks/netherite"), ::isIdList)

    val ONLY_ITEMS: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("If not empty, only these items are cleared (ids or tags). keepItems still wins.")
        .define("onlyItems", emptyList<String>(), ::isIdList)

    val KEEP_ENTITY_TYPES: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("Entity types that are never cleared nor limited: ids or tags.")
        .define("keepEntityTypes", listOf("minecraft:ender_dragon", "minecraft:wither", "#c:bosses"), ::isIdList)

    val KEEP_NAMED: ModConfigSpec.BooleanValue = BUILDER
        .comment("Mobs with a custom name (a name tag) are spared.")
        .define("keepNamed", true)

    val KEEP_TAMED: ModConfigSpec.BooleanValue = BUILDER
        .comment("Mobs that have an owner are spared.")
        .define("keepTamed", true)

    val KEEP_LEASHED: ModConfigSpec.BooleanValue = BUILDER
        .comment("Leashed mobs are spared.")
        .define("keepLeashed", true)

    val KEEP_PERSISTENT: ModConfigSpec.BooleanValue = BUILDER
        .comment("Mobs that don't despawn (picked up an item, renamed, spawned by an egg...) are spared.")
        .define("keepPersistent", true)

    val KEEP_NEAR_PLAYERS_BLOCKS: ModConfigSpec.IntValue = BUILDER
        .comment("Everything within this distance of a player (not a spectator) is spared. 0 turns it off.")
        .defineInRange("keepNearPlayersBlocks", 8, 0, 256)

    val CHUNK_LIMITS_ENABLED: ModConfigSpec.BooleanValue = BUILDER
        .pop()
        .comment(
            "Chunk limits keep a chunk from holding too many entities of one type, between two clears.",
            "The oldest entities go first. The protection rules apply.",
        )
        .push("chunkLimits")
        .define("enabled", true)

    val CHUNK_LIMITS_CHECK_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("Time between two checks.")
        .defineInRange("checkSeconds", 10, 1, 3600)

    val CHUNK_LIMITS: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("One limit per entity type, as type=maximum per chunk. Any non-player entity type works, e.g. minecraft:zombie=30.")
        .define("limits", listOf("minecraft:item=100", "minecraft:experience_orb=50"), ::isCapList)

    val SPEC: ModConfigSpec = BUILDER.pop().build()

    private fun isIdList(value: Any?) = value is List<*> && value.all { it is String && IdPattern.parse(it) != null }

    private fun isCapList(value: Any?) = value is List<*> && value.all { it is String && ChunkCap.parse(it) != null }
}
