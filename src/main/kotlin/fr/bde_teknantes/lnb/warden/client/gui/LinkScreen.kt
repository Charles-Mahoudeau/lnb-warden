package fr.bde_teknantes.lnb.warden.client.gui

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import fr.bde_teknantes.lnb.warden.client.auth.ClientAuth
import fr.bde_teknantes.lnb.warden.client.auth.Session
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * Links the mod to an LNB account: shows a 6-digit code to enter on the website, and polls until the player
 * approves or denies it.
 */
class LinkScreen(private val parent: Screen?) : Screen(Component.translatable("screen.lnbwarden.link.title")) {
    private sealed interface Step {
        data object Requesting : Step
        data class Waiting(val request: WardenClientApi.LinkRequest, val expiresAt: Long) : Step
        data object Denied : Step
        data object Expired : Step
        data class Failed(val message: Component) : Step
    }

    private var step: Step = Step.Requesting
    private var intervalMs = 5_000L
    private var nextPollAt = 0L
    private var polling = false
    private var pollError: Component? = null

    override fun init() {
        if (step == Step.Requesting && !polling) requestCode()

        val center = width / 2
        val y = height / 2 + 40
        when (val current = step) {
            is Step.Waiting -> {
                addRenderableWidget(Button.builder(Component.translatable("screen.lnbwarden.link.open")) {
                    Util.getPlatform().openUri(current.request.verificationUriComplete)
                }.bounds(center - 154, y, 100, 20).build())
                addRenderableWidget(Button.builder(Component.translatable("screen.lnbwarden.link.copy")) {
                    minecraft!!.keyboardHandler.clipboard = current.request.userCode
                }.bounds(center - 50, y, 100, 20).build())
                addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
                    .bounds(center + 54, y, 100, 20).build())
            }
            Step.Expired, is Step.Failed -> {
                val label = if (current == Step.Expired) "screen.lnbwarden.link.new_code" else "screen.lnbwarden.link.retry"
                addRenderableWidget(Button.builder(Component.translatable(label)) { restart() }
                    .bounds(center - 102, y, 100, 20).build())
                addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { onClose() }
                    .bounds(center + 2, y, 100, 20).build())
            }
            Step.Denied, Step.Requesting -> {
                val label = if (current == Step.Denied) CommonComponents.GUI_BACK else CommonComponents.GUI_CANCEL
                addRenderableWidget(Button.builder(label) { onClose() }.bounds(center - 100, y, 200, 20).build())
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val center = width / 2
        val top = height / 2 - 70
        guiGraphics.drawCenteredString(font, title, center, top, 0xFFFFFF)

        when (val current = step) {
            Step.Requesting -> guiGraphics.drawCenteredString(
                font,
                Component.translatable("screen.lnbwarden.link.requesting", LoadingDotsText.get(Util.getMillis())),
                center, top + 40, 0xAAAAAA,
            )
            is Step.Waiting -> {
                guiGraphics.drawCenteredString(
                    font,
                    Component.translatable("screen.lnbwarden.link.instructions", current.request.verificationUri),
                    center, top + 20, 0xAAAAAA,
                )

                val code = current.request.userCode.chunked(3).joinToString(" ")
                guiGraphics.pose().pushPose()
                guiGraphics.pose().translate(center.toFloat(), (top + 38).toFloat(), 0f)
                guiGraphics.pose().scale(3f, 3f, 1f)
                guiGraphics.drawCenteredString(font, code, 0, 0, 0xFFFFFF)
                guiGraphics.pose().popPose()

                val secondsLeft = max(0L, (current.expiresAt - Util.getMillis()) / 1000)
                val timeLeft = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)
                guiGraphics.drawCenteredString(
                    font,
                    Component.translatable("screen.lnbwarden.link.time_left", timeLeft),
                    center, top + 72, 0xAAAAAA,
                )
                pollError?.let { guiGraphics.drawCenteredString(font, it, center, top + 86, 0xFF5555) }
            }
            Step.Denied -> guiGraphics.drawCenteredString(
                font, Component.translatable("screen.lnbwarden.link.denied"), center, top + 40, 0xFF5555,
            )
            Step.Expired -> guiGraphics.drawCenteredString(
                font, Component.translatable("screen.lnbwarden.link.expired"), center, top + 40, 0xFF5555,
            )
            is Step.Failed -> guiGraphics.drawCenteredString(font, current.message, center, top + 40, 0xFF5555)
        }
    }

    override fun tick() {
        val current = step as? Step.Waiting ?: return
        if (polling || Util.getMillis() < nextPollAt) return
        poll(current)
    }

    override fun onClose() {
        minecraft!!.setScreen(parent)
    }

    private val isOpen: Boolean
        get() = minecraft?.screen === this

    private fun restart() {
        step = Step.Requesting
        rebuildWidgets()
    }

    private fun requestCode() {
        polling = true
        WardenClientApi.createLinkRequest().thenAcceptAsync({ result ->
            polling = false
            if (!isOpen) return@thenAcceptAsync
            step = when {
                result is ApiResult.Ok -> {
                    intervalMs = (result.body.interval * 1000).toLong().coerceAtLeast(1000)
                    nextPollAt = Util.getMillis() + intervalMs
                    pollError = null
                    Step.Waiting(result.body, Util.getMillis() + (result.body.expiresIn * 1000).toLong())
                }
                result is ApiResult.Http && result.status == 429 ->
                    Step.Failed(Component.translatable("screen.lnbwarden.link.too_many"))
                else -> {
                    LnbWarden.LOGGER.warn("Couldn't create an LNB link request: {}", result.describe())
                    Step.Failed(Component.translatable("screen.lnbwarden.link.unreachable"))
                }
            }
            rebuildWidgets()
        }, minecraft!!)
    }

    private fun poll(waiting: Step.Waiting) {
        polling = true
        WardenClientApi.poll(waiting.request).thenAcceptAsync({ result ->
            polling = false
            if (!isOpen || step !== waiting) return@thenAcceptAsync
            nextPollAt = Util.getMillis() + intervalMs
            when {
                result is ApiResult.Ok -> {
                    pollError = null
                    onPollAnswer(result.body)
                }
                result is ApiResult.Http && result.status == 429 -> intervalMs += 5_000
                // Unknown request: it expired and was cleaned up.
                result is ApiResult.Http && result.status == 404 -> show(Step.Expired)
                else -> {
                    if (pollError == null) LnbWarden.LOGGER.warn("Polling the LNB link request failed: {}", result.describe())
                    pollError = Component.translatable("screen.lnbwarden.link.poll_failed").withStyle(ChatFormatting.RED)
                }
            }
        }, minecraft!!)
    }

    private fun onPollAnswer(answer: WardenClientApi.PollResult) {
        when (answer.status) {
            "pending" -> Unit
            "denied" -> show(Step.Denied)
            "expired" -> show(Step.Expired)
            "approved" -> {
                val token = answer.token
                val user = answer.user
                val expiresAt = answer.expiresAt
                if (token == null || user == null || expiresAt == null) {
                    LnbWarden.LOGGER.error("LNB approved the link without a token")
                    show(Step.Failed(Component.translatable("screen.lnbwarden.link.unreachable")))
                    return
                }
                ClientAuth.onLinked(Session(Session.VERSION, ClientConfig.apiUrl, token, expiresAt, user.id, user.name))
                SystemToast.add(
                    minecraft!!.toasts,
                    TOAST,
                    Component.translatable("toast.lnbwarden.linked.title"),
                    Component.translatable("toast.lnbwarden.linked", user.name),
                )
                onClose()
            }
            else -> LnbWarden.LOGGER.warn("Unknown link request status {}", answer.status)
        }
    }

    private fun show(newStep: Step) {
        step = newStep
        rebuildWidgets()
    }

    private companion object {
        val TOAST = SystemToast.SystemToastId()
    }
}
