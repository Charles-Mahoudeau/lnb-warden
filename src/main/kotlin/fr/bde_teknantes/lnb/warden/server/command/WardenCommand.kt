package fr.bde_teknantes.lnb.warden.server.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.AuthMode
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.anticheat.ServerAnticheat
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.events.EventStream
import fr.bde_teknantes.lnb.warden.server.playtime.ServerPlaytime
import fr.bde_teknantes.lnb.warden.server.session.SessionTracker
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `/lnbwarden status`: reports the mod version, the auth mode and the health of the link with LNB's API.
 * `/lnbwarden auth mode <mode>`: changes and saves `auth.mode`.
 */
object WardenCommand {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun register(event: RegisterCommandsEvent) {
        val mode = Commands.literal("mode").executes(::showMode)
        for (authMode in AuthMode.entries) {
            mode.then(Commands.literal(authMode.id).executes { setMode(it, authMode) })
        }

        event.dispatcher.register(Commands.literal("playtime").executes(::playtime))
        event.dispatcher.register(
            Commands.literal(LnbWarden.ID)
                .requires { it.hasPermission(Commands.LEVEL_GAMEMASTERS) }
                .then(Commands.literal("status").executes(::status))
                .then(
                    Commands.literal("info").then(
                        Commands.argument("player", GameProfileArgument.gameProfile()).executes(::info)
                    )
                )
                .then(
                    Commands.literal("auth")
                        // Turning authentication off lets anyone in under any name.
                        .requires { it.hasPermission(Commands.LEVEL_ADMINS) }
                        .then(mode) as LiteralArgumentBuilder<CommandSourceStack>
                )
        )
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val version = ModList.get().getModContainerById(LnbWarden.ID)
            .map { it.modInfo.version.toString() }
            .orElse("?")
        val apiState = Component.translatable(
            if (ServerConfig.isApiConfigured) "commands.lnbwarden.status.api.configured"
            else "commands.lnbwarden.status.api.missing"
        )
        source.sendSuccess({ Component.translatable("commands.lnbwarden.status", version, apiState) }, false)

        source.sendSuccess({ Component.translatable("commands.lnbwarden.status.mode", ServerConfig.AUTH_MODE.get().id) }, false)

        val lastSuccess = ApiHealth.lastSuccess?.let(::format) ?: never()
        val lastError = ApiHealth.lastError
            ?.let { Component.translatable("commands.lnbwarden.status.api_error", format(it.at), it.message) }
            ?: Component.translatable("commands.lnbwarden.status.none")
        source.sendSuccess({ Component.translatable("commands.lnbwarden.status.api_health", lastSuccess, lastError) }, false)

        val stream = when (val status = EventStream.status) {
            is EventStream.Status.Connected ->
                Component.translatable("commands.lnbwarden.status.events.connected", format(status.since))
            is EventStream.Status.Disconnected ->
                Component.translatable("commands.lnbwarden.status.events.disconnected", format(status.since), status.reason)
        }
        source.sendSuccess({ stream }, false)

        val heartbeat = SessionTracker.lastHeartbeat
        val heartbeatState = when {
            heartbeat == null -> never()
            heartbeat.error == null -> Component.translatable("commands.lnbwarden.status.heartbeat.ok", format(heartbeat.at))
            else -> Component.translatable("commands.lnbwarden.status.heartbeat.error", format(heartbeat.at), heartbeat.error)
        }
        source.sendSuccess(
            { Component.translatable("commands.lnbwarden.status.heartbeat", heartbeatState, SessionTracker.count) },
            false,
        )
        val anticheat = if (ServerConfig.ANTICHEAT_ENABLED.get()) {
            Component.translatable("commands.lnbwarden.status.anticheat.on", ServerAnticheat.count)
        } else {
            Component.translatable("commands.lnbwarden.status.anticheat.off")
        }
        source.sendSuccess({ anticheat }, false)
        return Command.SINGLE_SUCCESS
    }

    /** `/playtime`: what is left of the player's weekly allowance. LNB decides it, this only shows it. */
    private fun playtime(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.player
        val message = player?.let { ServerPlaytime.describe(it.uuid) } ?: Component.translatable("commands.lnbwarden.playtime.none")
        context.source.sendSuccess({ message }, false)
        return Command.SINGLE_SUCCESS
    }

    /** `/lnbwarden info <player>`: what LNB knows about this Minecraft account, playtime included. */
    private fun info(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val profile = GameProfileArgument.getGameProfiles(context, "player").first()
        WardenServerApi.player(profile.id).thenAcceptAsync({ result ->
            when {
                result is ApiResult.Ok -> {
                    val player = result.body
                    source.sendSuccess({ Component.translatable("commands.lnbwarden.info.header", profile.name, player.userId) }, false)
                    source.sendSuccess({ playtimeInfo(player.playtime) }, false)
                }
                result is ApiResult.Http && result.status == 404 ->
                    source.sendFailure(Component.translatable("commands.lnbwarden.info.not_linked", profile.name))
                else -> source.sendFailure(Component.translatable("commands.lnbwarden.info.failed", profile.name, result.describe()))
            }
        }, source.server)
        return Command.SINGLE_SUCCESS
    }

    private fun playtimeInfo(playtime: WardenServerApi.Playtime?): Component {
        if (playtime?.enforced != true) return Component.translatable("commands.lnbwarden.info.playtime.off")
        if (playtime.limited != true) return Component.translatable("commands.lnbwarden.info.playtime.unlimited")
        return Component.translatable(
            "commands.lnbwarden.info.playtime",
            playtime.state ?: "?",
            ServerPlaytime.duration(playtime.usedSeconds ?: 0),
            ServerPlaytime.duration(playtime.remainingSeconds ?: 0),
            ServerPlaytime.duration(playtime.limitSeconds ?: 0),
        )
    }

    private fun showMode(context: CommandContext<CommandSourceStack>): Int {
        context.source.sendSuccess({ Component.translatable("commands.lnbwarden.status.mode", ServerConfig.AUTH_MODE.get().id) }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun setMode(context: CommandContext<CommandSourceStack>, mode: AuthMode): Int {
        ServerConfig.AUTH_MODE.set(mode)
        ServerConfig.AUTH_MODE.save()
        LnbWarden.LOGGER.info("{} set auth.mode to {}", context.source.textName, mode.id)
        context.source.sendSuccess({ Component.translatable("commands.lnbwarden.auth.mode.set", mode.id) }, true)
        return Command.SINGLE_SUCCESS
    }

    private fun format(instant: Instant): String = TIME.format(instant)

    private fun never(): Component = Component.translatable("commands.lnbwarden.status.never")
}
