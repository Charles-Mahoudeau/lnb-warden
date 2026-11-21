package fr.bde_teknantes.lnb.warden.common.changelog

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangelogTest {
    private fun unseen(json: String) = ApiClient.GSON.fromJson(json, Changelog.Unseen::class.java)

    @Test
    fun `parses the documented unseen answer`() {
        val post = unseen(
            """{"post":{"id":"0b1f","slug":"saison-4-2","title":"Saison 4.2","summary":null,"versionTag":"Saison 4.2",
            "publishedAt":"2027-01-12T18:00:00.000Z","lang":"fr","url":"https://x/y","body":"## Hi\n",
            "blocks":[{"type":"heading","level":2,"content":[{"text":"Hi"}]},{"type":"future"}]}}""",
        ).post!!
        assertEquals("Saison 4.2", post.title)
        assertNull(post.summary)
        assertEquals(2, post.blocks!!.size)
    }

    @Test
    fun `no post`() {
        assertNull(unseen("""{"post":null}""").post)
    }

    @Test
    fun `language follows the game`() {
        assertEquals("fr", Changelog.langOf("fr_fr"))
        assertEquals("fr", Changelog.langOf("fr_ca"))
        assertEquals("en", Changelog.langOf("de_de"))
        assertEquals("en", Changelog.langOf(null))
    }

    private fun inl(t: String) = listOf(Changelog.Inline(t))

    @Test
    fun `ordered lists count per depth and restart`() {
        val block = Changelog.Block(
            "list", ordered = true,
            items = listOf(
                Changelog.ListItem(0, inl("a")), Changelog.ListItem(1, inl("a1")), Changelog.ListItem(1, inl("a2")),
                Changelog.ListItem(0, inl("b")), Changelog.ListItem(1, inl("b1")),
            ),
        )
        val markers = BlockLayout.flow(listOf(block)).map { (it as BlockLayout.Flow.Text).marker }
        assertEquals(listOf("1.", "1.", "2.", "2.", "1."), markers)
    }

    @Test
    fun `bullets, tables, rules, code and unknown types`() {
        val flow = BlockLayout.flow(
            listOf(
                Changelog.Block("list", items = listOf(Changelog.ListItem(null, inl("x")))),
                Changelog.Block("table", header = listOf(inl("A"), inl("B")), rows = listOf(listOf(inl("1"), inl("2")))),
                Changelog.Block("rule"),
                Changelog.Block("code", text = "line\n\n"),
                Changelog.Block("hologram"),
            ),
        )
        assertEquals(5, flow.size)
        assertEquals("-", (flow[0] as BlockLayout.Flow.Text).marker)
        assertEquals("A | B", (flow[1] as BlockLayout.Flow.Text).runs.joinToString("") { it.text })
        assertEquals("1 | 2", (flow[2] as BlockLayout.Flow.Text).runs.joinToString("") { it.text })
        assertTrue(flow[3] is BlockLayout.Flow.Rule)
        assertEquals("line", (flow[4] as BlockLayout.Flow.Text).runs.single().text)
    }

    @Test
    fun `empty input is empty`() {
        assertTrue(BlockLayout.flow(null).isEmpty())
    }

    @Test
    fun `an image block is a picture from its PNG`() {
        val blocks = ApiClient.GSON.fromJson(
            """[{"type":"image","src":"https://x/a.webp","pngUrl":"https://x/a.png","alt":"Map","width":800,"height":450}]""",
            Array<Changelog.Block>::class.java,
        ).toList()
        assertEquals(listOf(BlockLayout.Flow.Image("https://x/a.png", "Map", 800, 450)), BlockLayout.flow(blocks))
    }

    @Test
    fun `an image without a usable PNG is its alt text`() {
        val noUrl = Changelog.Block("image", alt = "Map", width = 10, height = 10)
        val noSize = Changelog.Block("image", pngUrl = "https://x/a.png", alt = "Map")
        for (block in listOf(noUrl, noSize)) {
            val flow = BlockLayout.flow(listOf(block)).single() as BlockLayout.Flow.Text
            assertEquals("Map", flow.runs.single().text)
        }
        assertTrue(BlockLayout.flow(listOf(Changelog.Block("image"))).isEmpty())
    }
}
