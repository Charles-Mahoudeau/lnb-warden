package fr.bde_teknantes.lnb.warden.common.changelog

/**
 * The changelog as LNB's API answers it (`docs/changelog.md`). Every field LNB may leave out is nullable, because
 * Gson fills a missing one with null whatever the type says.
 */
object Changelog {
    /** A piece of text with one style, or a link when [link] is set. A line break is `\n` inside [text]. */
    data class Inline(
        val text: String?,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val code: Boolean? = null,
        val link: String? = null,
    )

    /** An item of a list. Nested items are flat, with a deeper [depth]; 0 is the top level. */
    data class ListItem(val depth: Int?, val content: List<Inline>?)

    /**
     * One block of a post's body. [type] is `heading`, `paragraph`, `quote`, `list`, `code`, `table` or `rule`; the fields
     * that go with it are set, the others are null. A type this version doesn't know is skipped.
     */
    data class Block(
        val type: String,
        val level: Int? = null,
        val content: List<Inline>? = null,
        val ordered: Boolean? = null,
        val items: List<ListItem>? = null,
        val text: String? = null,
        val header: List<List<Inline>>? = null,
        val rows: List<List<List<Inline>>>? = null,
        /** An image block: [pngUrl] is the PNG copy, [width] and [height] are its size. [src] is a WebP the game can't read. */
        val src: String? = null,
        val pngUrl: String? = null,
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    )

    /**
     * A published post, in one language: [lang] is the language of the text, which is not always the one asked for.
     * The list leaves out [body] and [blocks].
     */
    data class Post(
        val id: String,
        val slug: String?,
        val title: String,
        val summary: String?,
        val versionTag: String?,
        val publishedAt: String?,
        val lang: String?,
        val url: String?,
        val body: String? = null,
        val blocks: List<Block>? = null,
    )

    /** [next] is the cursor of the following page, or null at the end. */
    data class PostList(val posts: List<Post>?, val next: String?)

    data class Unseen(val post: Post?)

    /** The language to ask LNB for, from the game's language code such as `fr_fr`. French stays French, anything else is English. */
    fun langOf(gameLanguage: String?): String = if (gameLanguage.orEmpty().lowercase().startsWith("fr")) "fr" else "en"
}
