package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.client.auth.AuthState
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.CommonComponents

/**
 * The title screen's account button. A badge on its right shows the link's state, by shape as well as by color,
 * and its tooltip gives the details.
 */
class AccountButton(builder: Builder) : Button(builder) {
    /** The state the tooltip was built for. */
    private var shownState: AuthState? = null

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = ClientAuth.state
        if (state != shownState) {
            shownState = state
            tooltip = Tooltip.create(CommonComponents.joinLines(AccountScreen.statusLines(state)))
        }
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)

        val badge = LinkBadge.of(state)
        val font = Minecraft.getInstance().font
        guiGraphics.drawString(
            font,
            badge.glyph,
            x + width - 6 - font.width(badge.glyph),
            y + (height - font.lineHeight) / 2 + 1,
            if (active) badge.color else 0xFFA0A0A0.toInt(),
        )
    }
}

/** The link's state at a glance, shared by the title screen's button and the account screen. */
internal enum class LinkBadge(val glyph: String, val color: Int) {
    LINKED("✔", 0xFF55FF55.toInt()),
    NOT_LINKED("✖", 0xFFFF5555.toInt()),
    ISSUE("⚠", 0xFFFFAA00.toInt()),
    CHECKING("•", 0xFFAAAAAA.toInt());

    companion object {
        fun of(state: AuthState): LinkBadge = when (state) {
            is AuthState.Linked -> if (state.me.activeBan == null) LINKED else ISSUE
            AuthState.NotLinked, AuthState.TokenRejected -> NOT_LINKED
            is AuthState.Unreachable, is AuthState.Refused -> ISSUE
            AuthState.Checking -> CHECKING
        }
    }
}
