/**
 * EN: Application-level state holder. Owns the camera pipeline subscription,
 * the [SpotlightController] lifecycle, and all top-level navigation state.
 *
 * The class is constructed once by the Koin container and destroyed when the
 * Compose window closes via [close]. It exposes two separate flows:
 * - [state] for infrequent navigation/calibration changes (causes recomposition of routing)
 * - [frameFlow] for every camera frame (30 fps; consumed only by [CameraPreview])
 *
 * Thread-safety: [spotlight] is written only from [loadScene] which is called from the
 * Main (UI) thread, and read from the IO-dispatched lambda inside [init]. The
 * @Volatile annotation ensures visibility across these two threads.
 *
 * RU: Хранитель состояния уровня приложения. Владеет подпиской на камерный pipeline,
 * жизненным циклом [SpotlightController] и всей верхнеуровневой навигацией.
 *
 * Класс создаётся один раз Koin-контейнером и уничтожается при закрытии
 * Compose-окна через [close]. Он предоставляет два отдельных потока:
 * - [state] для редких изменений навигации/калибровки
 * - [frameFlow] для каждого кадра камеры (30 fps; потребляется только [CameraPreview])
 *
 * Потокобезопасность: [spotlight] пишется только из [loadScene] (UI-поток) и
 * читается из IO-лямбды внутри [init]. @Volatile обеспечивает видимость.
 *
 * @param pipeline EN: camera capture + detection pipeline / RU: pipeline захвата и детекции
 * @param detector EN: YuNet face detector; owned here for lifecycle management / RU: детектор YuNet; владение для управления жизненным циклом
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
import tracker.detect.YuNetDetector
import tracker.usecase.CalibrationUseCase
import tracker.dmx.ArtNetSender
import tracker.dmx.DmxFixture
import tracker.dmx.SpotlightController
import tracker.scene.SceneData

class AppViewModel(
    private val pipeline: TrackingPipeline,
    private val detector: YuNetDetector,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _frameFlow = MutableStateFlow<DetectedFrame?>(null)
    val frameFlow: StateFlow<DetectedFrame?> = _frameFlow.asStateFlow()

    private val _selectedIdFlow = MutableStateFlow<Int?>(null)
    val selectedIdFlow: StateFlow<Int?> = _selectedIdFlow.asStateFlow()

    @Volatile private var spotlight: SpotlightController? = null

    init {
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
            CalibrationUseCase.buildMapper(cal.points)
                .onSuccess { mapperOk = true }
                .getOrNull()
        }

        val calibStatus = when {
            scene.calibration == null -> CalibrationStatus.None
            mapperOk -> CalibrationStatus.Active
            else -> CalibrationStatus.Fallback
        }

        spotlight = SpotlightController(
            sender = ArtNetSender(scene.fixtures.map { cfg ->
                DmxFixture(cfg.host, cfg.subnet, cfg.universe, cfg.startChannel)
            }),
            mapper = mapper,
        )

        _state.update { it.copy(screen = AppScreen.Tracking(scene), calibrationStatus = calibStatus) }
    }

    /**
     * EN: Saves [scene] and activates it (same as [loadScene]).
     * RU: Сохраняет [scene] и активирует её (аналог [loadScene]).
     *
     * @param scene EN: saved scene to activate / RU: сохранённая сцена для активации
     */
    fun onSceneSaved(scene: SceneData) = loadScene(scene)

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
     * EN: Navigates to the scene list screen.
     * RU: Переходит на экран списка сцен.
     */
    fun navigateToSceneManager() {
        _state.update { it.copy(screen = AppScreen.SceneManager) }
    }

    /**
     * EN: Opens the scene editor for the currently active scene.
     * Must only be called from the Tracking screen.
     *
     * RU: Открывает редактор для текущей активной сцены.
     * Должен вызываться только с экрана трекинга.
     */
    fun navigateToSceneEditor() {
        val current = (_state.value.screen as? AppScreen.Tracking)?.scene
        _state.update { it.copy(screen = AppScreen.SceneEditor(current)) }
    }

    /**
     * EN: Returns to the previous screen on editor cancellation:
     * - Tracking if the editor was opened with a scene
     * - SceneManager otherwise
     *
     * RU: Возвращается на предыдущий экран при отмене редактора:
     * - Tracking если редактор был открыт со сценой
     * - SceneManager иначе
     */
    fun navigateBack() {
        val prev = when (val s = _state.value.screen) {
            is AppScreen.SceneEditor -> if (s.scene != null) AppScreen.Tracking(s.scene) else AppScreen.SceneManager
            else -> AppScreen.SceneManager
        }
        _state.update { it.copy(screen = prev) }
    }

    /**
     * EN: Releases all resources: spotlight controller, face detector, and coroutine scope.
     * Must be called from the UI thread when the window closes.
     *
     * RU: Освобождает все ресурсы: контроллер прожектора, детектор лиц и coroutine scope.
     * Должен вызываться из UI-потока при закрытии окна.
     */
    override fun close() {
        spotlight?.close()
        detector.close()
        scope.cancel()
    }
}
