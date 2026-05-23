package tracker.app

import tracker.domain.entity.PanTilt
import tracker.domain.usecase.DmxSender
import tracker.domain.usecase.PositionMapper

/**
 * EN: Orchestrates face tracking → DMX output for one active scene.
 * Delegates coordinate resolution to [FacePositionResolver], smooths the result with
 * two [OneEuroFilter] instances (pan + tilt), and forwards to the DMX transport.
 *
 * Lives in the app layer because it coordinates domain logic ([FacePositionResolver]) with
 * infrastructure ([ArtNetSender]); adapters must not perform such orchestration.
 *
 * RU: Оркестрирует трекинг лиц → DMX-выход для одной активной сцены.
 * Делегирует разрешение координат [FacePositionResolver], сглаживает результат двумя
 * экземплярами [OneEuroFilter] (pan + tilt), затем передаёт в DMX-транспорт.
 *
 * Находится в app-слое, поскольку координирует доменную логику ([FacePositionResolver])
 * с инфраструктурой ([ArtNetSender]); адаптеры не должны выполнять такую оркестрацию.
 *
 * @param sender    DMX transport; any [DmxSender] implementation / DMX-транспорт; любая реализация [DmxSender]
 * @param mapper    optional position mapper; null = linear fallback /
 *                  опциональный маппер; null — линейный fallback
 * @param minCutoff One-Euro minimum cutoff Hz; lower = less jitter, more lag at rest /
 *                  минимальная частота среза One-Euro в Гц
 * @param beta      One-Euro speed coefficient; higher = less lag on fast movement /
 *                  коэффициент скорости One-Euro
 */
class SpotlightController(
    private val sender: DmxSender,
    private val mapper: PositionMapper? = null,
    minCutoff: Double = 0.5,
    beta: Double = 0.003,
) : AutoCloseable {

    private val panFilter = OneEuroFilter(minCutoff = minCutoff, beta = beta)
    private val tiltFilter = OneEuroFilter(minCutoff = minCutoff, beta = beta)

    /**
     * EN: Processes one [DetectedFrame], applies One-Euro smoothing, and sends an Art-Net packet.
     * When the selected face is absent, resets the filters and sends a blackout.
     * Must be called from a background thread (IO dispatcher).
     *
     * RU: Обрабатывает один [DetectedFrame], применяет сглаживание One-Euro и отправляет Art-Net пакет.
     * Если выбранное лицо отсутствует — сбрасывает фильтры и отправляет блэкаут.
     * Вызывать из фонового потока (IO-диспетчер).
     *
     * @param frame      current tracking frame / текущий кадр трекинга
     * @param selectedId face ID to follow, or null to blackout / ID лица или null для блэкаута
     */
    fun update(frame: DetectedFrame, selectedId: Int?) {
        val raw = FacePositionResolver.resolve(frame, selectedId, mapper)
        if (raw == null) {
            panFilter.reset()
            tiltFilter.reset()
            sender.send(null)
        } else {
            val now = System.nanoTime()
            sender.send(
                PanTilt(
                    pan = panFilter.filter(raw.pan.toDouble(), now).toFloat(),
                    tilt = tiltFilter.filter(raw.tilt.toDouble(), now).toFloat(),
                )
            )
        }
    }

    override fun close() = sender.close()
}
