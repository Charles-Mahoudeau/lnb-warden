package fr.bde_teknantes.lnb.warden.server.anticheat

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AnticheatJsonTest {
    @Test
    fun `fields a file doesn't have are left out`() {
        val file = WardenServerApi.AnticheatFile("shader", "a".repeat(128), null, "s.zip", null, null, null, null, null)
        val json = ApiClient.GSON.toJson(WardenServerApi.AnticheatReportBody("s", listOf(file)))
        assertFalse("null" in json)
        assertFalse("fingerprint" in json)
    }

    @Test
    fun `a nested jar names its top-level file`() {
        val file = WardenServerApi.AnticheatFile("mod", "a".repeat(128), 1, "k.jar", "k", null, null, null, null, "b".repeat(128))
        assertEquals(true, "\"nestedIn\":\"${"b".repeat(128)}\"" in ApiClient.GSON.toJson(file))
    }

    @Test
    fun `the fingerprint is sent as a plain number`() {
        val file = WardenServerApi.AnticheatFile("mod", "a".repeat(128), 4294967295, "m.jar", "m", null, null, null, null)
        assertEquals(true, "\"fingerprint\":4294967295," in ApiClient.GSON.toJson(file))
    }

    @Test
    fun `the answer to a report is read`() {
        val report = ApiClient.GSON.fromJson(
            """
            {
              "reportId": "0d4c2b1e-3a77-4f5b-8c1a-6b1f0b1c2d3e",
              "mode": "observe",
              "verdict": {
                "action": "allow",
                "wouldBe": "hold",
                "reasons": [{ "code": "fileHeld", "sha512": "abc", "name": "Freecam", "action": "hold" }]
              },
              "player": { "score": 50, "band": "normal" },
              "files": [{ "sha512": "abc", "kind": "mod", "name": "Freecam", "state": "scored", "score": 60,
                          "effectiveScore": 60, "band": "middle", "status": "review", "action": "hold" }],
              "pending": 2,
              "pendingTimeoutSeconds": 60
            }
            """,
            WardenServerApi.AnticheatReport::class.java,
        )
        assertEquals("allow", report.verdict?.action)
        assertEquals("hold", report.verdict?.wouldBe)
        assertEquals(WardenServerApi.VerdictReason("fileHeld", "abc", "Freecam", "hold"), report.verdict?.reasons?.single())
        assertEquals(2, report.pending)
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(report.verdict))
    }

    @Test
    fun `the answer of an anticheat that is off is read`() {
        val report = ApiClient.GSON.fromJson(
            """{ "reportId": null, "mode": "off", "verdict": { "action": "allow", "reasons": [] }, "files": [], "pending": 0 }""",
            WardenServerApi.AnticheatReport::class.java,
        )
        assertNull(report.reportId)
        assertNull(report.player)
        assertEquals(Verdicts.Outcome.Pass, Verdicts.decide(report.verdict))
    }
}
