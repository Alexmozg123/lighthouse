/**
 * EN: Application entry point. Wires together [TrackingPipeline], [SpotlightController], and
 * [CameraPreview] inside a single Compose window. Lifecycle cleanup (detector + Art-Net client)
 * is handled by [DisposableEffect] so native resources are freed when the window closes.
 *
 * RU: Точка входа приложения. Связывает [TrackingPipeline], [SpotlightController] и
 * [CameraPreview] внутри одного Compose-окна. Освобождение нативных ресурсов (детектор +
 * Art-Net клиент) выполняется в [DisposableEffect] при закрытии окна.
 */
package tracker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import tracker.app.DetectedFrame
import tracker.app.TrackingPipeline
import tracker.capture.CameraSource
import tracker.detect.YuNetDetector
import tracker.dmx.DmxFixture
import tracker.dmx.SpotlightController
import tracker.ui.CameraPreview

/**
 * EN: Compose application entry point. Creates the main window and bootstraps all subsystems:
 * - [YuNetDetector] — face detection model (ONNX, native resources).
 * - [TrackingPipeline] — continuous camera grab + detect + track flow on IO dispatcher.
 * - [SpotlightController] — translates tracked face position to Art-Net DMX output.
 * - [CameraPreview] — renders the live camera feed with face overlays; click to select target.
 *
 * The selected face ID is shared between the UI and the spotlight controller via
 * [MutableStateFlow] to avoid locking.
 *
 * RU: Точка входа Compose-приложения. Создаёт главное окно и инициализирует все подсистемы:
 * - [YuNetDetector] — модель детекции лиц (ONNX, нативные ресурсы).
 * - [TrackingPipeline] — непрерывный захват + детекция + трекинг на IO-диспетчере.
 * - [SpotlightController] — переводит позицию выбранного лица в DMX-вывод через Art-Net.
 * - [CameraPreview] — отображает живое видео с оверлеями лиц; клик выбирает цель.
 *
 * ID выбранного лица передаётся между UI и контроллером через [MutableStateFlow],
 * чтобы избежать явных блокировок.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lighthouse — face → DMX",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        val frameFlow = remember { MutableStateFlow<DetectedFrame?>(null) }
        val selectedIdFlow = remember { MutableStateFlow<Int?>(null) }
        val detector = remember { YuNetDetector() }
        val pipeline = remember { TrackingPipeline(CameraSource(deviceIndex = 0), detector) }
        val spotlight = remember {
            SpotlightController(
                fixtures = listOf(
                    DmxFixture(host = "127.0.0.1", subnet = 0, universe = 0, startChannel = 1),
                ),
            )
        }

        DisposableEffect(Unit) {
            onDispose {
                detector.close()
                spotlight.close()
            }
        }
        LaunchedEffect(Unit) {
            pipeline.frames().collect { frame ->
                frameFlow.value = frame
                val selectedId = selectedIdFlow.value
                withContext(Dispatchers.IO) {
                    spotlight.update(frame, selectedId)
                }
            }
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    state = frameFlow,
                    selectedFaceId = selectedIdFlow,
                    onFaceSelected = { selectedIdFlow.value = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
