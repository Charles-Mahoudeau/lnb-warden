package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.client.gui.Ui
import fr.bde_teknantes.lnb.warden.common.changelog.BlockLayout
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

/** A post's body, wrapped to a width and ready to draw at any scroll position. */
internal class PostBody(private val flow: List<BlockLayout.Flow>, private val images: ChangelogImages) {
    private class Line(
        val text: FormattedCharSequence?,
        val x: Int,
        val y: Int,
        val color: Int,
        val well: Boolean = false,
        val bar: Boolean = false,
        val rule: Boolean = false,
        val image: BlockLayout.Flow.Image? = null,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
    )

    private var lines = emptyList<Line>()
    private var width = 0
    private var lineHeight = 10

    /** The height of the whole body, once laid out. */
    var height = 0
        private set

    fun layout(font: Font, width: Int) {
        this.width = width
        lineHeight = font.lineHeight + 1
        val out = ArrayList<Line>()
        var y = 0
        for (item in flow) {
            when (item) {
                BlockLayout.Flow.Rule -> {
                    y += 4
                    out += Line(null, 0, y, Ui.DIVIDER, rule = true)
                    y += 5
                }
                is BlockLayout.Flow.Image -> {
                    // The size is known before the picture arrives, so nothing moves when it does. Never enlarged.
                    val scale = minOf(1f, width.coerceAtLeast(40).toFloat() / item.width, MAX_IMAGE_HEIGHT.toFloat() / item.height)
                    val w = maxOf(1, (item.width * scale).toInt())
                    val h = maxOf(1, (item.height * scale).toInt())
                    out += Line(null, 0, y, Ui.DIVIDER, image = item, imageWidth = w, imageHeight = h)
                    y += h + 6
                }
                is BlockLayout.Flow.Text -> {
                    if (item.kind == BlockLayout.Kind.HEADING && y > 0) y += 5
                    val well = item.kind == BlockLayout.Kind.CODE || item.kind == BlockLayout.Kind.TABLE_HEADER ||
                        item.kind == BlockLayout.Kind.TABLE_ROW
                    val pad = if (well) 3 else 0
                    val quote = if (item.kind == BlockLayout.Kind.QUOTE) 6 else 0
                    val x = item.indent * 10 + quote + pad
                    val marker = item.marker
                    val markerWidth = marker?.let { font.width(it) + 4 } ?: 0
                    val wrapped = font.split(component(item), (width - x - markerWidth - pad).coerceAtLeast(40))
                    val color = colorOf(item.kind)
                    if (marker != null) out += Line(FormattedCharSequence.forward(marker, Style.EMPTY), x, y, Ui.MUTED)
                    wrapped.forEachIndexed { i, seq ->
                        out += Line(seq, x + markerWidth, y + i * lineHeight, color, well = well, bar = quote > 0)
                    }
                    y += maxOf(wrapped.size, 1) * lineHeight + if (item.kind == BlockLayout.Kind.HEADING) 1 else 4
                }
            }
        }
        lines = out
        height = y
    }

    private fun colorOf(kind: BlockLayout.Kind) = when (kind) {
        BlockLayout.Kind.QUOTE, BlockLayout.Kind.TABLE_HEADER -> Ui.MUTED
        BlockLayout.Kind.CODE -> Ui.CODE
        else -> Ui.TEXT
    }

    private fun component(item: BlockLayout.Flow.Text): Component {
        val out: MutableComponent = Component.empty()
        val bold = item.kind == BlockLayout.Kind.HEADING || item.kind == BlockLayout.Kind.TABLE_HEADER
        for (run in item.runs) {
            var style = Style.EMPTY.withBold(bold || run.bold).withItalic(run.italic)
            if (run.code) style = style.withColor(Ui.CODE and 0xFFFFFF)
            val link = run.link
            if (link != null && isWebLink(link)) {
                style = style.withColor(Ui.RED and 0xFFFFFF).withUnderlined(true).withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, link))
            }
            out.append(Component.literal(run.text).withStyle(style))
        }
        return out
    }

    fun render(g: GuiGraphics, font: Font, left: Int, top: Int, scroll: Int, viewTop: Int, viewBottom: Int) {
        for (line in lines) {
            val y = top + line.y - scroll
            if (y + maxOf(lineHeight, line.imageHeight) < viewTop || y > viewBottom) continue
            val x = left + line.x
            line.image?.let { drawImage(g, font, it, x, y, line.imageWidth, line.imageHeight) }
            if (line.image != null) continue
            if (line.rule) {
                g.fill(left, y, left + width, y + 1, line.color)
                continue
            }
            if (line.well) g.fill(left, y, left + width, y + lineHeight, 0xFF0B0E27.toInt())
            if (line.bar) g.fill(left, y, left + 2, y + lineHeight, Ui.DIVIDER)
            g.drawString(font, line.text ?: continue, x, y + 1, line.color, false)
        }
    }

    private fun drawImage(g: GuiGraphics, font: Font, image: BlockLayout.Flow.Image, x: Int, y: Int, w: Int, h: Int) {
        val state = images.get(image.url)
        if (state is ChangelogImages.State.Ready) {
            g.blit(state.location, x, y, w, h, 0f, 0f, state.width, state.height, state.width, state.height)
            return
        }
        // Loading, or failed: the alt text in a well of the picture's size.
        Ui.inset(g, x, y, x + w, y + h)
        val label = image.alt.ifBlank { "…" }
        g.drawCenteredString(font, font.plainSubstrByWidth(label, w - 8), x + w / 2, y + (h - font.lineHeight) / 2, Ui.MUTED)
    }

    /** The web link under the mouse, if any. Coordinates are those [render] was given. */
    fun linkAt(font: Font, left: Int, top: Int, scroll: Int, mouseX: Double, mouseY: Double): String? {
        val line = lines.firstOrNull { it.text != null && mouseY >= top + it.y - scroll && mouseY < top + it.y - scroll + lineHeight } ?: return null
        val style = font.splitter.componentStyleAtWidth(line.text!!, (mouseX - left - line.x).toInt()) ?: return null
        val click = style.clickEvent ?: return null
        return if (click.action == ClickEvent.Action.OPEN_URL) click.value else null
    }

    companion object {
        private const val MAX_IMAGE_HEIGHT = 180

        fun isWebLink(link: String) = link.startsWith("https://") || link.startsWith("http://")
    }
}
