package tracker.adapter.camera

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.OpenCVFrameGrabber

/**
 * EN: Factory that creates and configures a [FrameGrabber] for the system webcam.
 * On macOS [FFmpegFrameGrabber] with `avfoundation` is used; on all other platforms
 * [OpenCVFrameGrabber] is the portable fallback.
 *
 * RU: Фабрика, создающая и настраивающая [FrameGrabber] для системной веб-камеры.
 * На macOS используется [FFmpegFrameGrabber] с `avfoundation`; на остальных платформах —
 * [OpenCVFrameGrabber].
 *
 * @param deviceIndex camera device index / индекс камеры (0 = первая доступная)
 * @param width       requested capture width / запрошенная ширина захвата
 * @param height      requested capture height / запрошенная высота захвата
 * @param targetFps   requested frame rate / запрошенная частота кадров
 */
class CameraSource(
    private val deviceIndex: Int = 0,
    private val width: Int = 1280,
    private val height: Int = 720,
    private val targetFps: Double = 30.0,
) {
    /**
     * EN: Creates and returns a configured [FrameGrabber] without starting it.
     * Call [FrameGrabber.start] before the first [FrameGrabber.grab].
     *
     * RU: Создаёт и возвращает настроенный [FrameGrabber] без запуска.
     * Вызовите [FrameGrabber.start] перед первым [FrameGrabber.grab].
     */
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
