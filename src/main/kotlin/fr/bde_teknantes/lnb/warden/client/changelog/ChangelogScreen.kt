package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.client.gui.Ui
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.changelog.Changelog
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

/**
 * LNB's changelog: the newest post as a band with the main action, older posts as dated rows. Posts come from LNB's
 * public routes, so it opens from the title screen too. [parent] is where Done goes back to.
 */
class ChangelogScreen(private val parent: Screen?) : Screen(Component.translatable("screen.lnbwarden.changelog.title")) {
    private enum class Phase { LOADING, READY, FAILED, DISABLED }

    private class Entry(
        val post: Changelog.Post,
        val latest: Boolean,
        val title: List<FormattedCharSequence>,
        val summary: List<FormattedCharSequence>,
        val height: Int,
    ) {
        var top = 0
    }

    private val posts = ArrayList<Changelog.Post>()
    private var next: String? = null
    private var phase = Phase.LOADING
    private var moreLoading = false
    private var requested = false
    private var closed = false

    private var dateColumn = 44
    private var tagColumn = 0
    private var entries = emptyList<Entry>()
    private var contentHeight = 0
    private var scroll = 0
    private var selected = 0

    private var left = 0
    private var right = 0
    private var top = 0
    private var bottom = 0
    private var viewTop = 0
    private var viewBottom = 0

    override fun init() {
        val panelWidth = minOf(width - 24, 360)
        left = (width - panelWidth) / 2
        right = left + panelWidth
        top = 22
        bottom = height - 34
        viewTop = top + 26
        viewBottom = bottom - 4

        addRenderableWidget(Ui.button(Ui.Look.SOFT, CommonComponents.GUI_DONE, right - 80, height - 26, 80) { onClose() })
        if (phase == Phase.READY && next != null) {
            addRenderableWidget(
                Ui.button(Ui.Look.OUTLINE, Component.translatable("screen.lnbwarden.changelog.load_more"), left, height - 26, 110) {
                    fetch(next)
                }.also { it.active = !moreLoading }
            )
        }
        if (phase == Phase.FAILED) {
            addRenderableWidget(
                Ui.button(Ui.Look.SOFT, Component.translatable("screen.lnbwarden.changelog.retry"), width / 2 - 45, viewTop + 52, 90) {
                    phase = Phase.LOADING
                    fetch(null)
                    rebuildWidgets()
                }
            )
        }

        layoutEntries()
        if (!requested) {
            requested = true
            fetch(null)
        }
    }

    private fun fetch(before: String?) {
        if (before != null) {
            moreLoading = true
            rebuildWidgets()
        }
        ChangelogApi.list(ChangelogState.language, before).thenAccept { result ->
            Minecraft.getInstance().execute { if (!closed) onResult(before, result) }
        }
    }

    private fun onResult(before: String?, result: ApiResult<Changelog.PostList>) {
        moreLoading = false
        when {
            result is ApiResult.Ok -> {
                if (before == null) posts.clear()
                posts += result.body.posts.orEmpty()
                next = result.body.next
                if (before == null) posts.firstOrNull()?.let { ChangelogState.onNewest(it) }
                phase = Phase.READY
            }
            result.isDisabled -> phase = Phase.DISABLED
            else -> {
                // A failed second page keeps what is already shown.
                if (before == null) phase = Phase.FAILED
            }
        }
        rebuildWidgets()
    }

    private fun layoutEntries() {
        val inner = right - left - 16 - 6
        var y = 0
        // The older rows keep their date in a column as wide as the widest month, and the tag on the right.
        val older = posts.drop(1)
        dateColumn = 12 + (older.maxOfOrNull { font.width(ChangelogFormat.dayAndMonth(it.publishedAt).second) } ?: 0)
        tagColumn = older.maxOfOrNull { p -> p.versionTag?.takeIf { it.isNotBlank() }?.let { font.width(it) + 8 + 10 } ?: 0 } ?: 0
        entries = posts.mapIndexed { index, post ->
            val latest = index == 0
            val textWidth = if (latest) inner - 16 else inner - dateColumn - tagColumn - 6
            val title = font.split(Component.literal(post.title).withStyle(ChatFormatting.BOLD), textWidth)
            val summary = post.summary?.takeIf { it.isNotBlank() }?.let { font.split(Component.literal(it), textWidth) }.orEmpty()
                .let { if (latest) it else it.take(2) }
            val height = if (latest) 8 + 20 + title.size * LINE + 3 + summary.size * LINE + 6 + 20 + 8
            else 6 + maxOf(title.size * LINE + summary.size * LINE, 22) + 6
            Entry(post, latest, title, summary, height).also {
                it.top = y
                y += height + if (latest) 8 else 0
            }
        }
        contentHeight = y
        scroll = scroll.coerceIn(0, maxScroll())
    }

    private fun maxScroll() = maxOf(0, contentHeight - (viewBottom - viewTop))

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(g, mouseX, mouseY, partialTick)
        Ui.raised(g, left, top, right, bottom)
        g.drawString(font, title, left + 8, top + 8, Ui.TEXT, false)
        g.fill(left + 1, top + 22, right - 1, top + 23, Ui.DIVIDER)

