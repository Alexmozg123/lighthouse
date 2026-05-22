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
import tracker.detect.FaceDetection
import tracker.detect.YuNetDetector
import tracker.ui.CameraPreview

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lighthouse — face → DMX",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        val frameFlow = remember { MutableStateFlow<DetectedFrame?>(null) }
        val selectedFaceFlow = remember { MutableStateFlow<FaceDetection?>(null) }
        val detector = remember { YuNetDetector() }
        val pipeline = remember { TrackingPipeline(CameraSource(deviceIndex = 0), detector) }

        DisposableEffect(Unit) {
            onDispose { detector.close() }
        }
        LaunchedEffect(Unit) {
            pipeline.frames().collect { frame ->
                frameFlow.value = frame
                // Re-match selected face to nearest centroid in the new frame.
                val sel = selectedFaceFlow.value
                if (sel != null) {
                    val nearest = frame.faces.minByOrNull { face ->
                        val dx = face.centerX - sel.centerX
                        val dy = face.centerY - sel.centerY
                        dx * dx + dy * dy
                    }
                    selectedFaceFlow.value = if (nearest != null) {
                        val threshold = maxOf(sel.boxW, sel.boxH) * 1.5f
                        val dx = nearest.centerX - sel.centerX
                        val dy = nearest.centerY - sel.centerY
                        if (dx * dx + dy * dy < threshold * threshold) nearest else null
                    } else null
                }
            }
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    state = frameFlow,
                    selectedFace = selectedFaceFlow,
                    onFaceSelected = { selectedFaceFlow.value = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
