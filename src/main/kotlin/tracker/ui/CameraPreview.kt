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

/**
 * EN: Root camera preview composable. Displays the live camera feed scaled to the container
 * with `ContentScale.Fit` (letterboxing), and draws [FaceOverlay] on top.
 *
 * Tap/click handling: tap coordinates on the canvas are converted back to image-space
 * coordinates using [computeLetterbox], then compared against each face's bounding box.
 * The first face whose bbox contains the tap point triggers [onFaceSelected].
 *
 * Shows "Ожидание камеры…" while [state] is null (camera not yet open).
 *
 * RU: Корневой компосабл превью камеры. Отображает живое видео, масштабированное в контейнер
 * через `ContentScale.Fit` (letterboxing), и рисует [FaceOverlay] поверх.
 *
 * Обработка тапов: координаты тапа на канвасе переводятся обратно в координаты изображения
 * через [computeLetterbox], затем сравниваются с bbox каждого лица. Первое лицо, bbox
 * которого содержит точку тапа, вызывает [onFaceSelected].
 *
 * Пока [state] == null (камера ещё не открыта) отображает «Ожидание камеры…».
 *
 * @param state          flow of camera frames / поток кадров камеры
 * @param selectedFaceId flow of currently selected face ID / поток ID выбранного лица
 * @param onFaceSelected callback invoked when the user taps a face / колбэк при нажатии на лицо
 * @param modifier       modifier applied to the outer Box / модификатор внешнего Box
 */
@Composable
fun CameraPreview(
    state: StateFlow<DetectedFrame?>,
    selectedFaceId: StateFlow<Int?>,
    onFaceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame by state.collectAsState()
    val selectedId by selectedFaceId.collectAsState()
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
                    val hit = f.faces.find { tf ->
                        val face = tf.detection
                        imgX >= face.boxX && imgX <= face.boxX + face.boxW &&
                            imgY >= face.boxY && imgY <= face.boxY + face.boxH
                    }
                    if (hit != null) onFaceSelected(hit.id)
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
            FaceOverlay(f, selectedId)
        }
    }
}

/**
 * EN: Holds the geometric parameters of a `ContentScale.Fit` letterbox transform:
 * the x/y offset of the image within the container, and the uniform scale factor.
 * Used for both rendering (forward transform) and tap hit-testing (inverse transform).
 *
 * RU: Параметры letterbox-трансформации `ContentScale.Fit`:
 * смещение изображения внутри контейнера по x/y и единый коэффициент масштаба.
 * Используется как для рендеринга (прямое преобразование), так и для
 * hit-тестирования тапов (обратное преобразование).
 */
private data class Letterbox(val offsetX: Float, val offsetY: Float, val scale: Float)

/**
 * EN: Computes the [Letterbox] parameters that `ContentScale.Fit` would produce when
 * fitting an image of [imageW]×[imageH] into a container of [containerW]×[containerH].
 *
 * RU: Вычисляет параметры [Letterbox], которые `ContentScale.Fit` создаёт при
 * вписывании изображения [imageW]×[imageH] в контейнер [containerW]×[containerH].
 */
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

/**
 * EN: Canvas composable drawn on top of the camera image. Renders one bounding-box rectangle
 * per tracked face. The selected face is highlighted in **cyan** (4 px stroke);
 * all other faces are drawn in **green** (2 px stroke). Keypoints are not drawn here;
 * they can be added later without changing the hit-test logic in [CameraPreview].
 *
 * RU: Canvas-компосабл, рисуемый поверх изображения камеры. Отрисовывает один
 * ограничивающий прямоугольник на каждое отслеживаемое лицо. Выбранное лицо выделяется
 * **голубым** (обводка 4 px); все остальные — **зелёным** (обводка 2 px). Ключевые точки
 * здесь не рисуются; их можно добавить позже без изменения логики hit-теста в [CameraPreview].
 *
 * @param frame      current tracking snapshot / текущий снимок трекинга
 * @param selectedId ID of the face to highlight / ID лица для выделения
 */
@Composable
private fun FaceOverlay(frame: DetectedFrame, selectedId: Int?) {
    if (frame.faces.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lb = computeLetterbox(size.width, size.height, frame.imageWidth, frame.imageHeight)

        fun mapX(x: Float) = lb.offsetX + x * lb.scale
        fun mapY(y: Float) = lb.offsetY + y * lb.scale

        frame.faces.forEach { tf ->
            val face = tf.detection
            val isTarget = tf.id == selectedId
            drawRect(
                color = if (isTarget) Color.Cyan else Color.Green,
                topLeft = Offset(mapX(face.boxX), mapY(face.boxY)),
                size = Size(face.boxW * lb.scale, face.boxH * lb.scale),
                style = Stroke(width = if (isTarget) 4f else 2f),
            )
        }
    }
}
