package fr.bde_teknantes.lnb.warden.server.api

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.time.Instant

/**
 * Whether the server can reach LNB's API, from the result of every call.
 * Logs and tells operators once per outage and once at recovery, not once per failed call.
 */
object ApiHealth {
    data class Error(val at: Instant, val message: String)

    @Volatile
    var lastSuccess: Instant? = null
        private set

    @Volatile
    var lastError: Error? = null
        private set

    private var down = false

    @Synchronized
    fun record(what: String, result: ApiResult<*>) {
        // A 401 means the server's own token is wrong: as bad as an outage.
        val failed = result.isUnreachable || (result is ApiResult.Http && result.status == 401)
        if (failed) {
            lastError = Error(Instant.now(), "$what: ${result.describe()}")
            if (!down) {
                down = true
                LnbWarden.LOGGER.warn("LNB's API is unreachable ({}: {})", what, result.describe())
                notifyOperators(
                    Component.translatable("chat.lnbwarden.api.down", result.describe()).withStyle(ChatFormatting.RED)
                )
            }
        } else {
            lastSuccess = Instant.now()
            if (down) {
                down = false
                LnbWarden.LOGGER.info("LNB's API is reachable again")
                notifyOperators(Component.translatable("chat.lnbwarden.api.up").withStyle(ChatFormatting.GREEN))
            }
        }
    }

    private fun notifyOperators(message: Component) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            val players = server.playerList
            players.players.filter { players.isOp(it.gameProfile) }.forEach { it.sendSystemMessage(message) }
        }
    }
}
