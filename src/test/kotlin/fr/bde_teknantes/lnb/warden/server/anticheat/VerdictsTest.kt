package fr.bde_teknantes.lnb.warden.server.anticheat

import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi.Verdict
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi.VerdictReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerdictsTest {
    private val a = "a".repeat(128)
    private val b = "b".repeat(128)

    private fun remove(verdict: Verdict) = assertIs<Verdicts.Outcome.Remove>(Verdicts.decide(verdict))

    @Test
    fun `allow, flag and no verdict let the player be`() {
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(null))
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(Verdict("allow", "hold", emptyList())))
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(Verdict("flag", null, null)))
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(Verdict("somethingNew", null, null)))
    }

    @Test
    fun `a warning names the packs behind it`() {
        val verdict = Verdict(
            "warn", null,
            listOf(VerdictReason("packWarning", a, "Faithless", "warn"), VerdictReason("fileFlagged", b, "Sodium", "flag")),
        )
        assertEquals(Verdicts.Outcome.Warn(listOf("Faithless"), setOf(a)), Verdicts.decide(verdict))
    }

    @Test
    fun `a refusal names the files and keeps the first for the record`() {
        val outcome = remove(
            Verdict(
                "refuse", null,
                listOf(
                    VerdictReason("fileRefused", a, "Freecam", "refuse"),
                    VerdictReason("fileRefused", b, "XRay", "refuse"),
                    VerdictReason("fileHeld", null, "Other", "hold"),
                ),
            )
        )
        assertEquals("refuse", outcome.action)
        assertEquals("fileRefused", outcome.code)
        assertEquals(a, outcome.sha512)
        assertEquals(DisconnectReasons.ANTICHEAT_REFUSED, DisconnectReasons.keyOf(outcome.message))
        assertEquals("These files aren't allowed on LNB's server:\nFreecam, XRay\nRemove them to play.", outcome.message.string)
    }

    @Test
    fun `a hold is about files or about the account`() {
        val files = remove(Verdict("hold", null, listOf(VerdictReason("fileHeld", a, "Freecam", "hold"))))
        assertEquals(DisconnectReasons.ANTICHEAT_HELD, DisconnectReasons.keyOf(files.message))

        val account = remove(Verdict("hold", null, listOf(VerdictReason("manualHold", null, null, "hold"))))
        assertEquals("manualHold", account.code)
        assertEquals(DisconnectReasons.ANTICHEAT_ACCOUNT_HELD, DisconnectReasons.keyOf(account.message))
    }

    @Test
    fun `a kick is about banned files or a player nobody trusts`() {
        val files = remove(Verdict("kick", null, listOf(VerdictReason("fileBanned", a, "XRay Pack", "kick"))))
        assertEquals(DisconnectReasons.ANTICHEAT_BANNED, DisconnectReasons.keyOf(files.message))

        val player = remove(Verdict("kick", null, listOf(VerdictReason("playerZero", null, null, "kick"))))
        assertEquals(DisconnectReasons.ANTICHEAT_UNTRUSTED, DisconnectReasons.keyOf(player.message))
    }
}
