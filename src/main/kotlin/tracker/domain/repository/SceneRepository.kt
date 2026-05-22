package tracker.domain.repository

import tracker.domain.entity.SceneData

/**
 * EN: Persistence contract for [SceneData]. Decouples the UI from the concrete
 * storage mechanism so that the file-system implementation can be replaced or mocked.
 *
 * All operations are synchronous and intended to be called from the UI thread
 * only during user interactions (not in the hot camera loop).
 *
 * RU: Контракт персистентности для [SceneData]. Отделяет UI от конкретного механизма
 * хранения, позволяя заменить или подменить файловую реализацию.
 *
 * Все операции синхронные и предназначены для вызова только из UI-потока
 * во время пользовательских действий (не в горячем цикле камеры).
 */
interface SceneRepository {

    /**
     * EN: Returns all valid scenes sorted by name. Scenes that fail to deserialize are skipped.
     * RU: Возвращает все корректные сцены, отсортированные по имени. Битые файлы пропускаются.
     *
     * @return sorted scene list / отсортированный список сцен
     */
    fun listScenes(): List<SceneData>

    /**
     * EN: Persists [scene], overwriting any existing entry with the same name.
     * RU: Сохраняет [scene], перезаписывая существующую запись с тем же именем.
     *
     * @param scene scene to persist / сцена для сохранения
     */
    fun save(scene: SceneData)

    /**
     * EN: Removes [scene] from persistent storage. No-op if it does not exist.
     * RU: Удаляет [scene] из хранилища. Ничего не делает если запись отсутствует.
     *
     * @param scene scene to remove / сцена для удаления
     */
    fun delete(scene: SceneData)
}
