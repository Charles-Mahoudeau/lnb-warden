package fr.bde_teknantes.lnb.warden.client.changelog

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.common.changelog.Changelog
import fr.bde_teknantes.lnb.warden.common.network.ChangelogNoticePayload
import fr.bde_teknantes.lnb.warden.common.network.ChangelogSeenPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.network.PacketDistributor

/** What the client knows about the changelog: the post the server told it about, and a screen waiting to open. */
object ChangelogState {
    /** The newest post the player hasn't opened, until they do. Drives the dot on the pause menu's button. */
    var notice: ChangelogNoticePayload? = null
        private set

    private var openRequested = false

    /** The language to ask LNB for: the game's. */
    val language: String
        get() = Changelog.langOf(Minecraft.getInstance().languageManager.selected)

    /** A post to open as soon as the player is in the world. */
    private var pendingOpen: ChangelogNoticePayload? = null

    /**
     * A new post. The server's notice, sent after joining, opens the post itself; the title screen's check, with no
     * server, only shows a toast, since opening a screen on the player unasked would be too much.
     */
    fun onNotice(payload: ChangelogNoticePayload, open: Boolean = true) {
        notice = payload
        if (open) pendingOpen = payload else Minecraft.getInstance().toasts.addToast(ChangelogToast(payload))
    }

    /** The newest post the site has shown us, from the title screen's check or the list. */
    private var newest: String? = null
    private var lastCheck = 0L

    /** Posts older than this are no news, as on the server. */
    private val FRESH: java.time.Duration = java.time.Duration.ofDays(14)

    /**
     * Without a server nobody sends a notice, so the title screen asks LNB itself, at most once a minute: the newest
     * post is announced when it is recent and isn't the one last opened here.
     */
    fun checkTitle() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 60_000) return
        lastCheck = now
        ChangelogApi.list(language).thenAccept { result ->
            val post = (result as? fr.bde_teknantes.lnb.warden.common.api.ApiResult.Ok)?.body?.posts?.firstOrNull()
            if (post == null) {
                LnbWarden.LOGGER.info("Changelog check: no post ({})", result.describe())
                return@thenAccept
            }
            LnbWarden.LOGGER.info("Changelog check: newest {}, last opened {}", post.id, ClientConfig.LAST_OPENED.get())
            Minecraft.getInstance().execute { onNewest(post, announce = true) }
        }
    }

    /** The list's first post: remembered, so opening it counts as reading the newest. */
    fun onNewest(post: Changelog.Post, announce: Boolean = false) {
        newest = post.id
        if (!announce || notice?.id == post.id || post.id == ClientConfig.LAST_OPENED.get() || post.id == announced) return
        announced = post.id
        val unread = ChangelogNoticePayload(post.id, post.title.take(200), post.summary.orEmpty().take(400), post.versionTag.orEmpty().take(64))
        // The badge stays until it is read; the toast is only for news.
        val published = runCatching { java.time.Instant.parse(post.publishedAt) }.getOrNull()
        val fresh = published != null && java.time.Duration.between(published, java.time.Instant.now()) <= FRESH
        if (fresh) onNotice(unread, open = false) else notice = unread
    }

    private var announced: String? = null

    /** The player is reading the post [id]: remembers it, and tells the server when it is the one they were offered. */
    fun onOpened(id: String) {
        if (id == newest || id == notice?.id) {
            ClientConfig.LAST_OPENED.set(id)
            ClientConfig.SPEC.save()
        }
        if (notice?.id != id) return
        notice = null
        if (Minecraft.getInstance().connection != null) PacketDistributor.sendToServer(ChangelogSeenPayload(id))
    }

    /** Leaving a world keeps the unread post, which is still unread, and drops only a screen that was waiting to open. */
    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        pendingOpen = null
    }

    /** `/changelog`. The chat screen closes right after a command runs, so the screen opens on the next tick. */
    fun requestOpen() {
        openRequested = true
    }

    fun tick() {
        // Left on the title screen, the game still notices a post published meanwhile.
        val mc = Minecraft.getInstance()
        if (mc.level == null && mc.screen is net.minecraft.client.gui.screens.TitleScreen && System.currentTimeMillis() - lastCheck > 120_000) checkTitle()
        val pending = pendingOpen
        if (pending != null) {
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player
            // Once the world is up and nothing else is on screen; a screen the player is using is left alone until it closes.
            if (player != null && minecraft.level != null && minecraft.screen == null && player.tickCount > 40) {
                pendingOpen = null
                val post = Changelog.Post(pending.id, null, pending.title, pending.summary.ifEmpty { null }, pending.versionTag.ifEmpty { null }, null, null, null)
                minecraft.setScreen(ChangelogPostScreen(listOf(post), 0, null, loadList = true))
                val title = Component.literal(pending.title).withStyle(ChatFormatting.BOLD)
                player.sendSystemMessage(
                    Component.translatable("chat.lnbwarden.changelog", title).withStyle {
                        it.withColor(0xA99BFF)
                            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/changelog"))
                            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.lnbwarden.changelog.hover")))
                    },
                )
                minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.2f))
            }
        }
        if (!openRequested) return
        openRequested = false
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen == null) minecraft.setScreen(ChangelogScreen(null))
    }
}