        when (phase) {
            Phase.LOADING -> centered(g, Component.translatable("screen.lnbwarden.changelog.loading", LoadingDotsText.get(Util.getMillis())), viewTop + 14, Ui.MUTED)
            Phase.FAILED -> {
                centered(g, Component.translatable("screen.lnbwarden.changelog.failed"), viewTop + 14, Ui.TEXT)
                centered(g, Component.translatable("screen.lnbwarden.changelog.failed.hint"), viewTop + 28, Ui.MUTED)
            }
            Phase.DISABLED -> centered(g, Component.translatable("screen.lnbwarden.changelog.disabled"), viewTop + 14, Ui.MUTED)
            Phase.READY -> if (posts.isEmpty()) centered(g, Component.translatable("screen.lnbwarden.changelog.empty"), viewTop + 14, Ui.MUTED)
            else renderEntries(g, mouseX, mouseY)
        }
    }

    private fun centered(g: GuiGraphics, text: Component, y: Int, color: Int) {
        g.drawCenteredString(font, text, (left + right) / 2, y, color)
    }

    private fun renderEntries(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        g.enableScissor(left + 1, viewTop, right - 1, viewBottom)
        val x0 = left + 8
        val x1 = right - 14
        entries.forEachIndexed { index, entry ->
            val y = viewTop + entry.top - scroll
            if (y + entry.height < viewTop || y > viewBottom) return@forEachIndexed
            val hovered = mouseX in x0 until x1 && mouseY >= maxOf(y, viewTop) && mouseY < minOf(y + entry.height, viewBottom)
            if (entry.latest) renderLatest(g, entry, x0, x1, y, hovered) else renderRow(g, entry, index, x0, x1, y, hovered)
            if (index == selected) Ui.selection(g, x0, y, x1, y + entry.height)
        }
        g.disableScissor()
        if (maxScroll() > 0) {
            val track = viewBottom - viewTop
            val thumb = maxOf(16, track * track / contentHeight)
            val thumbY = viewTop + (track - thumb) * scroll / maxScroll()
            g.fill(right - 9, viewTop, right - 5, viewBottom, 0xFF0B0E27.toInt())
            g.fill(right - 9, thumbY, right - 5, thumbY + thumb, Ui.DUSK)
        }
    }

    private fun renderLatest(g: GuiGraphics, entry: Entry, x0: Int, x1: Int, y: Int, hovered: Boolean) {
        Ui.band(g, x0, y, x1, y + entry.height)
        var x = x0 + 8
        var ty = y + 8
        entry.post.versionTag?.takeIf { it.isNotBlank() }?.let { x = Ui.badge(g, x, ty - 2, it) + 6 }
        g.drawString(font, ChangelogFormat.date(entry.post.publishedAt), x, ty, Ui.MUTED, false)
        ty += 20
        entry.title.forEach { g.drawString(font, it, x0 + 8, ty, Ui.TEXT, false); ty += LINE }
        ty += 3
        entry.summary.forEach { g.drawString(font, it, x0 + 8, ty, Ui.MUTED, false); ty += LINE }
        ty += 6
        val label = Component.translatable("screen.lnbwarden.changelog.read")
        val w = font.width(label) + 24
        Ui.action(g, x0 + 8, ty, x0 + 8 + w, ty + 20, hovered)
        g.drawCenteredString(font, label, x0 + 8 + w / 2, ty + 6, Ui.TEXT)
    }

    private fun renderRow(g: GuiGraphics, entry: Entry, index: Int, x0: Int, x1: Int, y: Int, hovered: Boolean) {
        if (hovered) g.fill(x0, y, x1, y + entry.height, 0xFF1C2150.toInt())
        if (index > 1) g.fill(x0, y, x1, y + 1, Ui.DIVIDER)
        val (day, month) = ChangelogFormat.dayAndMonth(entry.post.publishedAt)
        val pose = g.pose()
        pose.pushPose()
        pose.translate((x0 + 6).toFloat(), (y + 7).toFloat(), 0f)
        pose.scale(1.5f, 1.5f, 1f)
        g.drawString(font, day, 0, 0, Ui.DUSK, false)
        pose.popPose()
        g.drawString(font, month, x0 + 6, y + 22, Ui.MUTED, false)
        val tx = x0 + dateColumn
        var ty = y + 6
        entry.title.forEach { g.drawString(font, it, tx, ty, Ui.TEXT, false); ty += LINE }
        entry.summary.forEach { g.drawString(font, it, tx, ty, Ui.MUTED, false); ty += LINE }
        entry.post.versionTag?.takeIf { it.isNotBlank() }?.let { tag ->
            val w = font.width(tag) + 8
            Ui.badge(g, x1 - w - 4, y + 5, tag, Ui.MUTED)
        }
    }

    private fun entryAt(mouseX: Double, mouseY: Double): Int {
        if (phase != Phase.READY || mouseX < left + 8 || mouseX >= right - 14 || mouseY < viewTop || mouseY >= viewBottom) return -1
        return entries.indexOfFirst { mouseY >= viewTop + it.top - scroll && mouseY < viewTop + it.top - scroll + it.height }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        val index = entryAt(mouseX, mouseY)
        if (button != 0 || index < 0) return false
        selected = index
        open(index)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (phase != Phase.READY) return false
        scroll = (scroll - (scrollY * 14).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (phase == Phase.READY && entries.isNotEmpty()) {
            when (keyCode) {
                GLFW.GLFW_KEY_DOWN -> { select(selected + 1); return true }
                GLFW.GLFW_KEY_UP -> { select(selected - 1); return true }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> if (focused == null) { open(selected); return true }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun select(index: Int) {
        selected = index.coerceIn(0, entries.size - 1)
        val entry = entries[selected]
        if (viewTop + entry.top - scroll < viewTop) scroll = entry.top
        else if (viewTop + entry.top + entry.height - scroll > viewBottom) scroll = entry.top + entry.height - (viewBottom - viewTop)
        scroll = scroll.coerceIn(0, maxScroll())
    }

    private fun open(index: Int) {
        minecraft!!.setScreen(ChangelogPostScreen(posts.toList(), index, this))
    }

    override fun removed() {
        closed = true
    }

    override fun onClose() {
        minecraft!!.setScreen(parent)
    }

    private companion object {
        const val LINE = 10
    }
}
