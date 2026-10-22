package fr.bde_teknantes.lnb.warden.server.session

import fr.bde_teknantes.lnb.warden.common.network.RealNamesPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * The real names of online players who chose to show theirs on LNB, sent to every client with the mod. Only touched on
 * the server thread.
 */
object ServerRealNames {
    private val names = HashMap<UUID, String>()

    /** Sends [player] everyone's real name, and everyone [player]'s, which is [name] (null when hidden or a guest). */
    fun onJoin(player: ServerPlayer, name: String?) {
        set(player.server, player.uuid, name)
        if (names.isNotEmpty()) send(player, RealNamesPayload(names.toMap()))
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        set(player.server, player.uuid, null)
    }

    /** From the event stream, when a player renamed themselves on LNB or changed whether to show their real name. */
    fun update(server: MinecraftServer, uuid: UUID, name: String?) {
        if (server.playerList.getPlayer(uuid) == null) return
        set(server, uuid, name)
    }

    private fun set(server: MinecraftServer, uuid: UUID, name: String?) {
        val shown = name?.take(RealNamesPayload.MAX_NAME_LENGTH)?.takeIf { it.isNotBlank() }
        val previous = if (shown == null) names.remove(uuid) else names.put(uuid, shown)
        if (previous == shown) return
        val payload = RealNamesPayload(mapOf(uuid to shown))
        server.playerList.players.forEach { send(it, payload) }
    }

    private fun send(player: ServerPlayer, payload: RealNamesPayload) {
        // Guests may play without the mod.
        if (player.connection.hasChannel(RealNamesPayload.TYPE)) PacketDistributor.sendToPlayer(player, payload)
    }
}
