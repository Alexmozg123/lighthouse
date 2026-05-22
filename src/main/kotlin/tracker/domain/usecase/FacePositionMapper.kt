package tracker.domain.usecase

import tracker.adapter.calibration.HomographyMapper
import tracker.app.DetectedFrame
import tracker.domain.entity.PanTilt

/**
 * EN: Maps a selected face's position in a [DetectedFrame] to a [PanTilt] command.
 *
 * Returns null when the selected face is absent from the frame — callers treat null
 * as a blackout signal.
 *
 * Mapping strategy:
 * - If [mapper] is provided: homography projection (camera px → normalised pan/tilt).
 * - Otherwise: linear fallback (centre-x / imageWidth, centre-y / imageHeight).
 *
 * RU: Переводит позицию выбранного лица в [DetectedFrame] в команду [PanTilt].
 *
 * Возвращает null, если выбранное лицо отсутствует в кадре — вызывающий код трактует
 * null как сигнал блэкаута.
 *
 * Стратегия маппинга:
 * - Если [mapper] задан: гомографическая проекция.
 * - Иначе: линейный fallback.
 */
object FacePositionMapper {

    /**
     * EN: Resolves the position of face [selectedId] inside [frame].
     * RU: Определяет позицию лица [selectedId] внутри [frame].
     *
     * @param frame      current tracking frame / текущий кадр трекинга
     * @param selectedId ID of the face to track, or null / ID отслеживаемого лица или null
     * @param mapper     optional homography mapper; null = linear fallback /
     *                   опциональный маппер; null — линейный fallback
     * @return [PanTilt] when face is in frame, null for blackout /
     *         [PanTilt] если лицо в кадре, null для блэкаута
     */
    fun resolve(frame: DetectedFrame, selectedId: Int?, mapper: HomographyMapper?): PanTilt? {
        val face = frame.faces.find { it.id == selectedId } ?: return null
        val cx = face.detection.boxX + face.detection.boxW / 2f
        val cy = face.detection.boxY + face.detection.boxH / 2f
        return if (mapper != null) {
            val (pan, tilt) = mapper.map(cx, cy)
            PanTilt(pan, tilt)
        } else {
            PanTilt(cx / frame.imageWidth, cy / frame.imageHeight)
        }
    }
}
