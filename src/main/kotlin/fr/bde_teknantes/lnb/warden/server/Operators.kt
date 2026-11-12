package fr.bde_teknantes.lnb.warden.server

import net.minecraft.network.chat.Component
import net.neoforged.neoforge.server.ServerLifecycleHooks

/** Messages for the operators who are online. Safe to call from any thread. */
object Operators {
    fun notify(message: Component) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            val players = server.playerList
            players.players.filter { players.isOp(it.gameProfile) }.forEach { it.sendSystemMessage(message) }
        }
    }
}
