package fr.bde_teknantes.lnb.warden.server.playtime

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.server.api.WardenServerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaytimeViewTest {
    private fun playtime(json: String) = ApiClient.GSON.fromJson(json, WardenServerApi.Playtime::class.java)

    private val counting = playtime(
        """{"enforced":true,"limited":true,"counting":true,"remainingSeconds":120,"limitSeconds":36000,
            "windowEndsAt":"2026-11-20T15:00:00.000Z","nextWindowStartsAt":null,"grace":false,"graceEndsAt":null}"""
    )

    @Test
    fun `a countdown goes down from the time the state came`() {
        val view = PlaytimeView(counting, 1_000)
        assertTrue(view.isCounting)
        assertEquals(120, view.remainingSeconds(1_000))
        assertEquals(90, view.remainingSeconds(31_000))
        assertEquals(0, view.remainingSeconds(500_000))
        assertEquals(121_000, view.deadlineMs)
    }

    @Test
    fun `nothing counts down outside the windows`() {
        val view = PlaytimeView(counting.copy(counting = false), 0)
        assertFalse(view.isCounting)
        assertNull(view.deadlineMs)
        assertEquals(120, view.remainingSeconds(90_000))
    }

    @Test
    fun `a player in grace has no countdown`() {
        val view = PlaytimeView(counting.copy(grace = true, remainingSeconds = 0), 0)
        assertTrue(view.isGrace)
        assertFalse(view.isCounting)
        assertNull(view.deadlineMs)
    }

    @Test
    fun `nothing applies when LNB only observes or the player is exempt`() {
        assertFalse(PlaytimeView(counting.copy(enforced = false), 0).isLimited)
        assertFalse(PlaytimeView(counting.copy(limited = false), 0).isCounting)
    }

    @Test
    fun `the denial of a login is read`() {
        val denied = playtime("""{"windowEndsAt":"2026-11-20T15:00:00.000Z","resetsAt":"2026-11-22T23:00:00.000Z"}""")
        assertEquals("2026-11-20T15:00:00.000Z", denied.windowEndsAt)
        assertNull(denied.enforced)
    }

    @Test
    fun `dates are shown in Paris time`() {
        assertEquals("2026-11-20 16:00 CET", PlaytimeView.formatDate("2026-11-20T15:00:00.000Z"))
        assertNull(PlaytimeView.formatDate(null))
    }

    @Test
    fun `the heartbeat answer is read`() {
        val result = ApiClient.GSON.fromJson(
            """{"unknown":[],"playtime":{"s1":{"enforced":false}}}""",
            WardenServerApi.HeartbeatResult::class.java,
        )
        assertEquals(false, result.playtime!!["s1"]!!.enforced)
    }
}
