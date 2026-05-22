package tracker.detect

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

/**
 * EN: YuNet face detector wrapping `org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN`
 * via JavaCV 1.5.10.
 *
 * The ONNX model is expected at `src/main/resources/models/face_detection_yunet_2023mar.onnx`.
 * On construction it is extracted to a temp file because the native ONNX Runtime requires a
 * real filesystem path, not a classpath resource stream.
 *
 * Thread safety: [detect] and [close] are both `synchronized` on a shared lock.
 * Without this guard, closing the window calls [close] on the main thread while the IO thread
 * may still be inside the native `FaceDetectorYN.detect()`, causing a use-after-free SIGSEGV
 * in `libopencv_dnn`. The `@Volatile closed` flag prevents a second entry into the lock
 * after the detector is already destroyed.
 *
 * RU: Детектор лиц YuNet, обёртывающий `org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN`
 * через JavaCV 1.5.10.
 *
 * ONNX-модель ожидается по пути `src/main/resources/models/face_detection_yunet_2023mar.onnx`.
 * При создании копируется во временный файл, так как нативный ONNX Runtime требует
 * реальный путь на диске, а не поток из classpath.
 *
 * Потокобезопасность: [detect] и [close] синхронизированы через общий лок.
 * Без этого закрытие окна вызывает [close] на главном потоке, пока IO-поток может
 * находиться внутри нативного `FaceDetectorYN.detect()`, что приводит к SIGSEGV
 * (use-after-free в `libopencv_dnn`). Флаг `@Volatile closed` предотвращает повторный
 * вход в лок после того, как детектор уже уничтожен.
 *
 * @param modelResourcePath classpath path to the ONNX model / путь к ONNX-модели в classpath
 * @param scoreThreshold    minimum detection confidence / минимальная уверенность детекции
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
        tempModel.toString(),
        "",
        Size(320, 320),
        scoreThreshold,
        nmsThreshold,
        topK,
        /* backend_id = */ 0,
        /* target_id  = */ 0,
    )
    private val lock = Any()
    @Volatile private var closed = false

    /**
     * EN: Runs YuNet inference on [frame] and returns all detected faces.
     * Returns an empty list if the detector has been closed or the frame is empty.
     * Safe to call concurrently with [close].
     *
     * RU: Запускает инференс YuNet на [frame] и возвращает все обнаруженные лица.
     * Возвращает пустой список, если детектор закрыт или кадр пустой.
     * Безопасно вызывать одновременно с [close].
     *
     * @param frame OpenCV Mat in BGR format / кадр в формате BGR OpenCV
     * @return detected faces in frame-pixel coordinates / обнаруженные лица в пикселях кадра
     */
    fun detect(frame: Mat): List<FaceDetection> {
        if (closed || frame.empty()) return emptyList()
        return synchronized(lock) {
            if (closed) return emptyList()
            detector.setInputSize(Size(frame.cols(), frame.rows()))

            val faces = Mat()
            detector.detect(frame, faces)
            if (faces.rows() == 0) {
                faces.release()
                return emptyList()
            }

            val indexer = faces.createIndexer<FloatIndexer>()
            val result = (0 until faces.rows()).map { row ->
                val r = row.toLong()
                FaceDetection(
                    boxX = indexer.get(r, 0L),
                    boxY = indexer.get(r, 1L),
                    boxW = indexer.get(r, 2L),
                    boxH = indexer.get(r, 3L),
                    rightEye = FacePoint(indexer.get(r, 4L), indexer.get(r, 5L)),
                    leftEye = FacePoint(indexer.get(r, 6L), indexer.get(r, 7L)),
                    nose = FacePoint(indexer.get(r, 8L), indexer.get(r, 9L)),
                    rightMouth = FacePoint(indexer.get(r, 10L), indexer.get(r, 11L)),
                    leftMouth = FacePoint(indexer.get(r, 12L), indexer.get(r, 13L)),
                    score = indexer.get(r, 14L),
                )
            }
            indexer.release()
            faces.release()
            result
        }
    }

    /**
     * EN: Releases the native [FaceDetectorYN] and deletes the temp ONNX model file.
     * Idempotent. Safe to call concurrently with [detect].
     *
     * RU: Освобождает нативный [FaceDetectorYN] и удаляет временный файл ONNX-модели.
     * Идемпотентен. Безопасно вызывать одновременно с [detect].
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
