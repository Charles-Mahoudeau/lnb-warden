package fr.bde_teknantes.lnb.warden.server.anticheat

import com.mojang.authlib.GameProfile
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.FileDigest
import fr.bde_teknantes.lnb.warden.common.anticheat.FileKind
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.common.network.FilesChallengePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesChallengeResponsePayload
import fr.bde_teknantes.lnb.warden.common.network.FilesReportPayload
import fr.bde_teknantes.lnb.warden.common.network.FilesRequestPayload
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.network.registration.ChannelAttributes
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * File verification, on the server: forwards what each authenticated player's client reports to LNB's API, applies
 * the verdict it answers, and checks at random times that the client still answers with the same files.
 *
 * Everything runs on the server thread. Guests have no LNB session, so they are never checked.
 */
object ServerAnticheat {
    /** What the server knows about the files of one connected player. */
    class Tracked(val profile: GameProfile, val sessionId: String) {
        val connectionId: ByteArray = FileDigest.connectionId()

        /** The last complete list the client sent, by SHA-512. */
        var files: Map<String, ReportedFile> = emptyMap()

        /** The parts received so far of the list being sent. */
        val parts = mutableListOf<ReportedFile>()
        var partCount = 0

        /** The last report LNB stored for this session. */
        var reportId: String? = null

        /** Set while the player is in the configuration phase. */
        var task: FilesTask? = null
        var online = false
        val createdAt = Util.getMillis()

        /** A verdict that came before the player spawned. */
        var deferred: Deferred? = null

        var nextChallengeAt = Long.MAX_VALUE
        var challenge: Challenge? = null

        /** In [Util.getMillis] time: when the list asked after a wrong answer is due. Zero when none was asked. */
        var listDueAt = 0L
        var lastListAt = 0L

        /** The packs the player was last warned about, so the same warning isn't repeated. */
        var warned: Set<String> = emptySet()
        var observed: String? = null
    }

    class Deferred(val reportId: String?, val verdict: WardenServerApi.Verdict?)

    /** [second] is set for the challenge that follows a wrong answer and a fresh list. */
    class Challenge(val nonce: ByteArray, val deadline: Long, val second: Boolean)

    /** Namespaces that aren't a mod's own. */
    private val SHARED_NAMESPACES = setOf("minecraft", "neoforge", "c", LnbWarden.ID)

    private const val ANSWER_MS = 10_000L
    private const val LIST_MS = 30_000L
    private const val MIN_LIST_INTERVAL_MS = 5_000L
    private const val MAX_PARTS = 200

    /** A player who never spawned and never left: their connection died during configuration. */
    private const val STALE_MS = 5 * 60_000L

    /** How often a player is challenged, in seconds. The system properties shorten it for tests. */
    private val CHALLENGE_MIN_S = Integer.getInteger("lnbwarden.anticheat.challengeMinSeconds", 120).coerceAtLeast(5)
    private val CHALLENGE_MAX_S = Integer.getInteger("lnbwarden.anticheat.challengeMaxSeconds", 300).coerceAtLeast(CHALLENGE_MIN_S)

    private val tracked = ConcurrentHashMap<UUID, Tracked>()
    private val tasks = ConcurrentHashMap<ServerConfigurationPacketListenerImpl, FilesTask>()
    private var failing = false

    val count: Int
        get() = tracked.size

    /** Called by `ServerAuth`, right after it registered its own task: the files are asked once the player is authenticated. */
    fun registerTask(event: RegisterConfigurationTasksEvent, listener: ServerConfigurationPacketListenerImpl, server: MinecraftServer) {
        if (!ServerConfig.ANTICHEAT_ENABLED.get()) return
        val task = FilesTask(listener, server)
        tasks[listener] = task
        event.register(task)
    }

    fun begin(profile: GameProfile, sessionId: String, task: FilesTask): Tracked {
        val entry = Tracked(profile, sessionId)
        entry.task = task
        tracked[profile.id] = entry
        return entry
    }

    fun forget(entry: Tracked) {
        tracked.remove(entry.profile.id, entry)
    }

    // ---- What the client sends ----

