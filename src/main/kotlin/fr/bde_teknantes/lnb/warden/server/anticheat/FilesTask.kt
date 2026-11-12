package fr.bde_teknantes.lnb.warden.server.anticheat

import com.mojang.authlib.GameProfile
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.common.network.FilesRequestPayload
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import fr.bde_teknantes.lnb.warden.server.auth.ServerAuth
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import java.util.function.Consumer

/**
 * `lnbwarden:files`: after `lnbwarden:auth`, asks the client which files it loaded and keeps the player in the
 * configuration phase until LNB's API said what to do with them.
 *
 * A player LNB can't judge (its API is down, or its anticheat is off) gets in. A client that doesn't answer doesn't.
 */
class FilesTask(
    private val listener: ServerConfigurationPacketListenerImpl,
    private val server: MinecraftServer,
) : ICustomConfigurationTask {
    private val profile: GameProfile = listener.owner
    private var entry: ServerAnticheat.Tracked? = null

    /** In [Util.getMillis] time. */
    @Volatile
    private var deadline = Long.MAX_VALUE
    private var answered = false
    private var done = false

    /** Whether there is nothing left to tick: the task ended, or its connection did. */
    val isOver: Boolean
        get() {
            if (done) return true
            if (listener.getConnection().isConnected) return false
            abandon()
            return true
        }

    override fun type(): ConfigurationTask.Type = TYPE

    override fun run(sender: Consumer<CustomPacketPayload>) {
        // Guests have no LNB session to report files for.
        val sessionId = ServerAuth.sessionOf(profile.id)
        if (sessionId == null || !listener.hasChannel(FilesRequestPayload.TYPE)) {
            server.execute(::finish)
            return
        }
        val entry = ServerAnticheat.begin(profile, sessionId, this)
        this.entry = entry
        deadline = Util.getMillis() + TIMEOUT_MS
        sender.accept(FilesRequestPayload(entry.connectionId))
    }

    /** Called every server tick. */
    fun tick() {
        if (answered || done || Util.getMillis() < deadline) return
        answered = true
        LnbWarden.LOGGER.info("{} didn't list their files in time", profile.name)
        entry?.let { ServerAnticheat.event(it, "challengeMissed", mapOf("missing" to "join")) }
        refuse(DisconnectReasons.filesTimedOut())
    }

    fun onFiles(entry: ServerAnticheat.Tracked, files: Map<String, ReportedFile>) {
        if (answered || done) return
        answered = true
        entry.files = files
        if (files.isEmpty()) {
            // Nothing the API would take, as in a development run where the mods are folders of classes.
            LnbWarden.LOGGER.info("{} reported no file", profile.name)
            finish()
            return
        }

        WardenServerApi.anticheatReport(ServerAnticheat.reportBody(entry)).thenAcceptAsync({ result ->
            if (done || !listener.getConnection().isConnected) return@thenAcceptAsync
            if (result !is ApiResult.Ok) {
                ServerAnticheat.onFailure(result)
                finish()
                return@thenAcceptAsync
            }
            ServerAnticheat.onAnswered()
            val report = result.body
            entry.reportId = report.reportId
            LnbWarden.LOGGER.info(
                "LNB's anticheat ({}): {} for {} with {} files, {} still looked up",
                report.mode, report.verdict?.action, profile.name, files.size, report.pending,
            )
            checkChannels(entry, files.values)
            onVerdict(entry, report)
        }, server)
    }

    private fun onVerdict(entry: ServerAnticheat.Tracked, report: WardenServerApi.AnticheatReport) {
        ServerAnticheat.logObserved(entry, report.verdict)
        when (val outcome = Verdicts.decide(report.verdict)) {
            Verdicts.Outcome.Pass -> finish()
            is Verdicts.Outcome.Warn -> {
                // Shown once the player has a chat to read it in.
                entry.deferred = ServerAnticheat.Deferred(report.reportId, report.verdict)
                finish()
            }
            is Verdicts.Outcome.Remove -> {
                ServerAnticheat.record(entry, report.reportId, outcome)
                refuse(outcome.message)
            }
        }
    }

    private fun checkChannels(entry: ServerAnticheat.Tracked, files: Collection<ReportedFile>) {
        if (!ServerConfig.ANTICHEAT_CHANNEL_CHECK.get()) return
        val mods = ServerAnticheat.unreportedChannelMods(listener.getConnection(), files)
        if (mods.isEmpty()) return
        LnbWarden.LOGGER.info("{} uses the channels of mods they didn't report: {}", profile.name, mods)
        ServerAnticheat.event(entry, "channelMismatch", mapOf("mods" to mods.sorted().take(20)))
    }

    private fun finish() {
        if (done) return
        done = true
        entry?.task = null
        listener.finishCurrentTask(TYPE)
    }

    private fun refuse(reason: Component) {
        if (done) return
        done = true
        endSession("kicked")
        listener.disconnect(reason)
    }

    /** The player left while the task waited. */
    private fun abandon() {
        done = true
        endSession("quit")
    }

    /** The player was allowed by LNB but won't spawn: nothing else would end their session before it times out. */
    private fun endSession(reason: String) {
        val entry = this.entry ?: return
        ServerAnticheat.forget(entry)
        val join = ServerAuth.takePending(profile.id) ?: return
        val sessionId = join.sessionId ?: return
        WardenServerApi.endSession(sessionId, reason).thenAccept { result ->
            if (result !is ApiResult.Ok) {
                LnbWarden.LOGGER.warn("Couldn't end the LNB session of {}: {}", profile.name, result.describe())
            }
        }
    }

    companion object {
        val TYPE = ConfigurationTask.Type(LnbWarden.id("files").toString())

        /** Hashing a large modpack for the first time takes a while. */
        private const val TIMEOUT_MS = 60 * 1000L
    }
}
