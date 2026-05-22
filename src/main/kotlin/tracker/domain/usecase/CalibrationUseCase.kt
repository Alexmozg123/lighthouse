package tracker.domain.usecase

import tracker.adapter.calibration.HomographyMapper
import tracker.domain.entity.CalibrationData
import tracker.domain.entity.CalibrationPoint

/**
 * EN: Domain rules for the 4-point camera→pan/tilt calibration workflow.
 *
 * Encapsulates two invariants:
 * 1. Each confirmed point must have a unique pan/tilt pair.
 * 2. The four points must form a non-degenerate projective mapping.
 *
 * [buildMapper] performs a single OpenCV call that both validates the geometry and
 * produces the ready-to-use mapper — avoiding a double construction.
 *
 * RU: Доменные правила для 4-точечного процесса калибровки камера→pan/tilt.
 *
 * Инкапсулирует два инварианта:
 * 1. Каждая точка должна иметь уникальную пару pan/tilt.
 * 2. Четыре точки должны образовывать невырожденное проективное отображение.
 *
 * [buildMapper] выполняет единственный вызов OpenCV, одновременно валидируя геометрию
 * и создавая готовый маппер.
 */
object CalibrationUseCase {

    /**
     * EN: Returns true if [pan]/[tilt] already appear in [existing].
     * RU: Возвращает true, если [pan]/[tilt] уже есть в [existing].
     *
     * @param existing confirmed points so far / уже подтверждённые точки
     * @param pan      candidate pan / кандидат pan
     * @param tilt     candidate tilt / кандидат tilt
     */
    fun isDuplicatePanTilt(existing: List<CalibrationPoint>, pan: Float, tilt: Float): Boolean =
        existing.any { it.pan == pan && it.tilt == tilt }

    /**
     * EN: Validates [points] (exactly 4) and constructs a [HomographyMapper].
     * Returns [Result.failure] when the geometry is degenerate (collinear points, duplicates, etc.).
     *
     * RU: Валидирует [points] (ровно 4) и создаёт [HomographyMapper].
     * Возвращает [Result.failure] при вырожденной геометрии (коллинеарные точки, дубликаты и т. д.).
     *
     * @param points exactly 4 calibration correspondences / ровно 4 соответствия калибровки
     * @return [Result] with mapper on success, or wrapped exception on failure /
     *         [Result] с маппером при успехе или обёрнутым исключением при ошибке
     */
    fun buildMapper(points: List<CalibrationPoint>): Result<HomographyMapper> {
        require(points.size == 4) { "Exactly 4 calibration points required, got ${points.size}" }
        return runCatching { HomographyMapper(CalibrationData(points)) }
    }
}
