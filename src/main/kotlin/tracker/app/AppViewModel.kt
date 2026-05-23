/**
 * EN: Application-level state holder. Owns the camera pipeline subscription,
 * the [SpotlightController] lifecycle, scene persistence, and all top-level navigation state.
 *
 * The class is constructed once by the Koin container and destroyed when the
 * Compose window closes via [close]. It exposes two separate flows:
 * - [state] for infrequent navigation/calibration changes (causes recomposition of routing)
 * - [frameFlow] for every camera frame (30 fps; consumed only by [tracker.ui.CameraPreview])
 *
 * Thread-safety: [spotlight] is written only from [loadScene] which is called from the
 * Main (UI) thread, and read from the IO-dispatched lambda inside [init]. The
 * @Volatile annotation ensures visibility across these two threads.
 *
 * RU: Хранитель состояния уровня приложения. Владеет подпиской на камерный pipeline,
 * жизненным циклом [SpotlightController], персистентностью сцен и всей верхнеуровневой навигацией.
 *
 * Класс создаётся один раз Koin-контейнером и уничтожается при закрытии
 * Compose-окна через [close]. Он предоставляет два отдельных потока:
 * - [state] для редких изменений навигации/калибровки
 * - [frameFlow] для каждого кадра камеры (30 fps; потребляется только [tracker.ui.CameraPreview])
 *
 * Потокобезопасность: [spotlight] пишется только из [loadScene] (UI-поток) и
 * читается из IO-лямбды внутри [init]. @Volatile обеспечивает видимость.
 *
 * @param pipeline EN: camera capture + detection pipeline; owned for lifecycle / RU: pipeline захвата и детекции; владение для жизненного цикла
 * @param repo     EN: scene persistence; read on init and after every save/delete / RU: персистентность сцен; читается при запуске и после сохранения/удаления
 */
package tracker.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tracker.domain.entity.CalibrationPoint
import tracker.domain.entity.DmxFixture
import tracker.domain.entity.SceneData
import tracker.domain.repository.SceneRepository
import tracker.domain.usecase.CalibrationUseCase
import tracker.domain.usecase.DmxSenderFactory
import tracker.domain.usecase.MapperFactory

