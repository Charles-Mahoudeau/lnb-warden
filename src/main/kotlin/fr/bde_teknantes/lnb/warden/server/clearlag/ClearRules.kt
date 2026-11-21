package fr.bde_teknantes.lnb.warden.server.clearlag

/** A configured id (`minecraft:diamond`) or tag (`#minecraft:logs`). */
data class IdPattern(val isTag: Boolean, val id: String) {
    companion object {
        /** Null when [text] isn't `namespace:path` or `#namespace:path`. */
        fun parse(text: String): IdPattern? {
            val trimmed = text.trim()
            val tag = trimmed.startsWith("#")
            val id = trimmed.removePrefix("#")
            val parts = id.split(':')
            if (parts.size != 2 || parts.any { it.isEmpty() }) return null
            return IdPattern(tag, id)
        }
    }
}

/** A per-chunk limit: at most [limit] entities of the type [type] in a chunk. */
data class ChunkCap(val type: String, val limit: Int) {
    companion object {
        /** Parses `namespace:path=limit`. Null when malformed or when the limit isn't positive. */
        fun parse(text: String): ChunkCap? {
            val index = text.lastIndexOf('=')
            if (index < 0) return null
            val type = text.substring(0, index).trim()
            val limit = text.substring(index + 1).trim().toIntOrNull() ?: return null
            if (limit < 1 || IdPattern.parse(type)?.isTag != false) return null
            return ChunkCap(type, limit)
        }
    }
}

object ChunkCaps {
    /**
     * Which of a chunk's entities to remove so that at most [limit] stay. [ages] are the entities' ages in ticks.
     * The oldest go first, because the newest are the ones a player just made or is looking at.
     * Returns indices into [ages].
     */
    fun excess(ages: List<Int>, limit: Int): List<Int> {
        if (ages.size <= limit) return emptyList()
        return ages.indices.sortedByDescending { ages[it] }.take(ages.size - limit)
    }
}
