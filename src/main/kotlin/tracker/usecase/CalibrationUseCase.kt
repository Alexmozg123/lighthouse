/**
 * EN: Domain rules for the 4-point camera→pan/tilt calibration workflow.
 *
 * Encapsulates two invariants that were previously spread across the UI:
 * 1. Each confirmed point must have a unique pan/tilt pair.
 * 2. The four points must form a non-degenerate projective mapping (no collinear or
 *    duplicate positions that cause [HomographyMapper] to return an empty matrix).
 *
 * [buildMapper] performs a single OpenCV call that both validates the geometry and
 * produces the ready-to-use mapper — avoiding the double construction that existed
 * when validation and loading were separate.
 *
 * RU: Доменные правила для 4-точечного процесса калибровки камера→pan/tilt.
 *
 * Инкапсулирует два инварианта, ранее разбросанных по UI:
 * 1. Каждая подтверждённая точка должна иметь уникальную пару pan/tilt.
 * 2. Четыре точки должны образовывать невырожденное проективное отображение (никаких
 *    коллинеарных или одинаковых позиций, из-за которых [HomographyMapper] вернёт пустую матрицу).
 *
 * [buildMapper] выполняет единственный вызов OpenCV, одновременно валидирующий геометрию
 * и создающий готовый маппер — исключая двойную конструкцию, бывшую ранее при разделении
 * валидации и загрузки.
 */
package tracker.usecase

import tracker.calibration.HomographyMapper
import tracker.scene.CalibrationData
import tracker.scene.CalibrationPoint

object CalibrationUseCase {

    /**
     * EN: Returns true if [pan]/[tilt] already appear in [existing], making them a duplicate.
     * Duplicate pan/tilt pairs produce a degenerate destination matrix in [buildMapper].
     *
     * RU: Возвращает true, если [pan]/[tilt] уже есть в [existing].
     * Дублирующиеся пары pan/tilt создают вырожденную матрицу назначения в [buildMapper].
     *
     * @param existing list of already confirmed points / список уже подтверждённых точек
     * @param pan      candidate pan value / кандидат pan
     * @param tilt     candidate tilt value / кандидат tilt
     * @return true if duplicate / true если дубликат
     */
    fun isDuplicatePanTilt(existing: List<CalibrationPoint>, pan: Float, tilt: Float): Boolean =
        existing.any { it.pan == pan && it.tilt == tilt }

    /**
     * EN: Validates [points] (must be exactly 4) and constructs a [HomographyMapper].
     * Returns [Result.success] with the mapper on valid geometry, or [Result.failure]
     * wrapping the underlying exception (e.g. [IllegalStateException] from [HomographyMapper]
     * when the matrix is empty or null — collinear/degenerate points).
     *
     * Callers decide how to translate the failure into a user-visible message.
     *
     * RU: Валидирует [points] (ровно 4) и создаёт [HomographyMapper].
     * Возвращает [Result.success] с маппером при корректной геометрии или [Result.failure]
     * с обёрнутым исключением (например, [IllegalStateException] из [HomographyMapper]
     * при пустой или null матрице — коллинеарные/вырожденные точки).
     *
     * Вызывающий код сам решает, как перевести ошибку в сообщение для пользователя.
     *
     * @param points exactly 4 calibration correspondences / ровно 4 соответствия калибровки
     * @return [Result] wrapping the ready mapper or the validation exception /
     *         [Result] с готовым маппером или исключением валидации
     */
    fun buildMapper(points: List<CalibrationPoint>): Result<HomographyMapper> {
        require(points.size == 4) { "Exactly 4 calibration points required, got ${points.size}" }
        return runCatching { HomographyMapper(CalibrationData(points)) }
    }
}
