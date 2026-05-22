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
import kotlinx.coroutines.flow.MutableStateFlow
import tracker.app.DetectedFrame
import tracker.app.TrackingPipeline
import tracker.capture.CameraSource
import tracker.detect.YuNetDetector
import tracker.ui.CameraPreview

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

        DisposableEffect(Unit) {
            onDispose { detector.close() }
        }
        LaunchedEffect(Unit) {
            pipeline.frames().collect { frame ->
                frameFlow.value = frame
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
