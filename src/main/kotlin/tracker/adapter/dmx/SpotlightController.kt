package tracker.adapter.dmx

import tracker.adapter.calibration.HomographyMapper
import tracker.app.DetectedFrame
import tracker.domain.usecase.FacePositionMapper

/**
 * EN: Orchestrates face tracking → DMX output for one active scene.
 * Delegates coordinate resolution to [FacePositionMapper] and transport to [ArtNetSender].
 *
 * RU: Оркестрирует трекинг лиц → DMX-выход для одной активной сцены.
 * Делегирует разрешение координат [FacePositionMapper] и транспорт [ArtNetSender].
 *
 * @param sender Art-Net transport layer / слой Art-Net транспорта
 * @param mapper optional homography mapper; null = linear fallback /
 *               опциональный маппер; null — линейный fallback
 */
class SpotlightController(
    private val sender: ArtNetSender,
    private val mapper: HomographyMapper? = null,
) : AutoCloseable {

    /**
     * EN: Processes one [DetectedFrame] and sends an Art-Net packet to every fixture.
     * Must be called from a background thread (IO dispatcher).
     *
     * RU: Обрабатывает один [DetectedFrame] и отправляет Art-Net пакет каждой фикстуре.
     * Вызывать из фонового потока (IO-диспетчер).
     *
     * @param frame      current tracking frame / текущий кадр трекинга
     * @param selectedId face ID to follow, or null to blackout / ID лица или null для блэкаута
     */
    fun update(frame: DetectedFrame, selectedId: Int?) {
        sender.send(FacePositionMapper.resolve(frame, selectedId, mapper))
    }

    override fun close() = sender.close()
}
