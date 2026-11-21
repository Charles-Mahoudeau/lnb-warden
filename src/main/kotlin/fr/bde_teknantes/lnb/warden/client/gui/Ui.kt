package fr.bde_teknantes.lnb.warden.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * LNB's own screens in Minecraft's idiom: raised panels and inset wells with square corners and a one-pixel bevel, in
 * the website's night colors. Crimson marks the one main action, and violet marks selection, as on the site.
 */
internal object Ui {
    const val TEXT = 0xFFE9EBF8.toInt()
    const val MUTED = 0xFFA0A6D0.toInt()
    const val DUSK = 0xFFA99BFF.toInt()
    const val RED = 0xFFF25C70.toInt()
    const val BAND = 0xFF1C2150.toInt()
    const val CODE = 0xFFBCC2E2.toInt()
    const val DIVIDER = 0xFF282F66.toInt()

    private const val PANEL = 0xFF13173A.toInt()
    private const val WELL = 0xFF0B0E27.toInt()
    private const val OUTLINE = 0xFF03040D.toInt()
    private const val HIGH = 0xFF3B4496.toInt()
    private const val LOW = 0xFF06081C.toInt()
    private const val WELL_EDGE = 0xFF232A68.toInt()
    private const val ACTION = 0xFFB82238.toInt()
    private const val ACTION_HIGH = 0xFFE0485C.toInt()
    private const val ACTION_LOW = 0xFF7A1020.toInt()
    private const val HOVER = 0xFF282F66.toInt()
    private const val ACTION_HOVER = 0xFFC9293F.toInt()

    private fun bevel(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int, top: Int, bottom: Int) {
        g.fill(x0, y0, x1, y1, OUTLINE)
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, top)
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, top)
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, bottom)
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, bottom)
    }

    /** A raised panel, for a screen's body, a toast or a button. */
    fun raised(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int = PANEL) = bevel(g, x0, y0, x1, y1, fill, HIGH, LOW)

    /** A raised band over a panel, such as the newest post. */
    fun band(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int) = bevel(g, x0, y0, x1, y1, BAND, HIGH, LOW)

    /** A well cut into the panel: code, a badge. */
    fun inset(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int) = bevel(g, x0, y0, x1, y1, WELL, LOW, WELL_EDGE)

    /** The main action's button. */
    fun action(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, hovered: Boolean) =
        bevel(g, x0, y0, x1, y1, if (hovered) ACTION_HOVER else ACTION, ACTION_HIGH, ACTION_LOW)

    /** The ring that marks the selected row, drawn inside the row. */
    fun selection(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int) {
        g.fill(x0, y0, x1, y0 + 1, DUSK)
        g.fill(x0, y1 - 1, x1, y1, DUSK)
        g.fill(x0, y0, x0 + 1, y1, DUSK)
        g.fill(x1 - 1, y0, x1, y1, DUSK)
    }

    /** A small label cut into the panel, such as a version tag. Returns the x after it. */
    fun badge(g: GuiGraphics, x: Int, y: Int, text: String, color: Int = DUSK): Int {
        val font = Minecraft.getInstance().font
        val w = font.width(text) + 8
        inset(g, x, y, x + w, y + 12)
        g.drawString(font, text, x + 4, y + 2, color, false)
        return x + w
    }

    fun hoverFill(hovered: Boolean) = if (hovered) HOVER else BAND

    enum class Look { SOFT, OUTLINE, ACTION }

    /** A button in this look: neutral soft, neutral outline (an inset), or the crimson main action. */
    class LnbButton(builder: Builder, private val look: Look) : Button(builder) {
        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val x1 = x + width
            val y1 = y + height
            when {
                !active -> inset(g, x, y, x1, y1)
                look == Look.ACTION -> action(g, x, y, x1, y1, isHoveredOrFocused)
                look == Look.OUTLINE -> if (isHoveredOrFocused) bevel(g, x, y, x1, y1, HOVER, LOW, WELL_EDGE) else inset(g, x, y, x1, y1)
                else -> bevel(g, x, y, x1, y1, hoverFill(isHoveredOrFocused), HIGH, LOW)
            }
            val font = Minecraft.getInstance().font
            val color = if (active) TEXT else MUTED
            g.drawCenteredString(font, message, x + width / 2, y + (height - font.lineHeight) / 2 + 1, color)
            if (isFocused) selection(g, x + 1, y + 1, x1 - 1, y1 - 1)
        }
    }

    fun button(look: Look, text: Component, x: Int, y: Int, width: Int, height: Int = 20, onPress: () -> Unit): Button =
        LnbButton(Button.builder(text) { onPress() }.bounds(x, y, width, height), look)
}
