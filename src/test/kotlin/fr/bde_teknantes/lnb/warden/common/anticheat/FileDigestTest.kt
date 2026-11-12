package fr.bde_teknantes.lnb.warden.common.anticheat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FileDigestTest {
    private val nonce = ByteArray(FileDigest.NONCE_SIZE) { 1 }
    private val connection = ByteArray(FileDigest.CONNECTION_ID_SIZE) { 2 }
    private val a = "a".repeat(128)
    private val b = "b".repeat(128)

    @Test
    fun `the order of the files doesn't matter`() {
        assertEquals(FileDigest.SIZE, FileDigest.of(nonce, connection, listOf(a, b)).size)
        assertContentEquals(FileDigest.of(nonce, connection, listOf(a, b)), FileDigest.of(nonce, connection, setOf(b, a)))
    }

    @Test
    fun `the nonce, the connection and the files all count`() {
        val digest = FileDigest.of(nonce, connection, listOf(a, b))
        assertFalse(digest.contentEquals(FileDigest.of(ByteArray(FileDigest.NONCE_SIZE), connection, listOf(a, b))))
        assertFalse(digest.contentEquals(FileDigest.of(nonce, ByteArray(FileDigest.CONNECTION_ID_SIZE), listOf(a, b))))
        assertFalse(digest.contentEquals(FileDigest.of(nonce, connection, listOf(a))))
    }
}
