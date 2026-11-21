package fr.bde_teknantes.lnb.warden.server.changelog

import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.changelog.Changelog
import fr.bde_teknantes.lnb.warden.common.network.ChangelogNoticePayload
import fr.bde_teknantes.lnb.warden.common.network.ChangelogSeenPayload
import fr.bde_teknantes.lnb.warden.server.AuthMode
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Tells a player who just joined about the newest changelog post they haven't seen. LNB decides what is new for them;
 * the server only relays it, since the API token never leaves the server. Only touched on the server thread.
 *
 * The call is asynchronous and short, and nothing here ever stops a player from joining: when anything fails, the
 * player is told nothing and LNB offers the post again at their next join.
 */
object ServerChangelog {
    /** The post each player was told about, until they open it. */
    private val offered = HashMap<UUID, String>()

    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val server = player.server
        // Players without the mod can't show it, and singleplayer or AuthMode.OFF mean no API calls at all.
        if (!server.isDedicatedServer || !ServerConfig.isApiConfigured || ServerConfig.AUTH_MODE.get() == AuthMode.OFF) return
        if (!player.connection.hasChannel(ChangelogNoticePayload.TYPE)) return

        val uuid = player.uuid
        val lang = Changelog.langOf(player.clientInformation().language())
        WardenServerApi.changelogUnseen(uuid, lang).thenAcceptAsync({ result ->
            val post = (result as? ApiResult.Ok)?.body?.post ?: return@thenAcceptAsync
            // The player may have left while LNB answered.
            val online = server.playerList.getPlayer(uuid) ?: return@thenAcceptAsync
            offered[uuid] = post.id
            PacketDistributor.sendToPlayer(online, notice(post))
        }, server)
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        offered.remove(event.entity.uuid)
    }

    /** The player opened the post of their notice. Anything else is ignored: a client can't mark what it wasn't offered. */
    fun onSeen(player: ServerPlayer, payload: ChangelogSeenPayload) {
        if (offered[player.uuid] != payload.id) return
        offered.remove(player.uuid)
        WardenServerApi.changelogSeen(payload.id, player.uuid)
    }

    fun notice(post: Changelog.Post) = ChangelogNoticePayload(
        post.id.take(ChangelogNoticePayload.MAX_ID_LENGTH),
        post.title.take(ChangelogNoticePayload.MAX_TITLE_LENGTH),
        post.summary.orEmpty().take(ChangelogNoticePayload.MAX_SUMMARY_LENGTH),
        post.versionTag.orEmpty().take(ChangelogNoticePayload.MAX_TAG_LENGTH),
    )
}
