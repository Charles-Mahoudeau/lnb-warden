package fr.bde_teknantes.lnb.warden.client.anticheat

import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.anticheat.FileKind
import fr.bde_teknantes.lnb.warden.common.anticheat.ReportedFile
import fr.bde_teknantes.lnb.warden.common.network.FilesReportPayload
import net.minecraft.client.Minecraft
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.loading.LoadingModList
import net.neoforged.neoforgespi.locating.IModFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Lists what the client has loaded: its mods, the resource packs it selected and its shader pack.
 *
 * [candidates] is cheap and runs on the client thread; [hash] reads the files and runs off it.
 */
object FileScanner {
    /** A file to report, before it is read. */
    data class Candidate(
        val kind: FileKind,
        val path: Path,
        val modIds: List<String> = emptyList(),
        val name: String? = null,
        val version: String? = null,
        val loader: String? = null,
    )

    /** The game and the loader, and LNB Warden itself: nothing LNB looks up or scores. */
    private val CLIENT_MODS = setOf("minecraft", "neoforge", LnbWarden.ID)
    private const val LOADER = "neoforge"
    private const val FOLDER_PACK = "file/"

    /** Mods can't change while the game runs. */
    val mods: List<Candidate> by lazy(::findMods)

    fun candidates(): List<Candidate> = mods + resourcePacks() + listOfNotNull(shaderPack())

    /** What decides the packs [candidates] returns: when it changes, the list may have. */
    fun packsKey(): Any {
        val minecraft = Minecraft.getInstance()
        val config = shaderConfig(minecraft)
        return minecraft.resourcePackRepository.selectedIds.toList() to config?.getLastModifiedTime()?.toMillis()
    }

    fun hash(candidates: List<Candidate>): List<ReportedFile> {
        val files = LinkedHashMap<String, ReportedFile>()
        for (candidate in candidates) {
            try {
                val hashes = FileHasher.hash(candidate.path)
                val facts = if (candidate.kind == FileKind.RESOURCE_PACK) PackInspector.inspect(candidate.path) else null
                // The same file twice (a copy under another name) is reported once: the API wants unique hashes.
                files.putIfAbsent(
                    hashes.sha512,
                    ReportedFile.of(
                        candidate.kind, hashes.sha512, hashes.fingerprint, candidate.path.name, candidate.modIds,
                        candidate.name, candidate.version, candidate.loader, facts,
                    ),
                )
            } catch (e: Exception) {
                LnbWarden.LOGGER.warn("Couldn't read {} to report it to LNB's server", candidate.path, e)
            }
        }
        if (files.size > FilesReportPayload.MAX_FILES) {
            LnbWarden.LOGGER.warn("{} files are loaded: only the first {} are reported", files.size, FilesReportPayload.MAX_FILES)
        }
        return files.values.take(FilesReportPayload.MAX_FILES)
    }

    /**
     * One entry per jar the loader found by itself. A jar nested in another isn't reported: the hash of the outer
     * jar already covers it. Its mod IDs go to the outer jar, so the server knows where they come from.
     */
    private fun findMods(): List<Candidate> {
        val list = LoadingModList.get()
        val nestedIds = HashMap<IModFile, MutableList<String>>()
        for (info in list.modFiles) {
            val root = rootOf(info.file)
            if (root !== info.file) nestedIds.getOrPut(root) { mutableListOf() } += info.mods.map { it.modId }
        }

        val candidates = mutableListOf<Candidate>()
        for (info in list.modFiles) {
            val file = info.file
            if (file.discoveryAttributes.parent() != null || !isOnDisk(file.filePath)) continue
            val ids = info.mods.map { it.modId } + nestedIds[file].orEmpty()
            val mod = info.mods.firstOrNull()
            candidates += Candidate(
                kind = if (ids.any { it in CLIENT_MODS }) FileKind.CLIENT else FileKind.MOD,
                path = file.filePath,
                modIds = ids,
                name = mod?.displayName,
                version = mod?.version?.toString(),
                loader = LOADER,
            )
        }

        // Jars without a mod in them (libraries, language loaders), when the player put them with the mods.
        val modsFolder = FMLPaths.MODSDIR.get().toAbsolutePath().normalize()
        for (info in list.plugins) {
            val file = info.file
            val path = file.filePath
            if (file.discoveryAttributes.parent() != null || !isOnDisk(path)) continue
            if (path.toAbsolutePath().normalize().parent != modsFolder) continue
            candidates += Candidate(FileKind.MOD, path, loader = LOADER)
        }
        return candidates
    }

    private fun rootOf(file: IModFile): IModFile {
        var root = file
        while (true) root = root.discoveryAttributes.parent() ?: return root
    }

    /** A real file: not a jar inside another, and not a folder of classes as in a development run. */
    private fun isOnDisk(path: Path): Boolean = path.fileSystem == FileSystems.getDefault() && path.isRegularFile()

    /** The packs the player picked from their resource packs folder. Built-in, mod and server packs are left out. */
    private fun resourcePacks(): List<Candidate> {
        val minecraft = Minecraft.getInstance()
        val folder = minecraft.resourcePackDirectory
        return minecraft.resourcePackRepository.selectedIds
            .filter { it.startsWith(FOLDER_PACK) }
            .map { folder.resolve(it.removePrefix(FOLDER_PACK)) }
            .filter { it.exists() }
            .map { Candidate(FileKind.RESOURCE_PACK, it) }
    }

    /** The shader pack Iris or Oculus has on. They aren't dependencies: their settings file says which. */
    private fun shaderPack(): Candidate? {
        val minecraft = Minecraft.getInstance()
        val config = shaderConfig(minecraft) ?: return null
        val settings = Properties()
        try {
            config.inputStream().use(settings::load)
        } catch (e: Exception) {
            LnbWarden.LOGGER.warn("Couldn't read {}", config, e)
            return null
        }
        if (settings.getProperty("enableShaders", "true") != "true") return null
        val pack = settings.getProperty("shaderPack")?.takeIf { it.isNotBlank() } ?: return null
        val folder = minecraft.gameDirectory.toPath().resolve("shaderpacks").normalize()
        val path = folder.resolve(pack).normalize()
        // A name can't point outside the shader packs folder.
        if (path.parent != folder || !(path.isRegularFile() || path.isDirectory())) return null
        return Candidate(FileKind.SHADER, path)
    }

    private fun shaderConfig(minecraft: Minecraft): Path? {
        val config = minecraft.gameDirectory.toPath().resolve("config")
        return sequenceOf("iris.properties", "oculus.properties").map(config::resolve).firstOrNull(Files::isRegularFile)
    }
}
