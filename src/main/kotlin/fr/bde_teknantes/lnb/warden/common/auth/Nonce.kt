package fr.bde_teknantes.lnb.warden.common.auth

import fr.bde_teknantes.lnb.warden.common.network.AuthChallengePayload
import java.security.SecureRandom
import java.util.Base64

/** The per-connection nonce a join ticket is bound to. */
object Nonce {
    private val RANDOM = SecureRandom()

    fun generate(): ByteArray = ByteArray(AuthChallengePayload.NONCE_SIZE).also(RANDOM::nextBytes)

    /** As LNB's API expects it: base64url, without padding. */
    fun encode(nonce: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
}
