package tracker.scene

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tracker.repository.SceneRepository
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
 * Все операции синхронные и предназначены для вызова только из UI-потока
 * во время выбора сцены (не в горячем цикле камеры).
 */
object SceneStore : SceneRepository {

    private val dir: Path = Path(System.getProperty("user.home"), ".lighthouse", "scenes")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * EN: Returns all valid scenes found on disk, sorted by name.
     * Files that fail to parse are silently skipped.
     *
     * RU: Возвращает все корректные сцены с диска, отсортированные по имени.
     * Файлы с ошибками парсинга молча пропускаются.
     */
    override fun listScenes(): List<SceneData> {
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .mapNotNull { runCatching { json.decodeFromString<SceneData>(it.readText()) }.getOrNull() }
            .sortedBy { it.name }
    }

    /**
     * EN: Persists [scene] to disk. Creates the scenes directory if absent.
     * Overwrites an existing file with the same sanitised name.
     *
     * RU: Сохраняет [scene] на диск. Создаёт директорию сцен при отсутствии.
     * Перезаписывает существующий файл с тем же санированным именем.
     *
     * @param scene scene to persist / сцена для сохранения
     */
    override fun save(scene: SceneData) {
        dir.createDirectories()
        dir.resolve("${sanitize(scene.name)}.json").writeText(json.encodeToString(scene))
    }

    /**
     * EN: Deletes the file corresponding to [scene]. No-op if the file does not exist.
     *
     * RU: Удаляет файл, соответствующий [scene]. Ничего не делает, если файл не существует.
     *
     * @param scene scene to delete / сцена для удаления
     */
    override fun delete(scene: SceneData) {
        dir.resolve("${sanitize(scene.name)}.json").deleteIfExists()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\w\\u0400-\\u04FF\\- ]"), "_").trim()
}
