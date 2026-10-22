package fr.bde_teknantes.lnb.warden.client

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import fr.bde_teknantes.lnb.warden.client.gui.DisconnectLinkButton
import fr.bde_teknantes.lnb.warden.client.gui.TitleMenu
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.GameShuttingDownEvent

/**
 * Client entrypoint. This class is never loaded on a dedicated server,
 * so it is safe to reference client-only classes (Minecraft, screens, rendering...) from here.
 */
@Mod(value = LnbWarden.ID, dist = [Dist.CLIENT])
class LnbWardenClient(modBus: IEventBus, container: ModContainer) {
    init {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)
        // Adds a "Config" button for this mod in the Mods screen.
        container.registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory { mod, parent -> ConfigurationScreen(mod, parent) },
        )

        NeoForge.EVENT_BUS.addListener(TitleMenu::onInit)
        NeoForge.EVENT_BUS.addListener(TitleMenu::onLoggingOut)
        NeoForge.EVENT_BUS.addListener(TitleMenu::onScreenOpening)
        NeoForge.EVENT_BUS.addListener(DisconnectLinkButton::onInit)
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> {
            ClientAuth.tick()
            TitleMenu.tick()
            Presence.tick()
        }
        NeoForge.EVENT_BUS.addListener(Presence::onLoggingIn)
        NeoForge.EVENT_BUS.addListener(Presence::onLoggingOut)
        NeoForge.EVENT_BUS.addListener<GameShuttingDownEvent> { Presence.onShutdown() }
    }
}
