package tracker.app

import tracker.domain.entity.PanTilt
import tracker.domain.usecase.PositionMapper

/**
 * EN: Resolves a selected face's position in a [DetectedFrame] to a [PanTilt] command.
 *
 * Returns null when the selected face is absent from the frame — callers treat null
 * as a blackout signal.
 *
 * Resolution strategy:
 * - If [mapper] is provided: delegates to the [PositionMapper] (e.g. homography projection).
 * - Otherwise: linear fallback (centre-x / imageWidth, centre-y / imageHeight).
 *
 * Named *Resolver* (not *Mapper*) to avoid confusion with the [PositionMapper] interface:
 * [HomographyMapper] *is* a [PositionMapper]; this class *uses* one.
 *
 * Resides in the app layer because it depends on [DetectedFrame] (an app-layer type).
 *
 * RU: Определяет позицию выбранного лица в [DetectedFrame] и преобразует в команду [PanTilt].
 *
 * Возвращает null, если выбранное лицо отсутствует в кадре — вызывающий код трактует
 * null как сигнал блэкаута.
 *
 * Стратегия:
 * - Если [mapper] задан: делегирует [PositionMapper] (например, гомографическая проекция).
 * - Иначе: линейный fallback.
 *
 * Называется *Resolver* (не *Mapper*), чтобы не путать с интерфейсом [PositionMapper]:
 * [HomographyMapper] реализует [PositionMapper]; этот класс его использует.
 *
 * Находится в app-слое, поскольку зависит от [DetectedFrame] (тип app-слоя).
 */
object FacePositionResolver {

    /**
     * EN: Resolves the position of face [selectedId] inside [frame].
     * RU: Определяет позицию лица [selectedId] внутри [frame].
     *
     * @param frame      current tracking frame / текущий кадр трекинга
     * @param selectedId ID of the face to track, or null / ID отслеживаемого лица или null
     * @param mapper     optional position mapper; null = linear fallback /
     *                   опциональный маппер; null — линейный fallback
     * @return [PanTilt] when face is in frame, null for blackout /
     *         [PanTilt] если лицо в кадре, null для блэкаута
     */
    fun resolve(frame: DetectedFrame, selectedId: Int?, mapper: PositionMapper?): PanTilt? {
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
