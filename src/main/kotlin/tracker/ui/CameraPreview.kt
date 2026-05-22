package tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.StateFlow
import tracker.app.DetectedFrame
import tracker.detect.FaceDetection

@Composable
fun CameraPreview(
    state: StateFlow<DetectedFrame?>,
    selectedFace: StateFlow<FaceDetection?>,
    onFaceSelected: (FaceDetection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame by state.collectAsState()
    val selected by selectedFace.collectAsState()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val frameRef = rememberUpdatedState(frame)

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = frameRef.value ?: return@detectTapGestures
                    if (f.faces.isEmpty()) return@detectTapGestures
                    val w = containerSize.width.toFloat()
                    val h = containerSize.height.toFloat()
                    if (w == 0f || h == 0f) return@detectTapGestures
                    val letterbox = computeLetterbox(w, h, f.imageWidth, f.imageHeight)
                    val imgX = (offset.x - letterbox.offsetX) / letterbox.scale
                    val imgY = (offset.y - letterbox.offsetY) / letterbox.scale
                    val hit = f.faces.find { face ->
                        imgX >= face.boxX && imgX <= face.boxX + face.boxW &&
                            imgY >= face.boxY && imgY <= face.boxY + face.boxH
                    }
                    if (hit != null) onFaceSelected(hit)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val f = frame
        if (f == null) {
            Text("Ожидание камеры…", color = Color.White)
        } else {
            Image(
                bitmap = f.image,
                contentDescription = "camera",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Low,
            )
            FaceOverlay(f, selected)
        }
    }
}

private data class Letterbox(val offsetX: Float, val offsetY: Float, val scale: Float)

private fun computeLetterbox(containerW: Float, containerH: Float, imageW: Int, imageH: Int): Letterbox {
    val imageAspect = imageW.toFloat() / imageH.toFloat()
    val containerAspect = containerW / containerH
    val drawWidth: Float
    val drawHeight: Float
    if (imageAspect > containerAspect) {
        drawWidth = containerW
        drawHeight = containerW / imageAspect
    } else {
        drawHeight = containerH
        drawWidth = containerH * imageAspect
    }
    return Letterbox(
        offsetX = (containerW - drawWidth) / 2f,
        offsetY = (containerH - drawHeight) / 2f,
        scale = drawWidth / imageW,
    )
}

@Composable
private fun FaceOverlay(frame: DetectedFrame, selectedFace: FaceDetection?) {
    if (frame.faces.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lb = computeLetterbox(size.width, size.height, frame.imageWidth, frame.imageHeight)

        fun mapX(x: Float) = lb.offsetX + x * lb.scale
        fun mapY(y: Float) = lb.offsetY + y * lb.scale

        frame.faces.forEach { face ->
            val isTarget = face == selectedFace
            drawRect(
                color = if (isTarget) Color.Cyan else Color.Green,
                topLeft = Offset(mapX(face.boxX), mapY(face.boxY)),
                size = Size(face.boxW * lb.scale, face.boxH * lb.scale),
                style = Stroke(width = if (isTarget) 4f else 2f),
            )
        }
    }
}
