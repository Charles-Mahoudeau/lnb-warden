package fr.bde_teknantes.lnb.warden.client.changelog

import com.mojang.brigadier.Command
import fr.bde_teknantes.lnb.warden.client.gui.AccountScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import fr.bde_teknantes.lnb.warden.client.gui.AccountButton
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.minecraft.commands.Commands

/** Where the player reaches the changelog: the pause menu, the title screen and `/changelog`. */
object ChangelogMenus {
    fun onInit(event: ScreenEvent.Init.Post) {
        when (val screen = event.screen) {
            is PauseScreen -> if (screen.showsPauseMenu()) pause(event, screen)
            is TitleScreen -> title(event, screen)
        }
    }

    /**
     * The chat line's click. Vanilla sends a clicked command to the server, which doesn't know `/changelog`, so a click
     * on that line is taken here and opens the screen.
     */
    fun onChatClick(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.screen !is ChatScreen || event.button != 0) return
        val minecraft = Minecraft.getInstance()
        val click = minecraft.gui.chat.getClickedComponentStyleAt(event.mouseX, event.mouseY)?.clickEvent ?: return
        if (click.action != ClickEvent.Action.RUN_COMMAND || click.value != "/changelog") return
        event.isCanceled = true
        minecraft.setScreen(ChangelogScreen(null))
    }

    fun onCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(Commands.literal("changelog").executes {
            ChangelogState.requestOpen()
            Command.SINGLE_SUCCESS
        })
    }

    /** Feedback and Bug report share a row: it becomes Changelog beside Account. Left alone when the row isn't there. */
    private fun pause(event: ScreenEvent.Init.Post, screen: PauseScreen) {
        val widgets = event.listenersList.filterIsInstance<AbstractWidget>()
        val feedback = widgets.firstOrNull { it.key == "menu.sendFeedback" } ?: return
        val bugs = widgets.firstOrNull { it.key == "menu.reportBugs" } ?: return
        event.removeListener(feedback)
        event.removeListener(bugs)
        val half = (feedback.width)
        event.addListener(
            ChangelogButton(
                Button.builder(Component.translatable("screen.lnbwarden.changelog.button")) {
                    Minecraft.getInstance().setScreen(ChangelogScreen(screen))
                }.bounds(feedback.x, feedback.y, half, feedback.height)
            )
        )
        event.addListener(
            Button.builder(Component.translatable("screen.lnbwarden.account.button")) {
                Minecraft.getInstance().setScreen(AccountScreen(screen))
            }.bounds(bugs.x, bugs.y, bugs.width, bugs.height).build()
        )
    }

    /** A small newspaper button right of Account, which is where the title menu leaves room for it. */
    private fun title(event: ScreenEvent.Init.Post, screen: TitleScreen) {
        ChangelogState.checkTitle()
        val account = event.listenersList.filterIsInstance<AbstractWidget>().firstOrNull { it is AccountButton }
        val x = if (account != null) account.x + account.width + 4 else 4
        val y = account?.y ?: 4
        event.addListener(
            NewsButton(
                Button.builder(Component.empty()) {
                    Minecraft.getInstance().setScreen(ChangelogScreen(screen))
                }.bounds(x, y, 20, 20).tooltip(Tooltip.create(Component.translatable("screen.lnbwarden.changelog.button")))
            )
        )
    }

    private val AbstractWidget.key: String?
        get() = (message.contents as? TranslatableContents)?.key
}

/** The pause menu's Changelog button, with a dot while a post is waiting. */
class ChangelogButton(builder: Builder) : Button(builder) {
    override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderWidget(g, mouseX, mouseY, partialTick)
        if (ChangelogState.notice != null) {
            unreadBadge(g, x + width - 12, y + 3)
        }
    }
}

/** The title screen's Changelog button: a newspaper, with a dot while a post is waiting. */
class NewsButton(builder: Builder) : Button(builder) {
    override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderWidget(g, mouseX, mouseY, partialTick)
        val x0 = x + 4
        val y0 = y + 5
        val paper = 0xFFE8E8E8.toInt()
        val ink = 0xFF404040.toInt()
        g.fill(x0, y0, x0 + 12, y0 + 10, paper)
        g.fill(x0 + 1, y0 + 1, x0 + 5, y0 + 4, ink)
        g.fill(x0 + 6, y0 + 1, x0 + 11, y0 + 2, ink)
        g.fill(x0 + 6, y0 + 3, x0 + 11, y0 + 4, ink)
        g.fill(x0 + 1, y0 + 5, x0 + 11, y0 + 6, ink)
        g.fill(x0 + 1, y0 + 7, x0 + 11, y0 + 8, ink)
        if (ChangelogState.notice != null) unreadBadge(g, x + width - 7, y - 2)
    }
}

/** A red badge with a dark edge, over a button's corner, for a post that hasn't been read. */
private fun unreadBadge(g: GuiGraphics, x: Int, y: Int) {
    g.fill(x, y, x + 9, y + 9, 0xFF2A0A10.toInt())
    g.fill(x + 1, y + 1, x + 8, y + 8, 0xFFE0485C.toInt())
    g.fill(x + 2, y + 2, x + 7, y + 7, 0xFFF25C70.toInt())
}
