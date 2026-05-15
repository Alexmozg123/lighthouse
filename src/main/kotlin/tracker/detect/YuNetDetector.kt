package tracker.detect

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

/**
 * YuNet face detector via JavaCV (org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN).
 *
 * Возвращает только самое крупное лицо (по площади bbox).
 * Модель ожидается в ресурсах: src/main/resources/models/face_detection_yunet_2023mar.onnx
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

    fun detect(frame: Mat): FaceDetection? {
        if (frame.empty()) return null
        detector.setInputSize(Size(frame.cols(), frame.rows()))

        val faces = Mat()
        detector.detect(frame, faces)
        if (faces.rows() == 0) {
            faces.release()
            return null
        }

        val indexer = faces.createIndexer<FloatIndexer>()
        var bestRow = 0
        var bestArea = -1f
        for (r in 0 until faces.rows()) {
            val w = indexer.get(r.toLong(), 2L)
            val h = indexer.get(r.toLong(), 3L)
            val area = w * h
            if (area > bestArea) {
                bestArea = area
                bestRow = r
            }
        }
        val r = bestRow.toLong()
        val det = FaceDetection(
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
        indexer.release()
        faces.release()
        return det
    }

    override fun close() {
        detector.close()
        tempModel.deleteIfExists()
    }

    private fun extractToTemp(resourcePath: String): Path {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("Model not found in resources: $resourcePath")
        val tmp = Files.createTempFile("yunet", ".onnx")
        stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        return tmp
    }

    @Suppress("unused")
    private fun ensureType3(mat: Mat): Mat =
        if (mat.type() == CV_8UC3) mat else mat.also { /* YuNet ждёт BGR uint8 */ }
}
