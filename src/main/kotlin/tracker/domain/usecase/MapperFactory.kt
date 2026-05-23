package tracker.domain.usecase

import tracker.domain.entity.CalibrationData

/**
 * EN: Output port that constructs a [PositionMapper] from calibration data.
 * Returns [Result.failure] if the geometry is degenerate (collinear points, etc.).
 * Isolates the domain from the concrete homography implementation in the adapter layer.
 *
 * RU: Выходной порт, создающий [PositionMapper] из данных калибровки.
 * Возвращает [Result.failure] при вырожденной геометрии (коллинеарные точки и т. д.).
 * Изолирует domain от конкретной реализации гомографии в слое adapter.
 */
fun interface MapperFactory {

    /**
     * EN: Creates a [PositionMapper] for [data], or returns failure on degenerate input.
     * RU: Создаёт [PositionMapper] для [data], или возвращает failure при вырожденном вводе.
     *
     * @param data calibration correspondences / калибровочные соответствия
     * @return mapper on success, wrapped exception on failure /
     *         маппер при успехе, обёрнутое исключение при ошибке
     */
    fun create(data: CalibrationData): Result<PositionMapper>
}
