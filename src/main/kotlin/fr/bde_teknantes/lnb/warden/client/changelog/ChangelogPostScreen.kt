package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.client.gui.Ui
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.changelog.BlockLayout
import fr.bde_teknantes.lnb.warden.common.changelog.Changelog
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

/**
 * One post, read from its blocks. [posts] is the list it was opened from and [index] its place in it, for Older and
 * Newer. Opening a post is what tells the server the player has seen it.
 */
class ChangelogPostScreen(
    initialPosts: List<Changelog.Post>,
    initialIndex: Int,
    private val parent: Screen?,
    /** Opened by itself, knowing only the post: the list is fetched so Older and Newer work. */
    private val loadList: Boolean = false,
) : Screen(Component.literal(initialPosts[initialIndex].title)) {
    private var posts = initialPosts
    private var index = initialIndex

    private enum class Phase { LOADING, READY, FAILED }

    private val preview = initialPosts[initialIndex]
    private var detail: Changelog.Post? = null
    private var body: PostBody? = null
    private var phase = Phase.LOADING
    private var requested = false
    private var closed = false

    private var titleLines = emptyList<FormattedCharSequence>()
    private var scroll = 0

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
        titleLines = font.split(Component.literal(preview.title).withStyle(ChatFormatting.BOLD), panelWidth - 16)
        viewTop = top + 8 + 20 + titleLines.size * LINE + 4 + (if (otherLanguage() != null) LINE else 0) + 6
        viewBottom = bottom - 4

        addRenderableWidget(Ui.button(Ui.Look.OUTLINE, Component.translatable("screen.lnbwarden.changelog.older"), left, height - 26, 60) {
            go(index + 1)
        }.also { it.active = index + 1 < posts.size })
        addRenderableWidget(Ui.button(Ui.Look.OUTLINE, Component.translatable("screen.lnbwarden.changelog.newer"), left + 64, height - 26, 60) {
            go(index - 1)
        }.also { it.active = index > 0 })
        addRenderableWidget(Ui.button(Ui.Look.SOFT, CommonComponents.GUI_DONE, right - 80, height - 26, 80) { onClose() })

        if (phase == Phase.FAILED) {
            addRenderableWidget(
                Ui.button(Ui.Look.SOFT, Component.translatable("screen.lnbwarden.changelog.retry"), width / 2 - 100, viewTop + 40, 96) {
                    phase = Phase.LOADING
                    fetch()
                    rebuildWidgets()
                }
            )
            preview.url?.takeIf { PostBody.isWebLink(it) }?.let { url ->
                addRenderableWidget(
                    Ui.button(Ui.Look.OUTLINE, Component.translatable("screen.lnbwarden.changelog.website"), width / 2 + 4, viewTop + 40, 96) {
                        ConfirmLinkScreen.confirmLinkNow(this, url)
                    }
                )
            }
        }

        body?.layout(font, right - left - 16 - 6)
        scroll = scroll.coerceIn(0, maxScroll())
        if (!requested) {
            requested = true
            fetch()
            if (loadList) fetchList()
        }
    }

    private fun fetchList() {
        ChangelogApi.list(ChangelogState.language).thenAccept { result ->
            Minecraft.getInstance().execute {
                if (closed || result !is ApiResult.Ok) return@execute
                val list = result.body.posts.orEmpty()
                val at = list.indexOfFirst { it.id == preview.id }
                if (at < 0) return@execute
                posts = list
                index = at
                rebuildWidgets()
            }
        }
    }

    private fun fetch() {
        ChangelogApi.post(ChangelogState.language, preview.id).thenAccept { result ->
            Minecraft.getInstance().execute { if (!closed) onResult(result) }
        }
    }

    private fun onResult(result: ApiResult<Changelog.Post>) {
        if (result is ApiResult.Ok) {
            detail = result.body
            // A site without blocks still answers with the Markdown, which reads fine as plain text.
            val flow = result.body.blocks?.let { BlockLayout.flow(it) }
                ?: listOf(BlockLayout.Flow.Text(BlockLayout.Kind.PARAGRAPH, listOf(BlockLayout.Run(result.body.body.orEmpty()))))
            body = PostBody(flow)
            phase = Phase.READY
            ChangelogState.onOpened(preview.id)
        } else {
            phase = Phase.FAILED
        }
        rebuildWidgets()
    }

    /** The language the text is in, when it isn't the one the player asked for. */
    private fun otherLanguage(): Component? {
        val lang = detail?.lang ?: return null
        if (lang == ChangelogState.language) return null
        return Component.translatable("screen.lnbwarden.changelog.only_in", Component.translatable("screen.lnbwarden.changelog.lang.$lang"))
    }

    private fun maxScroll() = maxOf(0, (body?.height ?: 0) - (viewBottom - viewTop))

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(g, mouseX, mouseY, partialTick)
        Ui.raised(g, left, top, right, bottom)

        var y = top + 8
        var x = left + 8
        preview.versionTag?.takeIf { it.isNotBlank() }?.let { x = Ui.badge(g, x, y - 2, it) + 6 }
        g.drawString(font, ChangelogFormat.date((detail ?: preview).publishedAt), x, y, Ui.MUTED, false)
        y += 20
        titleLines.forEach { g.drawString(font, it, left + 8, y, Ui.TEXT, false); y += LINE }
        otherLanguage()?.let { g.drawString(font, it, left + 8, y + 2, Ui.MUTED, false); y += LINE }
        g.fill(left + 1, viewTop - 3, right - 1, viewTop - 2, Ui.DIVIDER)

        when (phase) {
            Phase.LOADING -> g.drawCenteredString(
                font, Component.translatable("screen.lnbwarden.changelog.loading", LoadingDotsText.get(Util.getMillis())),
                (left + right) / 2, viewTop + 14, Ui.MUTED,
            )
            Phase.FAILED -> {
                g.drawCenteredString(font, Component.translatable("screen.lnbwarden.changelog.failed"), (left + right) / 2, viewTop + 10, Ui.TEXT)
                g.drawCenteredString(font, Component.translatable("screen.lnbwarden.changelog.failed.hint"), (left + right) / 2, viewTop + 24, Ui.MUTED)
            }
            Phase.READY -> renderBody(g)
        }
    }

    private fun renderBody(g: GuiGraphics) {
        val body = body ?: return
        g.enableScissor(left + 1, viewTop, right - 1, viewBottom)
        body.render(g, font, left + 8, viewTop, scroll, viewTop, viewBottom)
        g.disableScissor()
        if (maxScroll() > 0) {
            val track = viewBottom - viewTop
            val thumb = maxOf(16, track * track / body.height)
            val thumbY = viewTop + (track - thumb) * scroll / maxScroll()
            g.fill(right - 9, viewTop, right - 5, viewBottom, 0xFF0B0E27.toInt())
            g.fill(right - 9, thumbY, right - 5, thumbY + thumb, Ui.DUSK)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        val body = body ?: return false
        if (button != 0 || phase != Phase.READY || mouseY < viewTop || mouseY >= viewBottom) return false
        val link = body.linkAt(font, left + 8, viewTop, scroll, mouseX, mouseY) ?: return false
        ConfirmLinkScreen.confirmLinkNow(this, link)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - (scrollY * 14).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val page = viewBottom - viewTop - 10
        when (keyCode) {
            GLFW.GLFW_KEY_DOWN -> scroll = (scroll + 14).coerceIn(0, maxScroll())
            GLFW.GLFW_KEY_UP -> scroll = (scroll - 14).coerceIn(0, maxScroll())
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll = (scroll + page).coerceIn(0, maxScroll())
            GLFW.GLFW_KEY_PAGE_UP -> scroll = (scroll - page).coerceIn(0, maxScroll())
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun go(target: Int) {
        if (target in posts.indices) minecraft!!.setScreen(ChangelogPostScreen(posts, target, parent))
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
