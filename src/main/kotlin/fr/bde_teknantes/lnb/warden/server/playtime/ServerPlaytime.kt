package fr.bde_teknantes.lnb.warden.server.playtime

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.api.disconnectReason
import fr.bde_teknantes.lnb.warden.server.events.EventStream
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows and obeys the playtime limits that LNB decides. Everything here happens on the server thread.
 *
 * The state of each limited player comes from the login, the `playtimeWarning`, `playtimeGrace` and `playtimeUpdate`
 * events and the heartbeat. LNB sends the kick itself, within seconds of the allowance reaching zero. Only while the
 * event stream is down does a local timer kick at zero, so nobody plays for free during an outage.
 * Nothing is counted or stored here.
 */
object ServerPlaytime {
    private val views = ConcurrentHashMap<UUID, PlaytimeView>()

    private const val CHECK_TICKS = 20
    private var ticks = 0

    fun viewOf(uuid: UUID): PlaytimeView? = views[uuid]

    /** The state of a player who just joined, from the login. No announcement: the events announce what's needed. */
    fun onLogin(uuid: UUID, playtime: WardenServerApi.Playtime?) {
        if (playtime?.enforced == true) views[uuid] = PlaytimeView(playtime, Util.getMillis()) else views.remove(uuid)
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        views.remove(event.entity.uuid)
    }

    /** `playtimeUpdate`, or the heartbeat's answer: replaces the state, which can cancel a countdown. */
    fun onUpdate(server: MinecraftServer, uuid: UUID, playtime: WardenServerApi.Playtime) {
        val player = server.playerList.getPlayer(uuid) ?: return
        val before = views[uuid]
        onLogin(uuid, playtime)
        val after = views[uuid]
        if (before?.isCounting == true && after?.isCounting != true && after?.isGrace != true) {
            player.sendSystemMessage(Component.translatable("chat.lnbwarden.playtime.cancelled").withStyle(ChatFormatting.GREEN))
        }
    }

    /** `playtimeWarning`. [kind] is `allowance`, or `windowStart` for a player with no time left. */
    fun onWarning(server: MinecraftServer, uuid: UUID, remainingSeconds: Long, kind: String?) {
        val player = server.playerList.getPlayer(uuid) ?: return
        val time = duration(remainingSeconds)
        if (kind == "windowStart") {
            player.sendSystemMessage(Component.translatable("chat.lnbwarden.playtime.window_start", time).withStyle(ChatFormatting.RED))
            return
        }
        val current = views[uuid]?.state ?: WardenServerApi.Playtime(enforced = true, limited = true)
        views[uuid] = PlaytimeView(
            current.copy(enforced = true, limited = true, counting = true, grace = false, remainingSeconds = remainingSeconds),
            Util.getMillis(),
        )

        player.connection.send(ClientboundSetTitlesAnimationPacket(10, 60, 20))
        player.connection.send(
            ClientboundSetTitleTextPacket(Component.translatable("title.lnbwarden.playtime.warning", time).withStyle(ChatFormatting.GOLD))
        )
        player.sendSystemMessage(Component.translatable("chat.lnbwarden.playtime.warning", time).withStyle(ChatFormatting.GOLD))
    }

    /** `playtimeGrace`: the allowance ran out, but the player may stay until [graceEndsAt]. Replaces the kick. */
    fun onGrace(server: MinecraftServer, uuid: UUID, graceEndsAt: String?) {
        val player = server.playerList.getPlayer(uuid) ?: return
        val current = views[uuid]?.state ?: WardenServerApi.Playtime(enforced = true, limited = true)
        views[uuid] = PlaytimeView(
            current.copy(enforced = true, limited = true, grace = true, graceEndsAt = graceEndsAt, remainingSeconds = 0),
            Util.getMillis(),
        )
        player.sendSystemMessage(
            Component.translatable("chat.lnbwarden.playtime.grace", PlaytimeView.formatDate(graceEndsAt) ?: "?").withStyle(ChatFormatting.YELLOW)
        )
    }

    /** The `kick` event with the reason `playtimeLimit`. */
    fun kick(player: ServerPlayer, playtime: WardenServerApi.Playtime?) {
        views.remove(player.uuid)
        player.connection.disconnect(playtime.disconnectReason())
    }

    /** Every second: with LNB's event stream down, the countdown that ran out kicks, as a backup. */
    fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks < CHECK_TICKS) return
        ticks = 0
        if (views.isEmpty() || EventStream.status !is EventStream.Status.Disconnected) return
        val now = Util.getMillis()
        for ((uuid, view) in views) {
            val deadline = view.deadlineMs ?: continue
            if (now < deadline) continue
            val player = event.server.playerList.getPlayer(uuid)
            if (player == null) {
                views.remove(uuid)
                continue
            }
            LnbWarden.LOGGER.info("LNB's event stream is down and {}'s playtime ran out: kicking them", player.gameProfile.name)
            kick(player, view.state)
        }
    }

    fun duration(seconds: Long): Component {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = s % 3600 / 60
        return when {
            hours > 0 -> Component.translatable("playtime.lnbwarden.hours", hours, minutes)
            minutes > 0 -> Component.translatable("playtime.lnbwarden.minutes", minutes)
            else -> Component.translatable("playtime.lnbwarden.seconds", s)
        }
    }

    /** What `/playtime` tells [uuid]. */
    fun describe(uuid: UUID): Component {
        val view = views[uuid]
        if (view == null || !view.isLimited) return Component.translatable("commands.lnbwarden.playtime.none")
        val state = view.state
        if (view.isGrace) {
            val until = PlaytimeView.formatDate(state.graceEndsAt)
            return if (until != null) Component.translatable("commands.lnbwarden.playtime.grace", until)
            else Component.translatable("commands.lnbwarden.playtime.grace_open")
        }
        val left = duration(view.remainingSeconds(Util.getMillis()) ?: 0)
        if (state.counting == true) {
            val until = PlaytimeView.formatDate(state.windowEndsAt)
            return if (until != null) Component.translatable("commands.lnbwarden.playtime.counting", left, until)
            else Component.translatable("commands.lnbwarden.playtime.counting_open", left)
        }
        val next = PlaytimeView.formatDate(state.nextWindowStartsAt)
        return if (next != null) Component.translatable("commands.lnbwarden.playtime.free_next", left, next)
        else Component.translatable("commands.lnbwarden.playtime.free", left)
    }
}
