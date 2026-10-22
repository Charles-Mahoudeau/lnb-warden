package fr.bde_teknantes.lnb.warden.server.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** `/lnbwarden status`: reports the mod version and whether the API is configured. */
object WardenCommand {
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal(LnbWarden.ID)
                .requires { it.hasPermission(Commands.LEVEL_GAMEMASTERS) }
                .then(Commands.literal("status").executes(::status))
        )
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val version = ModList.get().getModContainerById(LnbWarden.ID)
            .map { it.modInfo.version.toString() }
            .orElse("?")
        val apiState = Component.translatable(
            if (ServerConfig.isApiConfigured) "commands.lnbwarden.status.api.configured"
            else "commands.lnbwarden.status.api.missing"
        )

        context.source.sendSuccess({ Component.translatable("commands.lnbwarden.status", version, apiState) }, false)
        return Command.SINGLE_SUCCESS
    }
}
