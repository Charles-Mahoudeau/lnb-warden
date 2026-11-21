package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.client.gui.Ui
import fr.bde_teknantes.lnb.warden.common.network.ChangelogNoticePayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.network.chat.Component

/** The notice of a new post, shown after joining. It has no click: the pause menu's button and `/changelog` open it. */
class ChangelogToast(private val notice: ChangelogNoticePayload) : Toast {
    override fun width() = 200

    override fun height() = 48

    override fun render(g: GuiGraphics, toasts: ToastComponent, timeSinceLastVisible: Long): Toast.Visibility {
        val font = toasts.minecraft.font
        Ui.raised(g, 0, 0, width(), height())
        val inner = width() - 14
        var y = 7
        val label = Component.translatable("toast.lnbwarden.changelog")
        val next = if (notice.versionTag.isNotEmpty()) Ui.badge(g, 7, y - 2, notice.versionTag) + 5 else 7
        g.drawString(font, label, next, y, Ui.DUSK, false)
        y += 15
        g.drawString(font, font.plainSubstrByWidth(notice.title, inner), 7, y, Ui.TEXT, false)
        y += 11
        if (notice.summary.isNotEmpty()) g.drawString(font, font.plainSubstrByWidth(notice.summary, inner), 7, y, Ui.MUTED, false)
        return if (timeSinceLastVisible < DISPLAY_MS) Toast.Visibility.SHOW else Toast.Visibility.HIDE
    }

    private companion object {
        const val DISPLAY_MS = 9000L
    }
}
