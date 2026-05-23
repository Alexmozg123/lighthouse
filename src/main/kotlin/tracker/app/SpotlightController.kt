package tracker.app

import tracker.domain.usecase.DmxSender
import tracker.domain.usecase.PositionMapper

/**
 * EN: Orchestrates face tracking → DMX output for one active scene.
 * Delegates coordinate resolution to [FacePositionResolver] and transport to [ArtNetSender].
 *
 * Lives in the app layer because it coordinates domain logic ([FacePositionResolver]) with
 * infrastructure ([ArtNetSender]); adapters must not perform such orchestration.
 *
 * RU: Оркестрирует трекинг лиц → DMX-выход для одной активной сцены.
 * Делегирует разрешение координат [FacePositionResolver] и транспорт [ArtNetSender].
 *
 * Находится в app-слое, поскольку координирует доменную логику ([FacePositionResolver])
 * с инфраструктурой ([ArtNetSender]); адаптеры не должны выполнять такую оркестрацию.
 *
 * @param sender DMX transport; any [DmxSender] implementation / DMX-транспорт; любая реализация [DmxSender]
 * @param mapper optional position mapper; null = linear fallback /
 *               опциональный маппер; null — линейный fallback
 */
class SpotlightController(
    private val sender: DmxSender,
    private val mapper: PositionMapper? = null,
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
        sender.send(FacePositionResolver.resolve(frame, selectedId, mapper))
    }

    override fun close() = sender.close()
}
