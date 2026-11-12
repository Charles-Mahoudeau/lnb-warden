package fr.bde_teknantes.lnb.warden.client.anticheat

import fr.bde_teknantes.lnb.warden.common.anticheat.PackFacts
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * Looks inside a resource pack for the two things an x-ray pack does: replacing the game's core shaders, and
 * making see-through the blocks that hide ores.
 *
 * Paths are matched anywhere in the pack, so a pack overlay (a second `assets` folder deeper in) counts too.
 */
object PackInspector {
    private val CORE_SHADER = Regex("(^|.*/)assets/minecraft/shaders/.+")
    private val BLOCK_TEXTURE = Regex("(^|.*/)assets/minecraft/textures/block/([a-z0-9_]+)\\.png")

    /** Full, opaque blocks that the underground is made of. Ores are matched by their name. */
    private val TERRAIN = setOf(
        "stone", "deepslate", "deepslate_top", "granite", "diorite", "andesite", "tuff", "dirt", "gravel",
        "netherrack", "basalt_side", "basalt_top", "blackstone", "blackstone_top", "end_stone", "ancient_debris_side",
        "ancient_debris_top",
    )

    /** Larger than any real block texture, animated ones included. */
    private const val MAX_PIXELS = 4096 * 4096

    /** [pack] is a zip file or a folder. */
    fun inspect(pack: Path): PackFacts =
        if (pack.isDirectory()) inspectTree(pack)
        else FileSystems.newFileSystem(pack).use { zip -> inspectTree(zip.rootDirectories.first()) }

    private fun inspectTree(root: Path): PackFacts {
        var coreShaders = false
        var alteredTextures = false
        Files.walk(root).use { paths ->
            for (file in paths) {
                if (!file.isRegularFile()) continue
                val path = file.relativeTo(root).invariantSeparatorsPathString
                if (CORE_SHADER.matches(path)) coreShaders = true
                val texture = BLOCK_TEXTURE.matchEntire(path)?.groupValues?.get(2) ?: continue
                if (!alteredTextures && isWatched(texture) && isSeeThrough(file)) alteredTextures = true
            }
        }
        return PackFacts(coreShaders, alteredTextures)
    }

    private fun isWatched(texture: String): Boolean = texture in TERRAIN || texture.endsWith("_ore")

    /** Whether any pixel of the image isn't fully opaque. An image that can't be read counts: the game couldn't draw it. */
    private fun isSeeThrough(file: Path): Boolean {
        val image = try {
            file.inputStream().use(ImageIO::read)
        } catch (_: Exception) {
            null
        } ?: return true
        if (image.width.toLong() * image.height > MAX_PIXELS) return true
        if (!image.colorModel.hasAlpha()) return false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0xFF) return true
            }
        }
        return false
    }
}
