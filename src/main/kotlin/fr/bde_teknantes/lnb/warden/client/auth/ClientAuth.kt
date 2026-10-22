package fr.bde_teknantes.lnb.warden.client.auth

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.client.api.WardenClientApi
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.Util
import net.minecraft.client.Minecraft
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CompletableFuture

/** What the client knows about its LNB link. Shown by the title screen's status widget. */
sealed interface AuthState {
    data object NotLinked : AuthState
    data object Checking : AuthState
    data class Linked(val me: WardenClientApi.Me) : AuthState
    data object TokenRejected : AuthState

    /** The API refused this device for another reason than its token, such as `schoolNotAllowed`. */
    data class Refused(val problem: String?) : AuthState

    /** [retryAt] is in [Util.getMillis] time. */
    data class Unreachable(val retryAt: Long) : AuthState
}

/**
 * The device session and its state, on the render thread only.
 *
 * Network answers come back on HTTP threads and are handed to the render thread with [Minecraft.execute].
 */
object ClientAuth {
    var state: AuthState = AuthState.NotLinked
        private set

    /** The stored session, when there is a usable one. */
    var session: Session? = null
        private set

    private val store by lazy { SessionStore(dataDir()) }
    private var started = false
    private var failedRefreshes = 0
    private var refreshing: CompletableFuture<ApiResult<WardenClientApi.Me>>? = null

    /** 10 s, 30 s, 1 min, then every 5 min. */
    private val RETRY_DELAYS_SECONDS = longArrayOf(10, 30, 60, 300)

    /** Reads `session.json` and checks it with the API, once per game. Returns whether a usable token was found. */
    fun start(): Boolean {
        if (started) return session != null
        started = true
        session = store.load(ClientConfig.apiUrl)
        if (session == null) {
            state = AuthState.NotLinked
            return false
        }
        LnbWarden.LOGGER.info("LNB link found for {} ({})", session!!.name, session!!.userId)
        refresh(showChecking = true)
        return true
    }

    /**
     * Asks the API who this device belongs to, and updates [state]. Concurrent calls share one request.
     * The future completes on the render thread, after [state] was updated.
     */
    fun refresh(showChecking: Boolean = false): CompletableFuture<ApiResult<WardenClientApi.Me>> {
        refreshing?.let { return it }
        val current = session
            ?: return CompletableFuture.completedFuture(ApiResult.Http(401, null))
        if (showChecking) state = AuthState.Checking

        val minecraft = Minecraft.getInstance()
        val future = WardenClientApi.me(current.token).thenApplyAsync({ result ->
            refreshing = null
            // Signed out or relinked while the request ran.
            if (session === current) onMe(current, result)
            result
        }, minecraft)
        // Already done when the request failed before leaving (bad apiUrl): Minecraft ran the callback inline.
        if (!future.isDone) refreshing = future
        return future
    }

    private fun onMe(current: Session, result: ApiResult<WardenClientApi.Me>) {
        when {
            result is ApiResult.Ok -> {
                failedRefreshes = 0
                state = AuthState.Linked(result.body)
                extendExpiry(current, result.body.device.expiresAt)
            }
            result is ApiResult.Http && result.status == 401 -> {
                LnbWarden.LOGGER.info("LNB rejected the device token of {}, forgetting it", current.userId)
                forget()
                state = AuthState.TokenRejected
            }
            result.isUnreachable -> {
                val delay = RETRY_DELAYS_SECONDS[failedRefreshes.coerceAtMost(RETRY_DELAYS_SECONDS.size - 1)]
                if (state !is AuthState.Unreachable) {
                    LnbWarden.LOGGER.warn("LNB's API is unreachable: {}. Retrying in {} s", result.describe(), delay)
                }
                failedRefreshes++
                state = AuthState.Unreachable(Util.getMillis() + delay * 1000)
            }
            else -> {
                val problem = (result as? ApiResult.Http)?.problem
                LnbWarden.LOGGER.warn("LNB's API refused this device: {}", result.describe())
                state = AuthState.Refused(problem)
            }
        }
    }

    /** Called every client tick: retries an unreachable API when its backoff is over. */
    fun tick() {
        val current = state
        if (current is AuthState.Unreachable && Util.getMillis() >= current.retryAt) refresh()
    }

    /** Retry now, from the status widget. */
    fun retry() {
        failedRefreshes = 0
        refresh(showChecking = true)
    }

    /** A link request was approved: store the new token and load the player. */
    fun onLinked(newSession: Session) {
        try {
            store.save(newSession)
        } catch (e: IOException) {
            // Keep going with the token in memory: the player is linked for this game at least.
            LnbWarden.LOGGER.error("Couldn't save the LNB link", e)
        }
        session = newSession
        failedRefreshes = 0
        refreshing = null
        LnbWarden.LOGGER.info("Linked to LNB as {} ({})", newSession.name, newSession.userId)
        refresh(showChecking = true)
    }

    /** The API rejected the token (401): delete it. */
    fun onTokenRejected() {
        if (session == null) return
        forget()
        state = AuthState.TokenRejected
    }

    /** Signs this device out on the API, then forgets the token whatever the answer. */
    fun signOut() {
        val current = session ?: return
        WardenClientApi.signOut(current.token).thenAccept { result ->
            if (result !is ApiResult.Ok) LnbWarden.LOGGER.warn("Sign-out from LNB failed: {}", result.describe())
        }
        LnbWarden.LOGGER.info("Signed out from LNB ({})", current.userId)
        forget()
        state = AuthState.NotLinked
    }

    private fun forget() {
        session = null
        refreshing = null
        store.delete()
    }

    private fun extendExpiry(current: Session, expiresAt: String) {
        val newer = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return
        val stored = current.expiry
        if (stored != null && !newer.isAfter(stored)) return
        val updated = current.copy(expiresAt = expiresAt)
        try {
            store.save(updated)
            session = updated
        } catch (e: IOException) {
            LnbWarden.LOGGER.warn("Couldn't update the LNB link's expiry: {}", e.toString())
        }
    }
}
