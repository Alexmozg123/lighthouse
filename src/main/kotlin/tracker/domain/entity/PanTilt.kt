package tracker.domain.entity

/**
 * EN: Normalised pan/tilt position for a moving-head fixture.
 * Both values are in [0.0, 1.0]. A null result from
 * [tracker.domain.usecase.FacePositionMapper.resolve] signals blackout.
 *
 * RU: Нормализованная позиция pan/tilt для световой головы.
 * Оба значения в [0.0, 1.0]. Null-результат
 * [tracker.domain.usecase.FacePositionMapper.resolve] означает блэкаут.
 *
 * @param pan  EN: horizontal angle, 0 = leftmost / RU: горизонтальный угол, 0 = крайний левый
 * @param tilt EN: vertical angle, 0 = top / RU: вертикальный угол, 0 = верх
 */
data class PanTilt(val pan: Float, val tilt: Float)
