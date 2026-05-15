package tracker.detect

/** Координаты в пикселях исходного кадра. */
data class FacePoint(val x: Float, val y: Float)

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
)