    fun onReport(listener: Any, payload: FilesReportPayload) {
        val entry = tracked[uuidOf(listener) ?: return] ?: return
        entry.parts += payload.files
        if (++entry.partCount > MAX_PARTS || entry.parts.size > FilesReportPayload.MAX_FILES) {
            LnbWarden.LOGGER.warn("{} sent too long a list of files: ignoring it", entry.profile.name)
            entry.parts.clear()
            entry.partCount = 0
            return
        }
        if (!payload.last) return

        val files = LinkedHashMap<String, ReportedFile>()
        entry.parts.forEach { files.putIfAbsent(it.sha512, it) }
        entry.parts.clear()
        entry.partCount = 0

        val task = entry.task
        if (task != null) task.onFiles(entry, files) else onListInGame(entry, files)
    }

    fun onChallengeResponse(listener: Any, payload: FilesChallengeResponsePayload) {
        val entry = tracked[uuidOf(listener) ?: return] ?: return
        val challenge = entry.challenge ?: return
        entry.challenge = null
        val expected = FileDigest.of(challenge.nonce, entry.connectionId, entry.files.keys)
        if (MessageDigest.isEqual(expected, payload.digest)) {
            scheduleChallenge(entry)
            return
        }

        val player = playerOf(entry) ?: return
        if (challenge.second) {
            LnbWarden.LOGGER.info("{} answered a file challenge wrong", entry.profile.name)
            event(entry, "challengeWrong")
            scheduleChallenge(entry)
        } else {
            // The client may just have changed packs: ask for its list again, then challenge it once more.
            entry.listDueAt = Util.getMillis() + LIST_MS
            PacketDistributor.sendToPlayer(player, FilesRequestPayload(entry.connectionId))
        }
    }

