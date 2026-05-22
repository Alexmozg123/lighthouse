package tracker.detect

/**
 * EN: A 2D point in the coordinate space of the original camera frame (pixels).
 * Used for the five facial keypoints returned by YuNet.
 *
 * RU: 2D-точка в системе координат исходного кадра камеры (пиксели).
 * Используется для пяти ключевых точек лица, возвращаемых YuNet.
 */
data class FacePoint(val x: Float, val y: Float)

/**
 * EN: A single face detection result from [YuNetDetector]. All coordinates are in
 * **original frame pixels** — UI code must apply the letterbox transform before drawing.
 *
 * The five keypoints follow the standard YuNet order:
 * right eye → left eye → nose tip → right mouth corner → left mouth corner.
 *
 * RU: Результат обнаружения одного лица от [YuNetDetector]. Все координаты — в
 * **пикселях исходного кадра**; UI-код обязан применить letterbox-трансформацию
 * перед отрисовкой.
 *
 * Пять ключевых точек в стандартном порядке YuNet:
 * правый глаз → левый глаз → кончик носа → правый угол рта → левый угол рта.
 *
 * @param boxX       left edge of bounding box / левый край ограничивающего прямоугольника
 * @param boxY       top edge of bounding box / верхний край ограничивающего прямоугольника
 * @param boxW       bounding box width / ширина ограничивающего прямоугольника
 * @param boxH       bounding box height / высота ограничивающего прямоугольника
 * @param rightEye   right eye keypoint / ключевая точка правого глаза
 * @param leftEye    left eye keypoint / ключевая точка левого глаза
 * @param nose       nose tip keypoint / ключевая точка кончика носа
 * @param rightMouth right mouth corner keypoint / ключевая точка правого угла рта
 * @param leftMouth  left mouth corner keypoint / ключевая точка левого угла рта
 * @param score      detection confidence in [0, 1] / уверенность детектора в диапазоне [0, 1]
 */
data class FaceDetection(
    val boxX: Float,
    val boxY: Float,
    val boxW: Float,
    val boxH: Float,
    val rightEye: FacePoint,
    val leftEye: FacePoint,
    val nose: FacePoint,
    val rightMouth: FacePoint,
    val leftMouth: FacePoint,
    val score: Float,
) {
    /** EN: Horizontal centre of the bounding box. RU: Горизонтальный центр bbox. */
    val centerX: Float get() = boxX + boxW / 2f

    /** EN: Vertical centre of the bounding box. RU: Вертикальный центр bbox. */
    val centerY: Float get() = boxY + boxH / 2f
}
