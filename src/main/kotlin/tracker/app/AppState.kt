/**
 * EN: Application-level UI state model. Represents navigation and calibration status.
 * Changed infrequently — safe to put in a top-level StateFlow without causing
 * expensive recomposition of the camera preview.
 *
 * RU: Модель состояния UI приложения. Хранит навигацию и статус калибровки.
 * Меняется редко — безопасно держать в отдельном StateFlow, не вызывая
 * дорогую рекомпозицию превью камеры.
 */
package tracker.app

import tracker.domain.entity.SceneData

/**
 * EN: Indicates how the coordinate mapping is working for the active scene.
 * RU: Указывает, как работает маппинг координат для активной сцены.
 */
sealed interface CalibrationStatus {
    /** EN: No scene is loaded. RU: Сцена не загружена. */
    data object None : CalibrationStatus

    /** EN: Homography mapper is active and valid. RU: Гомографический маппер активен и валиден. */
    data object Active : CalibrationStatus

    /** EN: Calibration data was invalid; falling back to linear mapping. RU: Данные калибровки невалидны; используется линейный маппинг. */
    data object Fallback : CalibrationStatus
}

/**
 * EN: Represents which top-level screen is currently visible.
 * RU: Представляет, какой верхнеуровневый экран сейчас отображается.
 */
sealed interface AppScreen {
    /** EN: Scene list shown on startup. RU: Список сцен, показываемый при запуске. */
    data object SceneManager : AppScreen

    /**
     * EN: Live tracking screen with the active scene.
     * RU: Экран живого трекинга с активной сценой.
     * @param scene EN: loaded scene / RU: загруженная сцена
     */
    data class Tracking(val scene: SceneData) : AppScreen

    /**
     * EN: Scene editor. [scene] is the scene being edited; null means creating a new scene.
     * [fromManager] is true when navigation originated from [SceneManager] (not from [Tracking]);
     * used by [AppViewModel] to decide where to return after save or cancel.
     *
     * RU: Редактор сцены. [scene] — редактируемая сцена; null означает создание новой.
     * [fromManager] равен true, когда навигация пришла из [SceneManager] (не из [Tracking]);
     * [AppViewModel] использует это поле чтобы решить, куда вернуться после сохранения или отмены.
     *
     * @param scene       EN: scene to edit, or null for a new scene / RU: редактируемая сцена или null для новой
     * @param fromManager EN: true when opened from SceneManager / RU: true если открыт из SceneManager
     */
    data class SceneEditor(val scene: SceneData?, val fromManager: Boolean = false) : AppScreen
}

/**
 * EN: Immutable snapshot of all top-level application state. Updated by [AppViewModel].
 * RU: Неизменяемый снимок всего верхнеуровневого состояния приложения. Обновляется [AppViewModel].
 *
 * @param screen            EN: current navigation destination / RU: текущий экран навигации
 * @param calibrationStatus EN: coordinate mapping mode for the active scene / RU: режим маппинга координат для активной сцены
 */
data class AppState(
    val screen: AppScreen = AppScreen.SceneManager,
    val calibrationStatus: CalibrationStatus = CalibrationStatus.None,
)
