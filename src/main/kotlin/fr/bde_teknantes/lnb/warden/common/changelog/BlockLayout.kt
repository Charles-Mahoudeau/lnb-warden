package fr.bde_teknantes.lnb.warden.common.changelog

/**
 * A post's blocks as a flat list of things to draw, free of any Minecraft class so it can be tested. The screen wraps
 * and styles them.
 */
object BlockLayout {
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    enum class Kind { HEADING, PARAGRAPH, QUOTE, LIST, CODE, TABLE_HEADER, TABLE_ROW }

    sealed interface Flow {
        /**
         * [level] is the heading's level. A list item has its [marker] ("-" or "2.") and its nesting [indent], and wraps
         * under the text rather than under the marker.
         */
        data class Text(
            val kind: Kind,
            val runs: List<Run>,
            val level: Int = 0,
            val marker: String? = null,
            val indent: Int = 0,
        ) : Flow

        /** A picture, from its PNG copy ([url]) at that copy's size. [alt] stands in while it loads or when it can't. */
        data class Image(val url: String, val alt: String, val width: Int, val height: Int) : Flow

        data object Rule : Flow
    }

    /** A table cell separator: one line per row, since the game has no room for columns. */
    private const val CELL_SEPARATOR = " | "

    fun flow(blocks: List<Changelog.Block>?): List<Flow> {
        val out = ArrayList<Flow>()
        for (block in blocks.orEmpty()) {
            when (block.type) {
                "heading" -> text(out, Kind.HEADING, runs(block.content), level = block.level ?: 2)
                "paragraph" -> text(out, Kind.PARAGRAPH, runs(block.content))
                "quote" -> text(out, Kind.QUOTE, runs(block.content))
                "code" -> block.text?.takeIf { it.isNotEmpty() }?.let { text(out, Kind.CODE, listOf(Run(it.trimEnd('\n'), code = true))) }
                "rule" -> out += Flow.Rule
                "image" -> image(out, block)
                "list" -> list(out, block)
                "table" -> table(out, block)
                // A type from a newer site: skip it rather than show nothing at all.
            }
        }
        return out
    }

    private fun text(out: MutableList<Flow>, kind: Kind, runs: List<Run>, level: Int = 0, marker: String? = null, indent: Int = 0) {
        if (runs.isEmpty() && marker == null) return
        out += Flow.Text(kind, runs, level, marker, indent)
    }

    private fun runs(inlines: List<Changelog.Inline>?): List<Run> =
        inlines.orEmpty().mapNotNull { inline ->
            val text = inline.text?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Run(text, inline.bold == true, inline.italic == true, inline.code == true, inline.link?.takeIf { it.isNotBlank() })
        }

    /** Without a usable PNG the picture is its alt text, as the site does inside lists and tables. */
    private fun image(out: MutableList<Flow>, block: Changelog.Block) {
        val url = block.pngUrl
        val width = block.width ?: 0
        val height = block.height ?: 0
        if (url.isNullOrBlank() || width <= 0 || height <= 0) {
            block.alt?.takeIf { it.isNotBlank() }?.let { text(out, Kind.PARAGRAPH, listOf(Run(it))) }
            return
        }
        out += Flow.Image(url, block.alt.orEmpty(), width, height)
    }

    /** The number of each depth is counted up, and starts again once the list comes back to a shallower one. */
    private fun list(out: MutableList<Flow>, block: Changelog.Block) {
        val ordered = block.ordered == true
        val counters = IntArray(MAX_DEPTH + 1)
        for (item in block.items.orEmpty()) {
            val depth = (item.depth ?: 0).coerceIn(0, MAX_DEPTH)
            counters.fill(0, depth + 1, counters.size)
            counters[depth]++
            val marker = if (ordered) "${counters[depth]}." else "-"
            text(out, Kind.LIST, runs(item.content), marker = marker, indent = depth)
        }
    }

    private fun table(out: MutableList<Flow>, block: Changelog.Block) {
        block.header?.takeIf { it.isNotEmpty() }?.let { text(out, Kind.TABLE_HEADER, row(it)) }
        for (cells in block.rows.orEmpty()) text(out, Kind.TABLE_ROW, row(cells))
    }

    private fun row(cells: List<List<Changelog.Inline>>): List<Run> = buildList {
        cells.forEachIndexed { index, cell ->
            if (index > 0) add(Run(CELL_SEPARATOR))
            addAll(runs(cell))
        }
    }

    private const val MAX_DEPTH = 5
}
