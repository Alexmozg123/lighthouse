package tracker.adapter.calibration

import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_calib3d.findHomography
import org.bytedeco.opencv.global.opencv_core.CV_32FC2
import org.bytedeco.opencv.opencv_core.Mat
import tracker.domain.entity.CalibrationData
import tracker.domain.usecase.PositionMapper

/**
 * EN: Computes and applies a projective homography from camera-pixel space to
 * normalised pan/tilt space [0, 1].
 *
 * Built once from 4 [CalibrationData.points] via `opencv_calib3d.findHomography`.
 * Subsequent [map] calls apply the 3×3 matrix analytically (no extra Mat allocations):
 * ```
 * [x', y', w'] = H × [px, py, 1]
 * pan = x'/w',  tilt = y'/w'
 * ```
 * Results are clamped to [0, 1]. Thread-safe after construction.
 *
 * RU: Вычисляет и применяет проективную гомографию из пространства пикселей камеры
 * в нормализованное пространство pan/tilt [0, 1].
 *
 * Строится один раз из 4 точек [CalibrationData.points] через `findHomography`.
 * Вызовы [map] применяют матрицу аналитически. Потокобезопасен после конструктора.
 *
 * @param calibration calibration data with exactly 4 points / данные калибровки (ровно 4 точки)
 */
class HomographyMapper(calibration: CalibrationData) : PositionMapper {

    private val h = DoubleArray(9)

    init {
        require(calibration.points.size == 4) {
            "Exactly 4 calibration points required, got ${calibration.points.size}"
        }
        val src = Mat(4, 1, CV_32FC2)
        val dst = Mat(4, 1, CV_32FC2)
        val srcBuf = src.createBuffer<java.nio.FloatBuffer>()
        val dstBuf = dst.createBuffer<java.nio.FloatBuffer>()
        for (p in calibration.points) {
            srcBuf.put(p.cameraX); srcBuf.put(p.cameraY)
            dstBuf.put(p.pan);     dstBuf.put(p.tilt)
        }
        val H: Mat = findHomography(src, dst)
        check(!H.isNull && !H.empty()) {
            "findHomography returned an empty result — calibration points may be collinear or too close"
        }
        val idx = H.createIndexer<DoubleIndexer>()
        for (i in 0..8) h[i] = idx.get(i.toLong() / 3, i.toLong() % 3)
        idx.release(); H.release(); src.release(); dst.release()
    }

    /**
     * EN: Maps a camera-space pixel to a normalised (pan, tilt) pair clamped to [0, 1].
     * RU: Отображает пиксель камеры в нормализованную пару (pan, tilt), обрезанную до [0, 1].
     *
     * @param pixelX X in original (unscaled) camera frame / X в оригинальном кадре
     * @param pixelY Y in original (unscaled) camera frame / Y в оригинальном кадре
     * @return (pan, tilt) in [0, 1]
     */
    override fun map(pixelX: Float, pixelY: Float): Pair<Float, Float> {
        val x = h[0] * pixelX + h[1] * pixelY + h[2]
        val y = h[3] * pixelX + h[4] * pixelY + h[5]
        val w = h[6] * pixelX + h[7] * pixelY + h[8]
        return (x / w).toFloat().coerceIn(0f, 1f) to (y / w).toFloat().coerceIn(0f, 1f)
    }
}
