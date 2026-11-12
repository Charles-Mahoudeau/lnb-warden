package fr.bde_teknantes.lnb.warden.client.anticheat

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.LnbServer
import fr.bde_teknantes.lnb.warden.common.anticheat.FileDigest
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import fr.bde_teknantes.lnb.warden.common.network.FilesChallengePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesChallengeResponsePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesReportPayload
import fr.bde_teknantes.lnb.warden.common.network.FilesRequestPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl
import net.minecraft.network.Connection
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The client side of LNB's file verification: tells LNB's server which mods, resource packs and shader pack are
 * loaded, by their hashes, and answers its challenges.
 *
 * Only LNB's server is answered: the list says a lot about a player's game.
 */
object ClientFiles {
    private const val POLL_TICKS = 20

    /** One thread: scans never overlap, and a report always follows the one before it. */
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LNB Warden files").apply { isDaemon = true }
    }

    /** What the server last gave or was sent on the current connection. Client thread only. */
    private class Link(val connection: Connection, val connectionId: ByteArray) {
        var sent: Set<String> = emptySet()
        var packsKey: Any? = null
    }

    private var link: Link? = null
    private var ticks = 0

    /** Hashes the mods while the game starts, so the first join doesn't wait for it. */
    fun warmUp() {
        executor.execute {
            try {
                // Not the packs: they aren't chosen yet, and listing them needs the client thread.
                val files = FileScanner.hash(FileScanner.mods)
                LnbWarden.LOGGER.debug("Hashed {} mod files ahead of time", files.size)
            } catch (e: Exception) {
                LnbWarden.LOGGER.debug("Couldn't hash the mods ahead of time", e)
            }
        }
    }

    fun onRequest(payload: FilesRequestPayload, context: IPayloadContext) {
        val connection = context.connection()
        val ip = (context.listener() as? ClientCommonPacketListenerImpl)?.serverData?.ip
        if (!LnbServer.isLnb(ip)) {
            LnbWarden.LOGGER.warn("{} asked for the list of loaded files, but it isn't LNB's server: not answering", ip)
            link = null
            return
        }
        val link = Link(connection, payload.connectionId)
        this.link = link
        report(link, force = true)
    }

    fun onChallenge(payload: FilesChallengePayload, context: IPayloadContext) {
        val link = this.link?.takeIf { it.connection === context.connection() } ?: return
        context.reply(FilesChallengeResponsePayload(FileDigest.of(payload.nonce, link.connectionId, link.sent)))
    }

    /** Called every client tick: reports again when the player changes packs while on LNB's server. */
    fun tick() {
        val link = this.link ?: return
        if (++ticks < POLL_TICKS) return
        ticks = 0
        if (!link.connection.isConnected) {
            this.link = null
            return
        }
        // Only in game: during configuration the server asks by itself.
        if (Minecraft.getInstance().connection?.connection !== link.connection) return
        if (FileScanner.packsKey() != link.packsKey) report(link, force = false)
    }

    /** Sends the list to the server. Without [force], only when it isn't the one the server already has. */
    private fun report(link: Link, force: Boolean) {
        link.packsKey = FileScanner.packsKey()
        val candidates = FileScanner.candidates()
        CompletableFuture.supplyAsync({ FileScanner.hash(candidates) }, executor).whenCompleteAsync({ files, error ->
            if (error != null) LnbWarden.LOGGER.error("Couldn't list the loaded files for LNB's server", error)
            send(link, files.orEmpty(), force)
        }, Minecraft.getInstance())
    }

    private fun send(link: Link, files: List<ReportedFile>, force: Boolean) {
        if (this.link !== link || !link.connection.isConnected) return
        val hashes = files.mapTo(HashSet()) { it.sha512 }
        if (!force && hashes == link.sent) return
        link.sent = hashes
        LnbWarden.LOGGER.debug("Reporting {} loaded files to LNB's server", files.size)
        for (part in FilesReportPayload.split(files)) link.connection.send(ServerboundCustomPayloadPacket(part as CustomPacketPayload))
    }
}
