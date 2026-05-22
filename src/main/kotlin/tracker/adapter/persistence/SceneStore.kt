package tracker.adapter.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tracker.domain.entity.SceneData
import tracker.domain.repository.SceneRepository
import java.nio.file.Path
import kotlin.io.path.*

/**
 * EN: Reads and writes [SceneData] as JSON files under `~/.lighthouse/scenes/`.
 * Each scene is stored in a file named after the scene with illegal filesystem
 * characters replaced by underscores.
 *
 * All operations are synchronous and intended to be called from the UI thread
 * only during scene selection (not in the hot camera loop).
 *
 * RU: Читает и записывает [SceneData] как JSON-файлы в `~/.lighthouse/scenes/`.
 * Каждая сцена хранится в файле, названном по имени сцены; недопустимые символы
 * заменяются на подчёркивание.
 *
 * Все операции синхронные и вызываются только из UI-потока при выборе сцены.
 */
object SceneStore : SceneRepository {

    private val dir: Path = Path(System.getProperty("user.home"), ".lighthouse", "scenes")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun listScenes(): List<SceneData> {
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .mapNotNull { runCatching { json.decodeFromString<SceneData>(it.readText()) }.getOrNull() }
            .sortedBy { it.name }
    }

    override fun save(scene: SceneData) {
        dir.createDirectories()
        dir.resolve("${sanitize(scene.name)}.json").writeText(json.encodeToString(scene))
    }

    override fun delete(scene: SceneData) {
        dir.resolve("${sanitize(scene.name)}.json").deleteIfExists()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\w\\u0400-\\u04FF\\- ]"), "_").trim()
}
