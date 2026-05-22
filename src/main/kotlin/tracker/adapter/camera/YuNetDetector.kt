package tracker.adapter.camera

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN
import tracker.domain.entity.FaceDetection
import tracker.domain.entity.FacePoint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

/**
 * EN: YuNet face detector wrapping `FaceDetectorYN` via JavaCV 1.5.10.
 *
 * The ONNX model is extracted to a temp file on construction because the native
 * ONNX Runtime requires a real filesystem path, not a classpath resource stream.
 *
 * Thread safety: [detect] and [close] are both `synchronized` on a shared lock.
 * Without this guard, closing the window on the main thread while the IO thread is
 * inside the native `FaceDetectorYN.detect()` causes a use-after-free SIGSEGV in
 * `libopencv_dnn`. The `@Volatile closed` flag prevents re-entry after destruction.
 *
 * RU: Детектор лиц YuNet через JavaCV 1.5.10.
 *
 * ONNX-модель копируется во временный файл при создании — нативный ONNX Runtime требует
 * реальный путь на диске. [detect] и [close] синхронизированы, чтобы избежать SIGSEGV
 * (use-after-free) при закрытии окна.
 *
 * @param modelResourcePath classpath path to the ONNX model / путь к модели в classpath
 * @param scoreThreshold    minimum detection confidence / минимальная уверенность
 * @param nmsThreshold      NMS IoU threshold / порог IoU для NMS
 * @param topK              maximum detections before NMS / максимум детекций до NMS
 */
class YuNetDetector(
    modelResourcePath: String = "/models/face_detection_yunet_2023mar.onnx",
    scoreThreshold: Float = 0.6f,
    nmsThreshold: Float = 0.3f,
    topK: Int = 50,
) : AutoCloseable {

    private val tempModel: Path = extractToTemp(modelResourcePath)
    private val detector: FaceDetectorYN = FaceDetectorYN.create(
        tempModel.toString(), "", Size(320, 320),
        scoreThreshold, nmsThreshold, topK, 0, 0,
    )
    private val lock = Any()
    @Volatile private var closed = false

    /**
     * EN: Runs YuNet inference on [frame] and returns all detected faces.
     * Returns an empty list if the detector has been closed or the frame is empty.
     *
     * RU: Запускает инференс YuNet на [frame]. Возвращает пустой список если детектор
     * закрыт или кадр пустой.
     *
     * @param frame OpenCV Mat in BGR format / кадр BGR
     * @return detected faces in frame-pixel coordinates / обнаруженные лица в пикселях кадра
     */
    fun detect(frame: Mat): List<FaceDetection> {
        if (closed || frame.empty()) return emptyList()
        return synchronized(lock) {
            if (closed) return emptyList()
            detector.setInputSize(Size(frame.cols(), frame.rows()))
            val faces = Mat()
            detector.detect(frame, faces)
            if (faces.rows() == 0) { faces.release(); return emptyList() }
            val indexer = faces.createIndexer<FloatIndexer>()
            val result = (0 until faces.rows()).map { row ->
                val r = row.toLong()
                FaceDetection(
                    boxX = indexer.get(r, 0L), boxY = indexer.get(r, 1L),
                    boxW = indexer.get(r, 2L), boxH = indexer.get(r, 3L),
                    rightEye  = FacePoint(indexer.get(r, 4L),  indexer.get(r, 5L)),
                    leftEye   = FacePoint(indexer.get(r, 6L),  indexer.get(r, 7L)),
                    nose      = FacePoint(indexer.get(r, 8L),  indexer.get(r, 9L)),
                    rightMouth= FacePoint(indexer.get(r, 10L), indexer.get(r, 11L)),
                    leftMouth = FacePoint(indexer.get(r, 12L), indexer.get(r, 13L)),
                    score     = indexer.get(r, 14L),
                )
            }
            indexer.release(); faces.release()
            result
        }
    }

    /**
     * EN: Releases the native detector and deletes the temp ONNX file. Idempotent.
     * RU: Освобождает нативный детектор и удаляет временный ONNX-файл. Идемпотентен.
     */
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            detector.close()
            tempModel.deleteIfExists()
        }
    }

    private fun extractToTemp(resourcePath: String): Path {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("Model not found in resources: $resourcePath")
        val tmp = Files.createTempFile("yunet", ".onnx")
        stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        return tmp
    }
}
