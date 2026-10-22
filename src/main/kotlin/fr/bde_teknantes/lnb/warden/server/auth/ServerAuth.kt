package fr.bde_teknantes.lnb.warden.server.auth

import com.mojang.authlib.GameProfile
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.network.AuthResponsePayload
import fr.bde_teknantes.lnb.warden.server.AuthMode
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.session.ServerRealNames
import fr.bde_teknantes.lnb.warden.server.session.SessionTracker
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What the server learnt about a player while authenticating them, shown once they spawn. */
data class PendingJoin(
    /** Null for guests. */
    val sessionId: String?,
    val warnings: List<WardenServerApi.Warning>,
    /** Whether this login just linked the Minecraft account. */
    val linked: Boolean,
    /** The player's real name, when they show it on LNB. */
    val shortName: String?,
    /** Why the player plays as a guest, or null when they are authenticated. */
    val guestReason: Component?,
)

/** Runs [AuthTask] for each connection to a dedicated server, unless `auth.mode` is `off`. */
object ServerAuth {
    private val tasks = ConcurrentHashMap<ServerConfigurationPacketListenerImpl, AuthTask>()
    private val pending = ConcurrentHashMap<UUID, PendingJoin>()

    fun registerTasks(event: RegisterConfigurationTasksEvent) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        // Singleplayer and LAN games never call the API.
        if (!server.isDedicatedServer) return
        val mode = ServerConfig.AUTH_MODE.get()
        if (mode == AuthMode.OFF) return
        val listener = event.listener as? ServerConfigurationPacketListenerImpl ?: return

        val task = AuthTask(listener, server, mode)
        tasks[listener] = task
        event.register(task)
    }

    fun onResponse(listener: Any, response: AuthResponsePayload) {
        val task = tasks[listener]
        if (task == null) {
            LnbWarden.LOGGER.debug("Ignoring an unexpected LNB auth response")
            return
        }
        task.onResponse(response)
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        if (tasks.isEmpty()) return
        tasks.entries.removeIf { (_, task) -> !task.isConnected }
        tasks.values.forEach(AuthTask::tick)
    }

    fun onAllowed(profile: GameProfile, join: PendingJoin) {
        tasks.entries.removeIf { (listener, _) -> listener.owner.id == profile.id }
        pending[profile.id] = join
    }

    /** Takes what [AuthTask] learnt about [uuid], once. */
    fun takePending(uuid: UUID): PendingJoin? = pending.remove(uuid)

    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val join = takePending(player.uuid)
        ServerRealNames.onJoin(player, join?.shortName)
        if (join == null) return
        join.sessionId?.let { SessionTracker.track(player.uuid, it) }
        greet(player, join)
    }

    private fun greet(player: ServerPlayer, join: PendingJoin) {
        join.guestReason?.let { reason ->
            player.sendSystemMessage(
                Component.translatable("chat.lnbwarden.guest", reason).withStyle(ChatFormatting.YELLOW)
            )
        }
        if (join.linked) {
            player.sendSystemMessage(
                Component.translatable("chat.lnbwarden.linked", player.gameProfile.name).withStyle(ChatFormatting.GREEN)
            )
        }
        for (warning in join.warnings) {
            player.sendSystemMessage(
                Component.translatable("chat.lnbwarden.warning", warning.body).withStyle(ChatFormatting.GOLD)
            )
        }
    }
}
