package tracker.domain.usecase

/**
 * EN: Converts a camera-pixel coordinate to a normalised (pan, tilt) pair in [0, 1].
 *
 * Implementations may use a projective homography ([tracker.adapter.calibration.HomographyMapper])
 * or a simple linear transform. Domain code depends only on this interface.
 *
 * RU: Переводит координату пикселя камеры в нормализованную пару (pan, tilt) в [0, 1].
 *
 * Реализации могут использовать проективную гомографию или линейное преобразование.
 * Доменный код зависит только от этого интерфейса.
 */
interface PositionMapper {

    /**
     * EN: Maps [pixelX], [pixelY] (original, unscaled camera-frame coordinates)
     * to a (pan, tilt) pair clamped to [0, 1].
     *
     * RU: Отображает [pixelX], [pixelY] (координаты оригинального кадра камеры)
     * в пару (pan, tilt), обрезанную до [0, 1].
     *
     * @param pixelX X in the original camera frame / X в оригинальном кадре камеры
     * @param pixelY Y in the original camera frame / Y в оригинальном кадре камеры
     * @return (pan, tilt) in [0, 1] / (pan, tilt) в диапазоне [0, 1]
     */
    fun map(pixelX: Float, pixelY: Float): Pair<Float, Float>
}
