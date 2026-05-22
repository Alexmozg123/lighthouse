/**
 * EN: Application entry point. Wires together [TrackingPipeline], [SpotlightController], and
 * [CameraPreview] inside a single Compose window. Shows [SceneManagerScreen] on startup so
 * the user can load or create a scene before tracking begins.
 *
 * The [HomographyMapper] is (re)created whenever a new scene is loaded. If the scene has no
 * calibration data, [SpotlightController] falls back to linear pixel→pan/tilt mapping.
 *
 * RU: Точка входа приложения. Связывает [TrackingPipeline], [SpotlightController] и
 * [CameraPreview] внутри одного Compose-окна. При запуске показывает [SceneManagerScreen],
 * чтобы пользователь мог загрузить или создать сцену до начала трекинга.
 *
 * [HomographyMapper] пересоздаётся при каждой загрузке новой сцены. Если у сцены нет
 * данных калибровки, [SpotlightController] использует линейный маппинг пиксель→pan/tilt.
 */
package tracker

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import tracker.app.DetectedFrame
import tracker.app.TrackingPipeline
import tracker.calibration.HomographyMapper
import tracker.capture.CameraSource
import tracker.detect.YuNetDetector
import tracker.dmx.DmxFixture
import tracker.dmx.SpotlightController
import tracker.scene.SceneData
import tracker.ui.CameraPreview
import tracker.ui.SceneEditorScreen
import tracker.ui.SceneManagerScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lighthouse — face → DMX",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        val frameFlow = remember { MutableStateFlow<DetectedFrame?>(null) }
        val selectedIdFlow = remember { MutableStateFlow<Int?>(null) }
        val detector = remember { YuNetDetector() }
        val pipeline = remember { TrackingPipeline(CameraSource(deviceIndex = 0), detector) }

        var activeScene by remember { mutableStateOf<SceneData?>(null) }
        var showSceneManager by remember { mutableStateOf(true) }
        var showSceneEditor by remember { mutableStateOf(false) }

        // SpotlightController holds an ArtNetClient — recreate and close it when the scene changes.
        var spotlight by remember { mutableStateOf<SpotlightController?>(null) }
        // true = homography active, false = fell back to linear (bad calibration), null = no scene
        var calibrationActive by remember { mutableStateOf<Boolean?>(null) }
        DisposableEffect(activeScene) {
            val scene = activeScene
            val controller = if (scene != null) {
                var mapperOk = false
                val mapper = scene.calibration?.let { cal ->
                    runCatching { HomographyMapper(cal) }.getOrElse { null }
                        ?.also { mapperOk = true }
                }
                calibrationActive = if (scene.calibration != null) mapperOk else null
                SpotlightController(
                    fixtures = scene.fixtures.map { DmxFixture(it.host, it.subnet, it.universe, it.startChannel) },
                    mapper = mapper,
                )
            } else { calibrationActive = null; null }
            spotlight = controller
            onDispose { controller?.close() }
        }

        DisposableEffect(Unit) {
            onDispose { detector.close() }
        }

        LaunchedEffect(Unit) {
            pipeline.frames().collect { frame ->
                frameFlow.value = frame
                val sp = spotlight ?: return@collect
                val selectedId = selectedIdFlow.value
                withContext(Dispatchers.IO) { sp.update(frame, selectedId) }
            }
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                when {
                    showSceneManager -> SceneManagerScreen(
                        frameFlow = frameFlow,
                        onSceneSelected = { scene: SceneData ->
                            activeScene = scene
                            showSceneManager = false
                        },
                    )
                    showSceneEditor -> SceneEditorScreen(
                        initial = activeScene,
                        frameFlow = frameFlow,
                        onSaved = { scene: SceneData ->
                            activeScene = scene
                            showSceneEditor = false
                        },
                        onCancelled = { showSceneEditor = false },
                    )
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        CameraPreview(
                            state = frameFlow,
                            selectedFaceId = selectedIdFlow,
                            onFaceSelected = { selectedIdFlow.value = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Floating toolbar: scene name + calibration badge + navigation
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            activeScene?.let {
                                Text(
                                    it.name,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.caption,
                                )
                            }
                            when (calibrationActive) {
                                true -> Text(
                                    "✓ Калибровка",
                                    color = Color(0xFF81C784),
                                    style = MaterialTheme.typography.caption,
                                )
                                false -> Text(
                                    "⚠ Линейный режим",
                                    color = Color(0xFFFFB74D),
                                    style = MaterialTheme.typography.caption,
                                )
                                null -> {}
                            }
                            Button(
                                onClick = { showSceneEditor = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) { Text("Изменить") }
                            Button(
                                onClick = { showSceneManager = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) { Text("Сцены") }
                        }
                    }
                }
            }
        }
    }
}
