/**
 * EN: Orchestrates face tracking → DMX output for one active scene.
 * Delegates coordinate resolution to [FacePositionMapper] and transport to [ArtNetSender].
 *
 * Lifecycle: created by [tracker.app.AppViewModel] when a scene is loaded, closed
 * when the scene changes or the window closes. Thread-safety is provided by the
 * caller ([AppViewModel]) which calls [update] exclusively from the IO dispatcher.
 *
 * RU: Оркестрирует трекинг лиц → DMX-выход для одной активной сцены.
 * Делегирует разрешение координат в [FacePositionMapper] и транспорт в [ArtNetSender].
 *
 * Жизненный цикл: создаётся [tracker.app.AppViewModel] при загрузке сцены, закрывается
 * при смене сцены или закрытии окна. Потокобезопасность обеспечивает вызывающий код
 * ([AppViewModel]), вызывающий [update] только из IO-диспетчера.
 *
 * @param sender EN: Art-Net transport layer / RU: слой Art-Net транспорта
 * @param mapper EN: optional homography mapper; null falls back to linear /
 *               RU: опциональный гомографический маппер; null — линейный fallback
 */
package tracker.dmx

import tracker.app.DetectedFrame
import tracker.calibration.HomographyMapper
import tracker.usecase.FacePositionMapper

class SpotlightController(
    private val sender: ArtNetSender,
    private val mapper: HomographyMapper? = null,
) : AutoCloseable {

    /**
     * EN: Processes one [DetectedFrame] and sends an Art-Net packet to every fixture.
     * Must be called from a background thread (IO dispatcher).
     *
     * RU: Обрабатывает один [DetectedFrame] и отправляет Art-Net пакет каждой фикстуре.
     * Должен вызываться из фонового потока (IO-диспетчер).
     *
     * @param frame      EN: current tracking frame / RU: текущий кадр трекинга
     * @param selectedId EN: ID of the face to follow, or null to blackout /
     *                   RU: ID лица для отслеживания, или null для блэкаута
     */
    fun update(frame: DetectedFrame, selectedId: Int?) {
        sender.send(FacePositionMapper.resolve(frame, selectedId, mapper))
    }

    override fun close() = sender.close()
}
