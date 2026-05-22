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
 * YuNet face detector via JavaCV (org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN).
 * Модель ожидается в ресурсах: src/main/resources/models/face_detection_yunet_2023mar.onnx
 *
 * detect() и close() синхронизированы через lock: закрытие окна вызывает close() на
 * главном потоке пока IO-поток может быть внутри нативного FaceDetectorYN.detect(),
 * что приводит к SIGSEGV (use-after-free в libopencv_dnn).
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
