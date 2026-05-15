package tracker.capture

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.OpenCVFrameGrabber

/**
 * Конфигурация и открытие grabber'а веб-камеры. Сам поток кадров строит
 * [tracker.app.TrackingPipeline] — там же делается детекция, чтобы Frame
 * не успевал перезаписаться следующим grab() до конвертации.
 */
class CameraSource(
    private val deviceIndex: Int = 0,
    private val width: Int = 1280,
    private val height: Int = 720,
    private val targetFps: Double = 30.0,
) {
    fun openGrabber(): FrameGrabber {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("mac")) {
            FFmpegFrameGrabber(deviceIndex.toString()).apply {
                format = "avfoundation"
                imageWidth = this@CameraSource.width
                imageHeight = this@CameraSource.height
                frameRate = targetFps
                setOption("pixel_format", "uyvy422")
                setOption("framerate", targetFps.toInt().toString())
            }
        } else {
            OpenCVFrameGrabber(deviceIndex).apply {
                imageWidth = this@CameraSource.width
                imageHeight = this@CameraSource.height
                frameRate = targetFps
            }
        }
    }
}