class AppViewModel(
    private val pipeline: TrackingPipeline,
    private val repo: SceneRepository,
    private val mapperFactory: MapperFactory,
    private val senderFactory: DmxSenderFactory,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _frameFlow = MutableStateFlow<DetectedFrame?>(null)
    val frameFlow: StateFlow<DetectedFrame?> = _frameFlow.asStateFlow()

    private val _selectedIdFlow = MutableStateFlow<Int?>(null)
    val selectedIdFlow: StateFlow<Int?> = _selectedIdFlow.asStateFlow()

    private val _scenes = MutableStateFlow<List<SceneData>>(emptyList())
    val scenes: StateFlow<List<SceneData>> = _scenes.asStateFlow()

    @Volatile private var spotlight: SpotlightController? = null

    init {
        _scenes.value = repo.listScenes()
        scope.launch {
            pipeline.frames().collect { frame ->
                _frameFlow.value = frame
                val sp = spotlight ?: return@collect
                val selectedId = _selectedIdFlow.value
                withContext(Dispatchers.IO) { sp.update(frame, selectedId) }
            }
        }
    }

    /**
     * EN: Loads [scene], (re)creates the [SpotlightController] with optional homography mapping,
     * and navigates to the Tracking screen. Safe to call from the UI thread.
     *
     * RU: Загружает [scene], (пере)создаёт [SpotlightController] с опциональным гомографическим
     * маппингом и переходит на экран трекинга. Безопасно вызывать из UI-потока.
     *
     * @param scene EN: scene to activate / RU: сцена для активации
     */
    fun loadScene(scene: SceneData) {
        spotlight?.close()
        spotlight = null

        var mapperOk = false
        val mapper = scene.calibration?.let { cal ->
            CalibrationUseCase.buildMapper(cal.points, mapperFactory)
                .onSuccess { mapperOk = true }
                .getOrNull()
        }

        val calibStatus = when {
            scene.calibration == null -> CalibrationStatus.None
            mapperOk -> CalibrationStatus.Active
            else -> CalibrationStatus.Fallback
        }

        spotlight = SpotlightController(
            sender = senderFactory.create(scene.fixtures.map { cfg ->
                DmxFixture(cfg.host, cfg.subnet, cfg.universe, cfg.startChannel)
            }),
            mapper = mapper,
        )

        _state.update { it.copy(screen = AppScreen.Tracking(scene), calibrationStatus = calibStatus) }
    }

    /**
     * EN: Persists [scene], refreshes the scene list, then navigates based on where the editor
     * was opened from: returns to [AppScreen.SceneManager] if opened from there, or activates
     * the scene via [loadScene] if opened from the Tracking screen.
     *
     * RU: Сохраняет [scene], обновляет список сцен, затем навигирует в зависимости от того,
     * откуда был открыт редактор: возвращается в [AppScreen.SceneManager] если оттуда, или
     * активирует сцену через [loadScene] если из экрана трекинга.
     *
     * @param scene EN: saved scene / RU: сохранённая сцена
     */
    fun onSceneSaved(scene: SceneData) {
        repo.save(scene)
        _scenes.value = repo.listScenes()
        val fromManager = (_state.value.screen as? AppScreen.SceneEditor)?.fromManager ?: false
        if (fromManager) {
            _state.update { it.copy(screen = AppScreen.SceneManager) }
        } else {
            loadScene(scene)
        }
    }

    /**
     * EN: Deletes [scene] from persistence and refreshes the scene list.
     * RU: Удаляет [scene] из хранилища и обновляет список сцен.
     *
     * @param scene EN: scene to delete / RU: сцена для удаления
     */
    fun deleteScene(scene: SceneData) {
        repo.delete(scene)
        _scenes.value = repo.listScenes()
    }

    /**
     * EN: Sets the face ID to follow. Pass null to blackout all fixtures.
     * RU: Устанавливает ID лица для слежения. null — блэкаут всех фикстур.
     *
     * @param id EN: face ID or null / RU: ID лица или null
     */
    fun selectFace(id: Int?) {
        _selectedIdFlow.value = id
    }

    /**
     * EN: Validates [points] for the calibration wizard without persisting anything.
     * Delegates to [CalibrationUseCase.buildMapper] and discards the mapper if successful.
     * Called by [tracker.ui.SceneEditorScreen] before the user saves a scene.
     *
     * RU: Валидирует [points] для визарда калибровки без сохранения.
     * Делегирует [CalibrationUseCase.buildMapper] и отбрасывает маппер при успехе.
     * Вызывается [tracker.ui.SceneEditorScreen] перед сохранением сцены.
     *
     * @param points calibration points to validate / точки калибровки для проверки
     * @return [Result.success] if geometry is valid, [Result.failure] otherwise /
     *         [Result.success] если геометрия корректна, [Result.failure] иначе
     */
    fun validateCalibration(points: List<CalibrationPoint>): Result<Unit> =
        CalibrationUseCase.buildMapper(points, mapperFactory).map { }

    /**
     * EN: Navigates to the scene list screen.
     * RU: Переходит на экран списка сцен.
     */
    fun navigateToSceneManager() {
        _state.update { it.copy(screen = AppScreen.SceneManager) }
    }

    /**
     * EN: Opens the editor to create a new scene. Navigation origin is marked as [AppScreen.SceneManager].
     * RU: Открывает редактор для создания новой сцены. Источник навигации помечается как [AppScreen.SceneManager].
     */
    fun navigateToNewSceneEditor() {
        _state.update { it.copy(screen = AppScreen.SceneEditor(scene = null, fromManager = true)) }
    }

    /**
     * EN: Opens the editor for [scene] from the scene manager.
     * RU: Открывает редактор для [scene] из менеджера сцен.
     *
     * @param scene EN: scene to edit / RU: сцена для редактирования
     */
    fun navigateToSceneEditorFor(scene: SceneData) {
        _state.update { it.copy(screen = AppScreen.SceneEditor(scene = scene, fromManager = true)) }
    }

    /**
     * EN: Opens the editor for the currently active scene from the Tracking screen.
     * Must only be called when the current screen is [AppScreen.Tracking].
     *
     * RU: Открывает редактор для текущей активной сцены из экрана трекинга.
     * Должен вызываться только когда текущий экран — [AppScreen.Tracking].
     */
    fun navigateToSceneEditor() {
        val current = (_state.value.screen as? AppScreen.Tracking)?.scene
        _state.update { it.copy(screen = AppScreen.SceneEditor(scene = current, fromManager = false)) }
    }

    /**
     * EN: Returns to the previous screen on editor cancellation:
     * - [AppScreen.SceneManager] if the editor was opened from there
     * - [AppScreen.Tracking] with the original scene if opened from the tracking screen
     *
     * RU: Возвращается на предыдущий экран при отмене редактора:
     * - [AppScreen.SceneManager] если редактор был открыт оттуда
     * - [AppScreen.Tracking] с оригинальной сценой если из экрана трекинга
     */
    fun navigateBack() {
        val prev = when (val s = _state.value.screen) {
            is AppScreen.SceneEditor -> if (s.fromManager) {
                AppScreen.SceneManager
            } else {
                s.scene?.let { AppScreen.Tracking(it) } ?: AppScreen.SceneManager
            }
            else -> AppScreen.SceneManager
        }
        _state.update { it.copy(screen = prev) }
    }

    /**
     * EN: Releases all resources: spotlight controller, camera pipeline (and its detector),
     * and coroutine scope. Must be called from the UI thread when the window closes.
     *
     * RU: Освобождает все ресурсы: контроллер прожектора, камерный pipeline (и его детектор),
     * и coroutine scope. Должен вызываться из UI-потока при закрытии окна.
     */
    override fun close() {
        spotlight?.close()
        scope.cancel()
        pipeline.close()
    }
}
