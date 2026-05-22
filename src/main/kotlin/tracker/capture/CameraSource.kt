package tracker.capture

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.OpenCVFrameGrabber

/**
 * EN: Factory that creates and configures a [FrameGrabber] for the system webcam.
 * The actual frame loop lives in [tracker.app.TrackingPipeline] so that detection
 * and conversion happen before the next [FrameGrabber.grab] overwrites the internal buffer.
 *
 * On macOS [FFmpegFrameGrabber] with `avfoundation` is used because it supports
 * `pixel_format = "uyvy422"` and provides native camera access without additional drivers.
 * On all other platforms [OpenCVFrameGrabber] is used as a portable fallback.
 *
 * RU: Фабрика, создающая и настраивающая [FrameGrabber] для системной веб-камеры.
 * Сам цикл обработки кадров живёт в [tracker.app.TrackingPipeline], чтобы детекция
 * и конвертация выполнялись до того, как следующий вызов [FrameGrabber.grab]
 * перезапишет внутренний буфер.
 *
 * На macOS используется [FFmpegFrameGrabber] с `avfoundation` — поддерживает
 * `pixel_format = "uyvy422"` и обеспечивает нативный доступ к камере без лишних
 * драйверов. На остальных платформах — [OpenCVFrameGrabber] как портируемый fallback.
 *
 * @param deviceIndex camera device index / индекс камеры (0 = первая/первая доступная)
 * @param width       requested capture width in pixels / запрошенная ширина захвата в пикселях
 * @param height      requested capture height in pixels / запрошенная высота захвата в пикселях
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
