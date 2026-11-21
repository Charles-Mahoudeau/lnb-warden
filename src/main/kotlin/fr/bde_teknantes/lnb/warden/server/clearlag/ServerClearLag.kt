package fr.bde_teknantes.lnb.warden.server.clearlag

import fr.bde_teknantes.lnb.warden.LnbWarden
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.OwnableEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.time.Instant

/**
 * Removes dropped items, experience orbs and hostile mobs on a schedule, and keeps chunks under a limit per entity type.
 * Everything here happens on the server thread. Nothing is stored: the schedule starts again with the server.
 */
object ServerClearLag {
    /** What a clear removed. */
    data class Result(val at: Instant, val items: Int, val orbs: Int, val mobs: Int, val manual: Boolean) {
        val total get() = items + orbs + mobs
    }

    private val schedule = ClearSchedule()
    private var chunkTicks = 0

    var lastResult: Result? = null
        private set

    /** Whether the last tick found the server lagging. */
    var lagging = false
        private set

    var averageMspt = 0.0
        private set

    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        if (!server.isDedicatedServer || !ClearLagConfig.ENABLED.get()) return

        averageMspt = server.averageTickTimeNanos / 1_000_000.0
        val threshold = ClearLagConfig.LAG_MSPT.get()
        lagging = threshold > 0 && averageMspt > threshold
        val interval = if (lagging) {
            minOf(ClearLagConfig.INTERVAL_SECONDS.get(), ClearLagConfig.LAG_INTERVAL_SECONDS.get())
        } else {
            ClearLagConfig.INTERVAL_SECONDS.get()
        }

        val step = schedule.tick(interval, ClearLagConfig.WARNING_SECONDS.get())
        if (step.fire) {
            clear(server, manual = false)
            schedule.reset()
        } else {
            announce(server, step)
        }

