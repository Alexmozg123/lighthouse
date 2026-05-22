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
import tracker.detect.FaceDetection
import tracker.detect.YuNetDetector
import java.awt.image.BufferedImage

data class DetectedFrame(
    val image: ImageBitmap,
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<FaceDetection>,
)

/**
 * Захват + детекция в одном flow: Frame из grabber'а одноразовый
 * (он же буфер для следующего grab()), поэтому Mat и BufferedImage
 * получаем сразу в этом же цикле до следующего grab.
 */
class TrackingPipeline(
    private val camera: CameraSource,
    private val detector: YuNetDetector,
) {
    fun frames(): Flow<DetectedFrame> = flow {
        val grabber = camera.openGrabber()
        grabber.start()
        val biConv = Java2DFrameConverter()
        val matConv = OpenCVFrameConverter.ToMat()
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
                        faces = detection,
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
