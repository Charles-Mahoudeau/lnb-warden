package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineLabel
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.Connection
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * Asks the player, while they join LNB's server, whether to link this Minecraft account to their LNB account.
 *
 * Shown in place of the connecting screen, so it keeps ticking the [connection] like that screen does:
 * without it, packets aren't flushed and a disconnection isn't noticed.
 */
class ConfirmLinkScreen(
    private val previous: Screen?,
    private val connection: Connection,
    private val username: String,
    private val me: WardenClientApi.Me,
    private val onChoice: (accepted: Boolean) -> Unit,
) : Screen(Component.translatable("screen.lnbwarden.confirm.title")) {
    private val full = me.limit.used >= me.limit.max
    private var chosen = false
    private var message = MultiLineLabel.EMPTY

    override fun init() {
        val name = Component.literal(username).withStyle(ChatFormatting.BOLD)
        val text = if (full) {
            Component.translatable("screen.lnbwarden.confirm.full", me.limit.used, me.limit.max, name)
        } else {
            Component.translatable("screen.lnbwarden.confirm.question", name, me.user.shortName)
                .append("\n")
                .append(Component.translatable("screen.lnbwarden.confirm.used", me.limit.used, me.limit.max))
        }
        message = MultiLineLabel.create(font, text, width - 50)

        val y = height / 2 + 30
        if (full) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { choose(false) }
                .bounds(width / 2 - 100, y, 200, 20).build())
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.lnbwarden.confirm.link")) { choose(true) }
                .bounds(width / 2 - 154, y, 150, 20).build())
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { choose(false) }
                .bounds(width / 2 + 4, y, 150, 20).build())
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF)
        message.renderCentered(guiGraphics, width / 2, height / 2 - 25)
    }

    override fun tick() {
        if (connection.isConnected) connection.tick() else connection.handleDisconnection()
    }

    override fun onClose() = choose(false)

    private fun choose(accepted: Boolean) {
        if (chosen) return
        chosen = true
        // Disconnected meanwhile: the disconnect screen already replaced this one.
        if (!connection.isConnected) return
        minecraft!!.setScreen(previous)
        onChoice(accepted)
    }
}
