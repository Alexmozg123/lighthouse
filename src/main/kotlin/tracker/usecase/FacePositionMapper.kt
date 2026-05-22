/**
 * EN: Domain types and logic for translating a selected face's position into a
 * normalised pan/tilt command for a moving-head fixture.
 *
 * [PanTilt] is the output of this layer and the input to the DMX transport layer
 * ([tracker.dmx.ArtNetSender]). Keeping it here (rather than in the dmx package)
 * lets the use-case layer remain independent of transport specifics.
 *
 * RU: Доменные типы и логика для перевода позиции выбранного лица в нормализованную
 * команду pan/tilt для световой головы.
 *
 * [PanTilt] — выход этого слоя и вход слоя DMX-транспорта ([tracker.dmx.ArtNetSender]).
 * Размещение здесь (а не в пакете dmx) позволяет слою use-case оставаться независимым
 * от деталей транспорта.
 */
package tracker.usecase

import tracker.app.DetectedFrame
import tracker.calibration.HomographyMapper

/**
 * EN: Normalised pan/tilt position for a moving-head fixture.
 * Both values are in [0.0, 1.0]; null [FacePositionMapper.resolve] result means blackout.
 *
 * RU: Нормализованная позиция pan/tilt для световой головы.
 * Оба значения в диапазоне [0.0, 1.0]; null-результат [FacePositionMapper.resolve] означает блэкаут.
 *
 * @param pan  EN: horizontal angle, 0 = leftmost / RU: горизонтальный угол, 0 = крайний левый
 * @param tilt EN: vertical angle, 0 = top / RU: вертикальный угол, 0 = верх
 */
data class PanTilt(val pan: Float, val tilt: Float)

/**
 * EN: Maps a selected face in a [DetectedFrame] to a [PanTilt] command.
 *
 * Returns null when the selected face is absent from the frame — callers should
 * treat null as a blackout signal.
 *
 * Mapping strategy:
 * - If [mapper] is provided: homography projection (camera px → normalised pan/tilt).
 * - Otherwise: linear fallback (centre-x / imageWidth, centre-y / imageHeight).
 *
 * RU: Переводит выбранное лицо в [DetectedFrame] в команду [PanTilt].
 *
 * Возвращает null, если выбранное лицо отсутствует в кадре — вызывающий код должен
 * трактовать null как сигнал блэкаута.
 *
 * Стратегия маппинга:
 * - Если [mapper] задан: гомографическая проекция (px камеры → нормализованный pan/tilt).
 * - Иначе: линейный fallback (centre-x / imageWidth, centre-y / imageHeight).
 */
object FacePositionMapper {

    /**
     * EN: Resolves the position of face [selectedId] inside [frame].
     *
     * RU: Определяет позицию лица [selectedId] внутри [frame].
     *
     * @param frame      EN: current tracking frame / RU: текущий кадр трекинга
     * @param selectedId EN: ID of the face to track, or null / RU: ID отслеживаемого лица или null
     * @param mapper     EN: optional homography mapper; null falls back to linear /
     *                   RU: опциональный гомографический маппер; null — линейный fallback
     * @return [PanTilt] when the face is in frame, null for blackout /
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
