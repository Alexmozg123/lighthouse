package tracker.calibration

import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_calib3d.findHomography
import org.bytedeco.opencv.global.opencv_core.CV_32FC2
import org.bytedeco.opencv.opencv_core.Mat
import tracker.scene.CalibrationData

/**
 * EN: Computes and applies a projective homography from camera-pixel space to
 * normalised pan/tilt space [0, 1].
 *
 * The homography is built once from the 4 [CalibrationData.points] via
 * `opencv_calib3d.findHomography`. Subsequent [map] calls apply the 3×3 matrix
 * analytically (no extra Mat allocations):
 * ```
 * [x', y', w'] = H × [px, py, 1]
 * pan  = x' / w'
 * tilt = y' / w'
 * ```
 * Results are clamped to [0, 1].
 *
 * Thread safety: [map] is read-only after construction — safe to call from any thread.
 *
 * RU: Вычисляет и применяет проективную гомографию из пространства пикселей камеры
 * в нормализованное пространство pan/tilt [0, 1].
 *
 * Гомографія строится один раз из 4 точек [CalibrationData.points] через
 * `opencv_calib3d.findHomography`. Последующие вызовы [map] применяют матрицу 3×3
 * аналитически (без дополнительных Mat-аллокаций).
 *
 * Потокобезопасность: [map] только читает после конструктора — безопасен из любого потока.
 *
 * @param calibration calibration data with exactly 4 points / данные калибровки ровно с 4 точками
 */
class HomographyMapper(calibration: CalibrationData) {

    private val h = DoubleArray(9)

    init {
        require(calibration.points.size == 4) { "Exactly 4 calibration points required, got ${calibration.points.size}" }

        val src = Mat(4, 1, CV_32FC2)
        val dst = Mat(4, 1, CV_32FC2)
        val srcBuf = src.createBuffer<java.nio.FloatBuffer>()
        val dstBuf = dst.createBuffer<java.nio.FloatBuffer>()
        for (p in calibration.points) {
            srcBuf.put(p.cameraX); srcBuf.put(p.cameraY)
            dstBuf.put(p.pan);     dstBuf.put(p.tilt)
        }

        val H: Mat = findHomography(src, dst)
        // findHomography returns a null/empty Mat when points are collinear or degenerate.
        check(!H.isNull && !H.empty()) {
            "findHomography returned an empty result — calibration points may be collinear or too close together"
        }
        val idx = H.createIndexer<DoubleIndexer>()
        for (i in 0..8) h[i] = idx.get(i.toLong() / 3, i.toLong() % 3)
        idx.release()
        H.release(); src.release(); dst.release()
    }

    /**
     * EN: Maps a camera-space pixel to a normalised pan/tilt pair.
     * The result is clamped to [0, 1] on both axes.
     *
     * RU: Отображает пиксель камеры в нормализованную пару pan/tilt.
     * Результат обрезается до [0, 1] по обеим осям.
     *
     * @param pixelX X coordinate in the original (unscaled) camera frame
     * @param pixelY Y coordinate in the original (unscaled) camera frame
     * @return (pan, tilt) both in [0, 1]
     */
    fun map(pixelX: Float, pixelY: Float): Pair<Float, Float> {
        val x = h[0] * pixelX + h[1] * pixelY + h[2]
        val y = h[3] * pixelX + h[4] * pixelY + h[5]
        val w = h[6] * pixelX + h[7] * pixelY + h[8]
        return (x / w).toFloat().coerceIn(0f, 1f) to (y / w).toFloat().coerceIn(0f, 1f)
    }
}
