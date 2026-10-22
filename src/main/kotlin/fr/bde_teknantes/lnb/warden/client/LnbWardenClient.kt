package fr.bde_teknantes.lnb.warden.client

import fr.bde_teknantes.lnb.warden.LnbWarden
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge

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

        modBus.addListener(WardenKeyMappings::register)
        NeoForge.EVENT_BUS.addListener(WardenKeyMappings::onClientTick)
    }
}