    private fun onListInGame(entry: Tracked, files: Map<String, ReportedFile>) {
        val player = playerOf(entry) ?: return
        val now = Util.getMillis()
        val asked = entry.listDueAt != 0L
        if (!asked && now - entry.lastListAt < MIN_LIST_INTERVAL_MS) return
        entry.lastListAt = now
        entry.listDueAt = 0L

        if (files.keys != entry.files.keys) {
            val before = entry.files
            entry.files = files
            val added = modsOf(files) - modsOf(before)
            val removed = modsOf(before) - modsOf(files)
            val sent = if (files.isEmpty()) CompletableFuture.completedFuture(null) else report(entry)
            // Mods can't be loaded or unloaded while the game runs. Packs can, from the options: no penalty for them.
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                LnbWarden.LOGGER.info("The mods of {} changed while they play", entry.profile.name)
                sent.thenRun { event(entry, "modSetChanged", mapOf("added" to added.take(20), "removed" to removed.take(20))) }
            }
        }
        if (asked) challenge(entry, player, second = true)
    }

    private fun modsOf(files: Map<String, ReportedFile>): Set<String> =
        files.values.filter { it.kind == FileKind.MOD || it.kind == FileKind.CLIENT }.mapTo(HashSet()) { it.sha512 }

    // ---- Challenges ----

    fun onServerTick(event: ServerTickEvent.Post) {
        if (tasks.isNotEmpty()) {
            tasks.entries.removeIf { (_, task) -> task.isOver }
            tasks.values.forEach(FilesTask::tick)
        }
        if (tracked.isEmpty()) return

        val now = Util.getMillis()
        for (entry in tracked.values) {
            if (!entry.online) {
                if (entry.task == null && now - entry.createdAt > STALE_MS) forget(entry)
                continue
            }
            val player = event.server.playerList.getPlayer(entry.profile.id) ?: continue
            val challenge = entry.challenge
            when {
                challenge != null && now > challenge.deadline -> missed(entry, "answer")
                entry.listDueAt != 0L && now > entry.listDueAt -> missed(entry, "list")
                challenge == null && entry.listDueAt == 0L && now >= entry.nextChallengeAt -> challenge(entry, player, second = false)
            }
        }
    }

    private fun challenge(entry: Tracked, player: ServerPlayer, second: Boolean) {
        val challenge = Challenge(FileDigest.nonce(), Util.getMillis() + ANSWER_MS, second)
        entry.challenge = challenge
        entry.nextChallengeAt = Long.MAX_VALUE
        PacketDistributor.sendToPlayer(player, FilesChallengePayload(challenge.nonce))
    }

    private fun missed(entry: Tracked, what: String) {
        LnbWarden.LOGGER.info("{} didn't answer a file challenge in time", entry.profile.name)
        entry.challenge = null
        entry.listDueAt = 0L
        event(entry, "challengeMissed", mapOf("missing" to what))
        scheduleChallenge(entry)
    }

    private fun scheduleChallenge(entry: Tracked) {
        val seconds = ThreadLocalRandom.current().nextInt(CHALLENGE_MIN_S, CHALLENGE_MAX_S + 1)
        entry.nextChallengeAt = Util.getMillis() + seconds * 1000L
    }

    // ---- Players coming and going ----

    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val entry = tracked[player.uuid] ?: return
        entry.task = null
        entry.online = true
        scheduleChallenge(entry)
        entry.deferred?.let { apply(entry, player, it.reportId, it.verdict) }
        entry.deferred = null
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        tracked.remove(event.entity.uuid)
    }

    // ---- LNB's API ----

    /** Sends the player's current files to LNB, and applies the verdict it answers. */
    private fun report(entry: Tracked): CompletableFuture<*> {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return CompletableFuture.completedFuture(null)
        return WardenServerApi.anticheatReport(reportBody(entry)).thenAcceptAsync({ result ->
            if (result is ApiResult.Ok) {
                onAnswered()
                result.body.reportId?.let { entry.reportId = it }
                onVerdict(entry, result.body.reportId, result.body.verdict)
            } else {
                onFailure(result)
            }
        }, server)
    }

    fun reportBody(entry: Tracked) = WardenServerApi.AnticheatReportBody(
        entry.sessionId,
        // The API refuses a nested jar whose top-level file isn't in the report, or is nested itself.
        entry.files.values.filter { file ->
            file.nestedIn == null || entry.files[file.nestedIn]?.let { it.nestedIn == null } == true
        }.map { file ->
            WardenServerApi.AnticheatFile(
                kind = file.kind.id,
                sha512 = file.sha512,
                fingerprint = file.fingerprint,
                filename = file.filename,
                modId = file.modIds.firstOrNull(),
                name = file.name,
                version = file.version,
                loader = file.loader,
                facts = file.facts?.let { WardenServerApi.AnticheatFacts(it.coreShaders, it.alteredTextures) },
                nestedIn = file.nestedIn,
            )
        },
    )

    /** Tells LNB about something that costs the player trust, and applies the verdict it answers. */
    fun event(entry: Tracked, kind: String, data: Map<String, Any> = emptyMap()) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val body = WardenServerApi.AnticheatEventsBody(
            entry.sessionId,
            listOf(WardenServerApi.AnticheatEvent(UUID.randomUUID().toString(), kind, data)),
        )
        WardenServerApi.anticheatEvents(body).thenAcceptAsync({ result ->
            if (result is ApiResult.Ok) {
                onAnswered()
                onVerdict(entry, entry.reportId, result.body.verdict)
            } else {
                onFailure(result)
            }
        }, server)
    }

    /** From LNB's event stream: a verdict changed, such as after a lookup ended or staff banned a file. */
    fun onVerdictEvent(sessionId: String, uuid: UUID, reportId: String?, verdict: WardenServerApi.Verdict?) {
        val entry = tracked[uuid]?.takeIf { it.sessionId == sessionId } ?: return
        if (reportId != null) entry.reportId = reportId
        onVerdict(entry, reportId, verdict)
    }

    /** Asks LNB again for the verdict of every player online, after the event stream was down. */
    fun resync(server: MinecraftServer) {
        for (entry in tracked.values) {
            if (!entry.online) continue
            WardenServerApi.anticheatSession(entry.sessionId).thenAcceptAsync({ result ->
                if (result is ApiResult.Ok) onVerdict(entry, result.body.reportId, result.body.verdict)
            }, server)
        }
    }

    private fun onVerdict(entry: Tracked, reportId: String?, verdict: WardenServerApi.Verdict?) {
        if (tracked[entry.profile.id] !== entry) return
        val player = playerOf(entry)
        if (player == null) {
            // Still in configuration: the task applies its own report's verdict, the others wait for the spawn.
            entry.deferred = Deferred(reportId, verdict)
            return
        }
        apply(entry, player, reportId, verdict)
    }

    private fun apply(entry: Tracked, player: ServerPlayer, reportId: String?, verdict: WardenServerApi.Verdict?) {
        logObserved(entry, verdict)
        when (val outcome = Verdicts.decide(verdict)) {
            Verdicts.Outcome.Pass -> entry.warned = emptySet()
            is Verdicts.Outcome.Warn -> {
                if (outcome.sha512s == entry.warned) return
                entry.warned = outcome.sha512s
                player.sendSystemMessage(warning(outcome))
            }
            is Verdicts.Outcome.Remove -> {
                record(entry, reportId, outcome)
                player.connection.disconnect(outcome.message)
            }
        }
    }

    fun warning(outcome: Verdicts.Outcome.Warn): Component =
        Component.translatable("chat.lnbwarden.anticheat.pack_warning", outcome.names.joinToString(", ").ifEmpty { "?" })
            .withStyle(ChatFormatting.GOLD)

    /** Issues the ban when the verdict is one, then tells LNB what the server did. The caller disconnects the player. */
    fun record(entry: Tracked, reportId: String?, outcome: Verdicts.Outcome.Remove) {
        val name = entry.profile.name
        LnbWarden.LOGGER.info("LNB's anticheat: {} for {} ({})", outcome.action, name, outcome.code)
        val banned = if (outcome.banReason == null) {
            CompletableFuture.completedFuture(Unit)
        } else {
            WardenServerApi.ban(entry.profile.id, outcome.banReason).thenApply { result ->
                if (result !is ApiResult.Ok) LnbWarden.LOGGER.error("Couldn't ban {} on LNB: {}", name, result.describe())
            }
        }
        val action = WardenServerApi.AnticheatActionBody(entry.sessionId, reportId, outcome.action, outcome.code, outcome.sha512)
        banned.thenCompose { WardenServerApi.anticheatAction(action) }.thenAccept { result ->
            if (result !is ApiResult.Ok) {
                LnbWarden.LOGGER.warn("Couldn't tell LNB the {} of {}: {}", outcome.action, name, result.describe())
            }
        }
    }

    /** While LNB only observes, the verdict is `allow`: this logs what it would have been, once per change. */
    fun logObserved(entry: Tracked, verdict: WardenServerApi.Verdict?) {
        val wouldBe = verdict?.wouldBe?.takeIf { it != verdict.action }
        if (wouldBe == entry.observed) return
        entry.observed = wouldBe
        if (wouldBe == null) return
        val names = verdict.reasons.orEmpty().mapNotNull { it.name ?: it.code }.distinct()
        LnbWarden.LOGGER.info("LNB's anticheat only observes: {} would get a {} ({})", entry.profile.name, wouldBe, names.joinToString(", "))
    }

    fun onAnswered() {
        if (!failing) return
        failing = false
        LnbWarden.LOGGER.info("LNB's anticheat answers again")
    }

    /** LNB's API is not on the way of players: when it fails, they play. Logged once per outage. */
    fun onFailure(result: ApiResult<*>) {
        if (failing) return
        failing = true
        if (result.isDisabled) LnbWarden.LOGGER.info("LNB's anticheat is turned off on the website: files aren't checked")
        else LnbWarden.LOGGER.warn("LNB's anticheat didn't answer ({}): files aren't checked until it does", result.describe())
    }

    // ---- Channels ----

    /**
     * The mods of this server whose network channels the client negotiated, but that it didn't report: a client
     * can't use a mod's channels without the mod. Only this server's own mod IDs are looked at, because a channel's
     * namespace isn't always its mod's ID.
     */
    fun unreportedChannelMods(connection: Connection, files: Collection<ReportedFile>): Set<String> {
        val namespaces = HashSet<String>()
        try {
            ChannelAttributes.getPayloadSetup(connection)?.channels()?.values?.forEach { channels ->
                channels.keys.mapTo(namespaces) { it.namespace }
            }
            ChannelAttributes.getOrCreateAdHocChannels(connection).toList().mapTo(namespaces) { it.namespace }
        } catch (e: Exception) {
            LnbWarden.LOGGER.debug("Couldn't read the channels of a connection", e)
            return emptySet()
        }
        val reported = files.flatMapTo(HashSet()) { it.modIds }
        val mods = ModList.get()
        return namespaces.filterTo(HashSet()) { it !in SHARED_NAMESPACES && it !in reported && mods.isLoaded(it) }
    }

    private fun playerOf(entry: Tracked): ServerPlayer? =
        if (entry.online) ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(entry.profile.id) else null

    private fun uuidOf(listener: Any): UUID? = when (listener) {
        is ServerConfigurationPacketListenerImpl -> listener.owner.id
        is ServerGamePacketListenerImpl -> listener.player.uuid
        else -> null
    }
}
