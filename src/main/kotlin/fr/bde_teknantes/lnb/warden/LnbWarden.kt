package fr.bde_teknantes.lnb.warden

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.warden.common.network.WardenNetwork
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.WardenServer
import fr.bde_teknantes.lnb.warden.server.auth.ServerAuth
import fr.bde_teknantes.lnb.warden.server.events.EventStream
import fr.bde_teknantes.lnb.warden.server.session.ServerRealNames
import fr.bde_teknantes.lnb.warden.server.session.SessionTracker
import fr.bde_teknantes.lnb.warden.server.command.WardenCommand
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.Logger

/**
 * Common entrypoint, loaded on both the physical client and the dedicated server.
 *
 * Logical-server code (commands, server-bound packet handling, API access) is registered from here
 * because singleplayer runs an integrated server inside the client.
 * Client-only code lives in [fr.bde_teknantes.lnb.warden.client.LnbWardenClient].
 */
@Mod(LnbWarden.ID)
class LnbWarden(modBus: IEventBus, container: ModContainer) {
    init {
        // COMMON configs are read from the local config/ folder and are never synced to clients,
        // unlike SERVER configs, which is why server-only secrets go here.
        container.registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC, "$ID-server.toml")

        modBus.addListener(WardenNetwork::register)
        modBus.addListener(ServerAuth::registerTasks)
        NeoForge.EVENT_BUS.addListener(WardenCommand::register)
        NeoForge.EVENT_BUS.addListener(ServerAuth::onServerTick)
        NeoForge.EVENT_BUS.addListener(ServerAuth::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(SessionTracker::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(ServerRealNames::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(SessionTracker::onServerTick)
        NeoForge.EVENT_BUS.addListener(EventStream::onServerTick)
        NeoForge.EVENT_BUS.addListener(WardenServer::onStarted)
        NeoForge.EVENT_BUS.addListener(WardenServer::onStopping)
    }

    companion object {
        const val ID = "lnbwarden"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)
    }
}
