package fr.bde_teknantes.lnb.warden.client

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import java.util.UUID

/**
 * The real names the server sent, for players who chose to show theirs on LNB. Drawn under their name tag and next to
 * their name in the tab list, by mixins.
 */
object ClientRealNames {
    // Only touched on the render thread.
    private val names = HashMap<UUID, Component>()

    fun update(changes: Map<UUID, String?>) {
        for ((uuid, name) in changes) {
            if (name == null) names.remove(uuid)
            else names[uuid] = Component.literal(name).withStyle(ChatFormatting.GRAY)
        }
    }

    @JvmStatic
    fun get(uuid: UUID): Component? = names[uuid]

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        names.clear()
    }
}