        if (ClearLagConfig.CHUNK_LIMITS_ENABLED.get() &&
            ++chunkTicks >= ClearLagConfig.CHUNK_LIMITS_CHECK_SECONDS.get() * ClearSchedule.TPS
        ) {
            chunkTicks = 0
            enforceChunkLimits(server)
        }
    }

    /** Starts the countdown again, e.g. after the config changed. */
    fun restart() = schedule.reset()

    /** Seconds before the next clear, for `/lnbwarden clearlag status`. */
    fun secondsLeft(): Int {
        val interval = if (lagging) {
            minOf(ClearLagConfig.INTERVAL_SECONDS.get(), ClearLagConfig.LAG_INTERVAL_SECONDS.get())
        } else {
            ClearLagConfig.INTERVAL_SECONDS.get()
        }
        return Mth.ceil(((interval * ClearSchedule.TPS - schedule.elapsedTicks).coerceAtLeast(0)) / ClearSchedule.TPS.toDouble())
    }

    private fun announce(server: MinecraftServer, step: ClearSchedule.Step) {
        val seconds = Mth.ceil(step.remainingTicks / ClearSchedule.TPS.toDouble())
        val barSeconds = ClearLagConfig.ACTION_BAR_SECONDS.get()
        if (barSeconds > 0 && seconds <= barSeconds && step.remainingTicks % ClearSchedule.TPS == 0L) {
            server.playerList.broadcastSystemMessage(
                Component.translatable("actionbar.lnbwarden.clearlag", seconds).withStyle(ChatFormatting.YELLOW), true,
            )
        }
        val mark = step.warnMark ?: return
        server.playerList.broadcastSystemMessage(
            Component.translatable("chat.lnbwarden.clearlag.warning", mark).withStyle(ChatFormatting.GOLD), false,
        )
    }

    /** Removes what the config targets, outside the protection rules. */
    fun clear(server: MinecraftServer, manual: Boolean): Result {
        var items = 0
        var orbs = 0
        var mobs = 0
        val rules = Rules.load()
        for (level in server.allLevels) {
            val players = level.players().filter { !it.isSpectator }
            for (entity in level.allEntities.toList()) {
                when {
                    entity is ItemEntity && ClearLagConfig.CLEAR_ITEMS.get() ->
                        if (rules.canClearItem(entity, players)) { entity.discard(); items++ }
                    entity is ExperienceOrb && ClearLagConfig.CLEAR_XP_ORBS.get() ->
                        if (rules.canClear(entity, players)) { entity.discard(); orbs++ }
                    entity is Mob && entity.type.category == MobCategory.MONSTER && ClearLagConfig.CLEAR_HOSTILE_MOBS.get() ->
                        if (rules.canClearMob(entity, players)) { entity.discard(); mobs++ }
                }
            }
        }
        val result = Result(Instant.now(), items, orbs, mobs, manual)
        lastResult = result
        LnbWarden.LOGGER.info("ClearLag removed {} item(s), {} experience orb(s), {} hostile mob(s)", items, orbs, mobs)
        if (ClearLagConfig.ANNOUNCE_RESULT.get()) {
            server.playerList.broadcastSystemMessage(
                Component.translatable("chat.lnbwarden.clearlag.result", items, orbs, mobs).withStyle(ChatFormatting.GREEN), false,
            )
        }
        return result
    }

    private fun enforceChunkLimits(server: MinecraftServer) {
        val caps = ClearLagConfig.CHUNK_LIMITS.get().mapNotNull(ChunkCap::parse)
            .mapNotNull { cap ->
                ResourceLocation.tryParse(cap.type)?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null) }?.let { it to cap.limit }
            }
            .toMap()
        if (caps.isEmpty()) return
        val rules = Rules.load()
        var removed = 0
        for (level in server.allLevels) {
            val players = level.players().filter { !it.isSpectator }
            // Chunk position and type, then the entities that can go.
            val groups = HashMap<Pair<Long, EntityType<*>>, MutableList<Entity>>()
            for (entity in level.allEntities) {
                if (entity.type !in caps || entity is Player) continue
                groups.getOrPut(entity.chunkPosition().toLong() to entity.type) { ArrayList() }.add(entity)
            }
            for ((key, entities) in groups) {
                val limit = caps.getValue(key.second)
                if (entities.size <= limit) continue
                // Spared entities still count toward the limit but can't be removed.
                val removable = entities.filter { rules.canLimit(it, players) }
                val excess = entities.size - limit
                val ages = removable.map(::ageOf)
                for (index in ChunkCaps.excess(ages, removable.size - excess.coerceAtMost(removable.size))) {
                    removable[index].discard()
                    removed++
                }
            }
        }
        if (removed > 0) LnbWarden.LOGGER.info("ClearLag chunk limits removed {} entit(y/ies)", removed)
    }

    private fun ageOf(entity: Entity) = if (entity is ItemEntity) entity.age else entity.tickCount

    /** The protection rules of the config at one moment. */
    private class Rules(
        val minItemAgeTicks: Int,
        val keepItems: List<IdPattern>,
        val onlyItems: List<IdPattern>,
        val keepTypes: List<IdPattern>,
        val nearSquared: Double,
    ) {
        fun canClearItem(entity: ItemEntity, players: List<Player>): Boolean {
            val stack = entity.item
            if (entity.age < minItemAgeTicks) return false
            if (keepItems.any { matches(it, stack) }) return false
            if (onlyItems.isNotEmpty() && onlyItems.none { matches(it, stack) }) return false
            return !isNearPlayer(entity, players) && !isKeptType(entity)
        }

        fun canClear(entity: Entity, players: List<Player>) = !isNearPlayer(entity, players) && !isKeptType(entity)

        fun canClearMob(mob: Mob, players: List<Player>) = canLimit(mob, players)

        /** The rules that apply to any entity a chunk limit removes. */
        fun canLimit(entity: Entity, players: List<Player>): Boolean {
            if (entity is ItemEntity && (entity.age < minItemAgeTicks || keepItems.any { matches(it, entity.item) })) return false
            if (entity is Mob) {
                if (ClearLagConfig.KEEP_NAMED.get() && entity.hasCustomName()) return false
                if (ClearLagConfig.KEEP_TAMED.get() && entity is OwnableEntity && entity.ownerUUID != null) return false
                if (ClearLagConfig.KEEP_LEASHED.get() && entity.isLeashed) return false
                if (ClearLagConfig.KEEP_PERSISTENT.get() && entity.isPersistenceRequired) return false
            }
            return !isNearPlayer(entity, players) && !isKeptType(entity)
        }

        private fun isKeptType(entity: Entity) = keepTypes.any { pattern ->
            if (pattern.isTag) {
                entity.type.`is`(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(pattern.id)))
            } else {
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == pattern.id
            }
        }

        private fun isNearPlayer(entity: Entity, players: List<Player>) =
            nearSquared > 0 && players.any { it.distanceToSqr(entity) <= nearSquared }

        private fun matches(pattern: IdPattern, stack: ItemStack): Boolean =
            if (pattern.isTag) {
                stack.`is`(TagKey.create(Registries.ITEM, ResourceLocation.parse(pattern.id)))
            } else {
                BuiltInRegistries.ITEM.getKey(stack.item).toString() == pattern.id
            }

        companion object {
            fun load(): Rules {
                val near = ClearLagConfig.KEEP_NEAR_PLAYERS_BLOCKS.get().toDouble()
                return Rules(
                    ClearLagConfig.MIN_ITEM_AGE_SECONDS.get() * 20,
                    ClearLagConfig.KEEP_ITEMS.get().mapNotNull(IdPattern::parse),
                    ClearLagConfig.ONLY_ITEMS.get().mapNotNull(IdPattern::parse),
                    ClearLagConfig.KEEP_ENTITY_TYPES.get().mapNotNull(IdPattern::parse),
                    near * near,
                )
            }
        }
    }
}
