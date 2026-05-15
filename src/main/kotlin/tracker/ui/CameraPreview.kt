package tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.StateFlow
import tracker.app.DetectedFrame
import tracker.detect.FaceDetection
import tracker.detect.FacePoint

@Composable
fun CameraPreview(
    state: StateFlow<DetectedFrame?>,
    modifier: Modifier = Modifier,
) {
    val frame by state.collectAsState()
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
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
            FaceOverlay(f)
        }
    }
}

@Composable
private fun FaceOverlay(frame: DetectedFrame) {
    val face = frame.face ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Image нарисован c ContentScale.Fit — считаем фактическую область отрисовки внутри Canvas
        val containerAspect = size.width / size.height
        val imageAspect = frame.imageWidth.toFloat() / frame.imageHeight.toFloat()
        val drawWidth: Float
        val drawHeight: Float
        if (imageAspect > containerAspect) {
            drawWidth = size.width
            drawHeight = size.width / imageAspect
        } else {
            drawHeight = size.height
            drawWidth = size.height * imageAspect
        }
        val offsetX = (size.width - drawWidth) / 2f
        val offsetY = (size.height - drawHeight) / 2f
        val scale = drawWidth / frame.imageWidth

        fun mapX(x: Float) = offsetX + x * scale
        fun mapY(y: Float) = offsetY + y * scale

        drawRect(
            color = Color.Green,
            topLeft = Offset(mapX(face.boxX), mapY(face.boxY)),
            size = Size(face.boxW * scale, face.boxH * scale),
            style = Stroke(width = 2f),
        )

        listOf(
            face.rightEye to Color.Red,
            face.leftEye to Color.Red,
            face.nose to Color.Yellow,
            face.rightMouth to Color.Cyan,
            face.leftMouth to Color.Cyan,
        ).forEach { (p: FacePoint, c: Color) ->
            drawCircle(
                color = c,
                radius = 4f,
                center = Offset(mapX(p.x), mapY(p.y)),
            )
        }
    }
}

@Suppress("unused")
private fun debugUnused(d: FaceDetection) = d.score
