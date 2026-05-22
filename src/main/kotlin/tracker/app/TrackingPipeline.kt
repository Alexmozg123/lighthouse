package tracker.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import tracker.capture.CameraSource
import tracker.detect.FaceTracker
import tracker.detect.TrackedFace
import tracker.detect.YuNetDetector
import java.awt.image.BufferedImage

/**
 * EN: Snapshot emitted for every camera frame that contains at least a valid image.
 *
 * [imageWidth] and [imageHeight] are the **original** frame dimensions (before any UI scaling).
 * They are used by [tracker.ui.CameraPreview] and [tracker.dmx.SpotlightController] to
 * normalise face coordinates into [0, 1].
 *
 * RU: Снимок, эмитируемый для каждого кадра камеры, содержащего валидное изображение.
 *
 * [imageWidth] и [imageHeight] — **оригинальные** размеры кадра (до масштабирования UI).
 * Используются в [tracker.ui.CameraPreview] и [tracker.dmx.SpotlightController] для
 * нормализации координат лиц в [0, 1].
 *
 * @param image       Compose-ready bitmap for display / Compose-битмап для отображения
 * @param imageWidth  original frame width in pixels / исходная ширина кадра в пикселях
 * @param imageHeight original frame height in pixels / исходная высота кадра в пикселях
 * @param faces       tracked faces visible in this frame / отслеживаемые лица, видимые в этом кадре
 */
data class DetectedFrame(
    val image: ImageBitmap,
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<TrackedFace>,
)

/**
 * EN: Combines camera capture, face detection, and face tracking into a single
 * `Flow<DetectedFrame>` running on [Dispatchers.IO].
 *
 * **One-shot frame constraint**: each [FrameGrabber.grab] call returns a `Frame` whose
 * internal pixel buffer is overwritten by the next `grab`. Therefore both the OpenCV `Mat`
 * (needed by the detector) and the `BufferedImage` (needed by the UI) are derived from the
 * same `Frame` *within the same loop iteration*, before advancing to the next grab.
 *
 * Frame converters are created once per pipeline lifetime and closed in the `finally` block.
 * [FaceTracker] is also scoped to the pipeline and holds its track state between emissions.
 *
 * RU: Объединяет захват камеры, детекцию лиц и трекинг лиц в единый
 * `Flow<DetectedFrame>` на [Dispatchers.IO].
 *
 * **Ограничение одноразового кадра**: каждый вызов [FrameGrabber.grab] возвращает `Frame`,
 * внутренний буфер пикселей которого перезаписывается следующим `grab`. Поэтому и
 * OpenCV `Mat` (нужен детектору), и `BufferedImage` (нужен UI) получаются из одного
 * `Frame` *в рамках одной итерации цикла* — до следующего grab.
 *
 * Конвертеры кадров создаются один раз на время жизни pipeline и закрываются в
 * блоке `finally`. [FaceTracker] также привязан к pipeline и хранит состояние
 * треков между эмиссиями.
 *
 * @param camera   configured but not yet started camera source / настроенный источник камеры (не запущен)
 * @param detector face detector instance shared with the caller /
 *                 экземпляр детектора лиц, общий с вызывающей стороной
 */
class TrackingPipeline(
    private val camera: CameraSource,
    private val detector: YuNetDetector,
) {
    /**
     * EN: Returns a cold `Flow` that, upon collection, opens the grabber, loops indefinitely
     * emitting one [DetectedFrame] per camera frame, and releases all resources when the
     * collector is cancelled or throws.
     *
     * The flow is moved to [Dispatchers.IO] via `flowOn`; callers collect on any dispatcher.
     *
     * RU: Возвращает холодный `Flow`, который при сборе открывает grabber, бесконечно
     * эмитирует по одному [DetectedFrame] на кадр камеры и освобождает все ресурсы при
     * отмене коллектора или исключении.
     *
     * Flow переключается на [Dispatchers.IO] через `flowOn`; коллекторы могут работать
     * на любом диспетчере.
     */
    fun frames(): Flow<DetectedFrame> = flow {
        val grabber = camera.openGrabber()
        grabber.start()
        val biConv = Java2DFrameConverter()
        val matConv = OpenCVFrameConverter.ToMat()
        val tracker = FaceTracker()
        try {
            while (true) {
                val frame = grabber.grab() ?: continue
                if (frame.image == null) continue
                val mat = matConv.convert(frame) ?: continue
                val detection = detector.detect(mat)
                val bi: BufferedImage = biConv.convert(frame) ?: continue
                emit(
                    DetectedFrame(
                        image = bi.toComposeImageBitmap(),
                        imageWidth = bi.width,
                        imageHeight = bi.height,
                        faces = tracker.update(detection),
                    )
                )
            }
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
            biConv.close()
            matConv.close()
        }
    }.flowOn(Dispatchers.IO)
}
