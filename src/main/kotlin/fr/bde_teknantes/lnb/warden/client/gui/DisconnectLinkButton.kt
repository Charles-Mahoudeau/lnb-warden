package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ScreenEvent

/** Adds a Link button to the disconnect screen when LNB's server refused the player for want of a link. */
object DisconnectLinkButton {
    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? DisconnectedScreen ?: return
        if (DisconnectReasons.keyOf(screen.details.reason()) !in DisconnectReasons.NEEDS_LINK) return

        val below = event.listenersList.filterIsInstance<Button>().maxByOrNull { it.y } ?: return
        event.addListener(
            Button.builder(Component.translatable("screen.lnbwarden.status.link")) {
                Minecraft.getInstance().setScreen(LinkScreen(TitleScreen()))
            }.bounds(below.x, below.y + below.height + 4, below.width, below.height).build()
        )
    }
}
