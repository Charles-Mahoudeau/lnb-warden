package fr.bde_teknantes.lnb.warden.common.anticheat

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The answer to a file challenge: proves the client still answers, live, with the file set it reported.
 *
 * It can't prove the client really has those files: the server never sees them.
 */
object FileDigest {
    const val NONCE_SIZE = 32
    const val CONNECTION_ID_SIZE = 16
    const val SIZE = 32

    private val RANDOM = SecureRandom()

    fun nonce(): ByteArray = ByteArray(NONCE_SIZE).also(RANDOM::nextBytes)

    fun connectionId(): ByteArray = ByteArray(CONNECTION_ID_SIZE).also(RANDOM::nextBytes)

    /**
     * SHA-256 of the server's [nonce], the [connectionId] it gave this connection, and the sorted [sha512s] of the
     * reported files. The nonce stops an old answer from being replayed, the connection ID one from another player.
     */
    fun of(nonce: ByteArray, connectionId: ByteArray, sha512s: Collection<String>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(nonce)
        digest.update(connectionId)
        for (sha512 in sha512s.toSortedSet()) digest.update(sha512.toByteArray(Charsets.US_ASCII))
        return digest.digest()
    }
}
