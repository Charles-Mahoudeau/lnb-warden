package fr.bde_teknantes.lnb.warden.client.anticheat

import fr.bde_teknantes.lnb.warden.common.anticheat.PackFacts
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class PackInspectorTest {
    private fun png(alpha: Int?): ByteArray {
        val image = BufferedImage(16, 16, if (alpha == null) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, ((alpha ?: 0xFF) shl 24) or 0x808080)
        // One see-through pixel is enough.
        if (alpha != null && alpha < 0xFF) for (y in 0 until 16) for (x in 0 until 16) if (x + y > 0) image.setRGB(x, y, 0xFF808080.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun zip(dir: Path, files: Map<String, ByteArray>): Path {
        val path = dir.resolve("pack.zip")
        ZipOutputStream(path.outputStream()).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return path
    }

    private val block = "assets/minecraft/textures/block"

    @Test
    fun `a plain pack has nothing to report`(@TempDir dir: Path) {
        val pack = zip(
            dir,
            mapOf(
                "pack.mcmeta" to "{}".toByteArray(),
                "$block/stone.png" to png(null),
                "$block/diamond_ore.png" to png(0xFF),
                // Glass is meant to be see-through.
                "$block/glass.png" to png(0),
                "assets/minecraft/shaders_notes.txt" to "x".toByteArray(),
            ),
        )
        assertEquals(PackFacts(coreShaders = false, alteredTextures = false), PackInspector.inspect(pack))
    }

    @Test
    fun `see-through stone is reported`(@TempDir dir: Path) {
        assertEquals(PackFacts(false, true), PackInspector.inspect(zip(dir, mapOf("$block/stone.png" to png(0)))))
    }

    @Test
    fun `a see-through ore is reported`(@TempDir dir: Path) {
        assertEquals(PackFacts(false, true), PackInspector.inspect(zip(dir, mapOf("$block/deepslate_iron_ore.png" to png(0x80)))))
    }

    @Test
    fun `an unreadable watched texture is reported`(@TempDir dir: Path) {
        assertEquals(PackFacts(false, true), PackInspector.inspect(zip(dir, mapOf("$block/deepslate.png" to "nope".toByteArray()))))
    }

    @Test
    fun `core shaders are reported`(@TempDir dir: Path) {
        val pack = zip(dir, mapOf("assets/minecraft/shaders/core/rendertype_solid.fsh" to "void main() {}".toByteArray()))
        assertEquals(PackFacts(true, false), PackInspector.inspect(pack))
    }

    @Test
    fun `an overlay counts`(@TempDir dir: Path) {
        val pack = zip(dir, mapOf("overlay_1/$block/stone.png" to png(0), "overlay_1/assets/minecraft/shaders/post/x.json" to "{}".toByteArray()))
        assertEquals(PackFacts(true, true), PackInspector.inspect(pack))
    }

    @Test
    fun `a folder pack is read like a zip`(@TempDir dir: Path) {
        val root = dir.resolve("pack")
        root.resolve("$block/gravel.png").also { it.parent.createDirectories() }.writeBytes(png(0))
        assertEquals(PackFacts(false, true), PackInspector.inspect(root))
    }
}
