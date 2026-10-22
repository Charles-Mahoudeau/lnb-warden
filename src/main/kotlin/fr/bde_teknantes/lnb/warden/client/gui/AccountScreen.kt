package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import fr.bde_teknantes.lnb.warden.client.auth.AuthState
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/**
 * The LNB account this device is linked to, from the title screen: a profile card with the action that fits the
 * link's state, then the linked Minecraft accounts.
 */
class AccountScreen(private val parent: Screen) : Screen(Component.translatable("screen.lnbwarden.account.title")) {
    /** The state the widgets were built for: they change with it. */
    private var shownState: AuthState? = null

    private var left = 0
    private var right = 0

    override fun init() {
        val state = ClientAuth.state
        shownState = state
        val contentWidth = minOf(width - 40, 360)
        left = (width - contentWidth) / 2
        right = left + contentWidth

        if (ClientAuth.session != null) {
            val signOut = Component.translatable("screen.lnbwarden.status.sign_out")
            val w = font.width(signOut) + 16
            addRenderableWidget(Button.builder(signOut) { ClientAuth.signOut() }.bounds(right - w, 6, w, 16).build())
        }

        val action = when (state) {
            AuthState.NotLinked, AuthState.TokenRejected ->
                Button.builder(Component.translatable("screen.lnbwarden.status.link")) {
                    minecraft!!.setScreen(LinkScreen(this))
                }
            is AuthState.Unreachable ->
                Button.builder(Component.translatable("screen.lnbwarden.status.retry")) { ClientAuth.retry() }
            else -> null
        }
        action?.let { addRenderableWidget(it.bounds(right - 6 - 70, CARD_TOP + 12, 70, 20).build()) }

        if (state is AuthState.Linked && state.me.accounts.isNotEmpty()) {
            val list = AccountList(minecraft!!, contentWidth, height - 36 - LIST_TOP, LIST_TOP)
            list.x = left
            state.me.accounts.forEach { list.add(AccountEntry(it)) }
            addRenderableWidget(list)
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.lnbwarden.account.website").append(" ↗")) {
            Util.getPlatform().openUri(ClientConfig.apiUrl + "/minecraft/warden")
        }.bounds(left, height - 28, contentWidth - 104, 20).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }
            .bounds(right - 100, height - 28, 100, 20).build())
    }

    override fun tick() {
        if (ClientAuth.state != shownState) rebuildWidgets()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        val state = ClientAuth.state
        renderCard(guiGraphics, state)

        if (state is AuthState.Linked) {
            val me = state.me
            val y = LIST_TOP - 12
            guiGraphics.drawString(font, Component.translatable("screen.lnbwarden.account.accounts"), left, y, GRAY)
            val count = Component.translatable("screen.lnbwarden.account.count", me.limit.used, me.limit.max)
            guiGraphics.drawString(font, count, right - font.width(count), y, GRAY)
            if (me.accounts.isEmpty()) {
                guiGraphics.drawWordWrap(
                    font, Component.translatable("screen.lnbwarden.account.no_accounts"),
                    left + 6, LIST_TOP + 8, right - left - 12, DARK_GRAY,
                )
            }
        }
    }

    private fun renderCard(guiGraphics: GuiGraphics, state: AuthState) {
        panel(guiGraphics, left, CARD_TOP, right, CARD_TOP + CARD_HEIGHT, 0x73000000)
        val avatarX = left + 6
        val avatarY = CARD_TOP + 6
        val textX = avatarX + 40
        val badge = LinkBadge.of(state)
        val sessionName = ClientAuth.session?.name

        when (state) {
            is AuthState.Linked -> {
                val me = state.me
                drawInitials(guiGraphics, me.user.name, me.user.id, avatarX, avatarY)
                val ban = me.activeBan
                titleLine(guiGraphics, textX, me.user.name, badge,
                    if (ban == null) "screen.lnbwarden.account.badge.linked" else "screen.lnbwarden.account.badge.banned")
                val second = when {
                    ban != null -> banLine(ban)
                    me.user.showRealName -> Component.translatable("screen.lnbwarden.account.shown_as", me.user.shortName)
                        .withStyle(ChatFormatting.GRAY)
                    else -> Component.translatable("screen.lnbwarden.account.name_hidden").withStyle(ChatFormatting.GRAY)
                }
                guiGraphics.drawString(font, second, textX, CARD_TOP + 18, GRAY)
                guiGraphics.drawString(
                    font,
                    Component.translatable("screen.lnbwarden.account.device", me.device.name, formatDate(me.device.expiresAt)),
                    textX, CARD_TOP + 30, DARK_GRAY,
                )
            }
            AuthState.NotLinked, AuthState.TokenRejected -> {
                drawUnknown(guiGraphics, avatarX, avatarY, "?")
                titleLine(guiGraphics, textX, null, badge, "screen.lnbwarden.account.badge.not_linked")
                val hint = if (state == AuthState.TokenRejected)
                    Component.translatable("screen.lnbwarden.status.token_rejected").withStyle(ChatFormatting.RED)
                else Component.translatable("screen.lnbwarden.account.link_hint").withStyle(ChatFormatting.GRAY)
                guiGraphics.drawWordWrap(font, hint, textX, CARD_TOP + 20, right - 82 - textX, GRAY)
            }
            is AuthState.Unreachable -> {
                drawSessionAvatar(guiGraphics, avatarX, avatarY)
                issueLines(guiGraphics, textX, sessionName, badge, "screen.lnbwarden.account.badge.unreachable")
                val seconds = maxOf(0L, (state.retryAt - Util.getMillis() + 999) / 1000)
                guiGraphics.drawString(
                    font, Component.translatable("screen.lnbwarden.account.retrying", seconds), textX, CARD_TOP + 30, DARK_GRAY,
                )
            }
            is AuthState.Refused -> {
                drawSessionAvatar(guiGraphics, avatarX, avatarY)
                issueLines(guiGraphics, textX, sessionName, badge, "screen.lnbwarden.account.badge.refused")
                guiGraphics.drawString(
                    font,
                    Component.translatable("screen.lnbwarden.status.refused", state.problem ?: "?").withStyle(ChatFormatting.RED),
                    textX, CARD_TOP + 30, GRAY,
                )
            }
            AuthState.Checking -> {
                drawUnknown(guiGraphics, avatarX, avatarY, "")
                guiGraphics.drawString(font, statusLines(state).first(), textX, CARD_TOP + 8, GRAY)
            }
        }
    }

    /** The account's name, if known, followed by the badge and its label. */
    private fun titleLine(guiGraphics: GuiGraphics, x: Int, name: String?, badge: LinkBadge, labelKey: String) {
        var cursor = x
        if (name != null) cursor = guiGraphics.drawString(font, name, cursor, CARD_TOP + 6, 0xFFFFFF) + 6
        val label = Component.literal(badge.glyph + " ").append(Component.translatable(labelKey))
        guiGraphics.drawString(font, label, cursor, CARD_TOP + 6, badge.color)
    }

    /** For a problem with LNB rather than with the account: the name, then the badge and its label below it. */
    private fun issueLines(guiGraphics: GuiGraphics, x: Int, name: String?, badge: LinkBadge, labelKey: String) {
        if (name != null) guiGraphics.drawString(font, name, x, CARD_TOP + 6, 0xFFFFFF)
        val label = Component.literal(badge.glyph + " ").append(Component.translatable(labelKey))
        guiGraphics.drawString(font, label, x, if (name != null) CARD_TOP + 18 else CARD_TOP + 6, badge.color)
    }

    private fun drawSessionAvatar(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val session = ClientAuth.session
        if (session == null) drawUnknown(guiGraphics, x, y, "?")
        else drawInitials(guiGraphics, session.name, session.userId, x, y)
    }

    private fun drawInitials(guiGraphics: GuiGraphics, name: String, userId: String, x: Int, y: Int) {
        drawAvatar(guiGraphics, x, y, AVATAR_COLORS[Math.floorMod(userId.hashCode(), AVATAR_COLORS.size)], initials(name), 0xFFFFFF)
    }

    private fun drawUnknown(guiGraphics: GuiGraphics, x: Int, y: Int, text: String) {
        drawAvatar(guiGraphics, x, y, 0xFF4A4540.toInt(), text, GRAY)
    }

    /** A 32×32 square with [text] drawn at twice the font's size. */
    private fun drawAvatar(guiGraphics: GuiGraphics, x: Int, y: Int, color: Int, text: String, textColor: Int) {
        guiGraphics.fill(x, y, x + 32, y + 32, 0xFF000000.toInt())
        guiGraphics.fill(x + 1, y + 1, x + 31, y + 31, color)
        if (text.isEmpty()) return
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + 16f, y + 16f, 0f)
        pose.scale(2f, 2f, 1f)
        guiGraphics.drawString(font, text, -font.width(text) / 2 + 1, -3, textColor)
        pose.popPose()
    }

    override fun onClose() {
        minecraft!!.setScreen(parent)
    }

    private class AccountList(minecraft: Minecraft, width: Int, height: Int, y: Int) :
        ContainerObjectSelectionList<AccountEntry>(minecraft, width, height, y, ROW_HEIGHT + 4) {
        init {
            centerListVertically = false
        }

        fun add(entry: AccountEntry) = addEntry(entry)

        override fun getRowWidth() = width - 10

        override fun getRowLeft() = x

        override fun getScrollbarPosition() = right - 6

        override fun renderListBackground(guiGraphics: GuiGraphics) {}

        override fun renderListSeparators(guiGraphics: GuiGraphics) {}
    }

    private class AccountEntry(private val account: WardenClientApi.Account) :
        ContainerObjectSelectionList.Entry<AccountEntry>() {
        private val skin = DefaultPlayerSkin.get(
            runCatching { UUID.fromString(account.uuid) }.getOrElse { UUID.nameUUIDFromBytes(account.username.toByteArray()) }
        )

        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float,
        ) {
            val font = Minecraft.getInstance().font
            val bottom = top + ROW_HEIGHT
            panel(guiGraphics, left, top, left + width, bottom, 0x4D000000)
            PlayerFaceRenderer.draw(guiGraphics, skin, left + 6, top + 4, 16)
            guiGraphics.drawString(font, account.username, left + 30, top + 3, 0xFFFFFF)
            val status = when {
                account.online -> Component.translatable("screen.lnbwarden.account.playing").withStyle(ChatFormatting.GREEN)
                account.lastSeenAt == null -> Component.translatable("screen.lnbwarden.account.never_seen")
                else -> Component.translatable("screen.lnbwarden.account.last_seen", formatDate(account.lastSeenAt))
            }
            guiGraphics.drawString(font, status, left + 30, top + 13, DARK_GRAY)
            val played = Component.translatable("screen.lnbwarden.account.played", formatDuration(account.playtimeSeconds))
            guiGraphics.drawString(font, played, left + width - 6 - font.width(played), top + 8, DARK_GRAY)
        }

        override fun children(): List<GuiEventListener> = emptyList()

        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    companion object {
        private const val CARD_TOP = 30
        private const val CARD_HEIGHT = 44
        private const val LIST_TOP = CARD_TOP + CARD_HEIGHT + 22
        private const val ROW_HEIGHT = 24
        private const val GRAY = 0xC6C6C6
        private const val DARK_GRAY = 0xA0A0A0

        /** Avatar backgrounds, picked from the user id so a user keeps theirs. */
        private val AVATAR_COLORS = intArrayOf(
            0xFF4A6FA5.toInt(), 0xFF6A4FA0.toInt(), 0xFF3F8A6E.toInt(), 0xFFA0563F.toInt(),
            0xFF8A7A2F.toInt(), 0xFF9A4870.toInt(), 0xFF3F7F9A.toInt(), 0xFF5E7A3A.toInt(),
        )

        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

        /** A dark panel with a one-pixel outline, as the card and the account rows. */
        private fun panel(guiGraphics: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            guiGraphics.fill(x0, y0, x1, y1, color)
            guiGraphics.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF3A3530.toInt())
        }

        /** The first letters of the first and last words, such as "CM" for "Charles Mahoudeau". */
        private fun initials(name: String): String {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return "?"
            val letters = if (words.size == 1) words[0].take(1) else words.first().take(1) + words.last().take(1)
            return letters.uppercase()
        }

        /** An API date in the game's language, such as "20 Jan 2027". */
        private fun formatDate(iso: String): String {
            val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
            val language = Minecraft.getInstance().languageManager.selected
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.forLanguageTag(language.replace('_', '-')))
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }

        private fun formatDuration(seconds: Double): Component {
            val minutes = (seconds / 60).toLong()
            return if (minutes < 60) Component.translatable("screen.lnbwarden.account.minutes", minutes)
            else Component.translatable("screen.lnbwarden.account.hours", minutes / 60, minutes % 60)
        }

        private fun banLine(ban: WardenClientApi.Ban): Component {
            val until = ban.expiresAt?.let { runCatching { DATE.format(Instant.parse(it)) }.getOrNull() }
            return (if (until == null) Component.translatable("screen.lnbwarden.status.banned_permanent", ban.reason)
            else Component.translatable("screen.lnbwarden.status.banned", until, ban.reason))
                .withStyle(ChatFormatting.RED)
        }

        /** The link's status, as the account button's tooltip. */
        fun statusLines(state: AuthState): List<Component> = when (state) {
            AuthState.NotLinked -> listOf(Component.translatable("screen.lnbwarden.status.not_linked"))
            AuthState.Checking -> listOf(
                Component.translatable("screen.lnbwarden.status.checking", LoadingDotsText.get(Util.getMillis()))
                    .withStyle(ChatFormatting.GRAY)
            )
            AuthState.TokenRejected -> listOf(
                Component.translatable("screen.lnbwarden.status.token_rejected").withStyle(ChatFormatting.RED)
            )
            is AuthState.Unreachable -> listOf(
                Component.translatable("screen.lnbwarden.status.unreachable").withStyle(ChatFormatting.RED)
            )
            is AuthState.Refused -> listOf(
                Component.translatable("screen.lnbwarden.status.refused", state.problem ?: "?").withStyle(ChatFormatting.RED)
            )
            is AuthState.Linked -> buildList {
                val me = state.me
                add(Component.translatable("screen.lnbwarden.status.linked", me.user.shortName))
                val used = "${me.limit.used}/${me.limit.max}"
                add(
                    if (me.accounts.isEmpty()) Component.translatable("screen.lnbwarden.status.no_accounts", used)
                    else Component.translatable(
                        "screen.lnbwarden.status.accounts",
                        me.accounts.joinToString(", ") { it.username },
                        used,
                    )
                )
                me.activeBan?.let { add(banLine(it)) }
            }
        }
    }
}
