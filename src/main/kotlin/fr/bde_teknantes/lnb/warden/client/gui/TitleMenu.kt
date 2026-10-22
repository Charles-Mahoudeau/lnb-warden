package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.client.LnbServer
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * Reshapes the title screen's menu: Multiplayer goes straight to LNB's server, Realms is gone, and Mods shares its
 * row with the account button, whose badge shows the LNB link's state. Leaving a server comes back to it.
 */
object TitleMenu {
    private var started = false

    /** Set when the startup check found no usable token: the link screen opens on the next tick. */
    private var offerLink = false

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? TitleScreen ?: return
        // Only on the first title screen: closing the link screen comes back here, and must not reopen it.
        if (!started) {
            started = true
            offerLink = !ClientAuth.start()
        }

        replaceMultiplayerButton(event, screen)
        replaceRealmsButton(event, screen)
    }

    /**
     * Set when leaving a server. The pause menu's Disconnect button then opens the server list, which the Multiplayer
     * button no longer leads to: the title screen replaces it.
     */
    private var leftServer = false

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        leftServer = true
    }

    fun onScreenOpening(event: ScreenEvent.Opening) {
        if (!leftServer) return
        when (event.newScreen) {
            is JoinMultiplayerScreen -> {
                leftServer = false
                event.newScreen = TitleScreen()
            }
            // A kick or singleplayer: back to the title screen already.
            is TitleScreen, is DisconnectedScreen -> leftServer = false
        }
    }

    /** Opens the link screen once, when the game starts without a usable token. */
    fun tick() {
        if (!offerLink) return
        val minecraft = Minecraft.getInstance()
        val screen = minecraft.screen as? TitleScreen ?: return
        offerLink = false
        minecraft.setScreen(LinkScreen(screen))
    }

    private fun replaceMultiplayerButton(event: ScreenEvent.Init.Post, screen: TitleScreen) {
        val multiplayer = event.listenersList.filterIsInstance<Button>().firstOrNull {
            (it.message.contents as? TranslatableContents)?.key == "menu.multiplayer"
        } ?: return
        event.removeListener(multiplayer)
        // No token is fine: the server decides whether linking is required, and says so.
        val replacement = Button.builder(multiplayer.message) { LnbServer.connect(screen) }
            .bounds(multiplayer.x, multiplayer.y, multiplayer.width, multiplayer.height)
            .tooltip(multiplayer.tooltip)
            .build()
        replacement.active = multiplayer.active
        event.addListener(replacement)
    }

    /**
     * Removes Realms, moves Mods up to its row at half width beside the LNB account button, and moves the rows below
     * up by one. Leaves the menu alone when it isn't the one expected (demo mode, another mod's layout).
     */
    private fun replaceRealmsButton(event: ScreenEvent.Init.Post, screen: TitleScreen) {
        val buttons = event.listenersList.filterIsInstance<AbstractWidget>()
        val realms = buttons.firstOrNull { it.key == "menu.online" } ?: return
        val mods = buttons.firstOrNull { it.key == "fml.menu.mods" } ?: return
        if (mods.y <= realms.y) return

        event.removeListener(realms)
        val shift = mods.y - realms.y
        val half = (realms.width - 4) / 2
        mods.setPosition(realms.x, realms.y)
        mods.width = half
        event.addListener(
            AccountButton(
                Button.builder(Component.translatable("screen.lnbwarden.account.button")) {
                    Minecraft.getInstance().setScreen(AccountScreen(screen))
                }.bounds(realms.x + half + 4, realms.y, realms.width - half - 4, realms.height)
            )
        )

        // Options, Quit, Language and Accessibility; not the copyright line at the very bottom.
        for (widget in buttons) {
            if (widget.y > mods.y + shift && widget.y < screen.height - 10) widget.y -= shift
        }
    }

    private val AbstractWidget.key: String?
        get() = (message.contents as? TranslatableContents)?.key
}
