package tracker.app

import kotlin.math.PI
import kotlin.math.abs

/**
 * EN: One-Euro filter for smoothing a single real-valued signal.
 * Reduces jitter when the signal is slow while staying responsive during fast movements
 * by adaptively raising the cutoff frequency proportional to the signal's speed.
 *
 * Reference: Casiez et al., "1€ Filter: A Simple Speed-based Low-pass Filter for
 * Noisy Input in Interactive Systems", CHI 2012.
 *
 * Not thread-safe; use one instance per thread.
 *
 * RU: Фильтр One-Euro для сглаживания одномерного вещественного сигнала.
 * При медленном движении уменьшает дрожание, при быстром — сохраняет отзывчивость
 * за счёт адаптивного повышения частоты среза пропорционально скорости сигнала.
 *
 * Не потокобезопасен; один экземпляр на поток.
 *
 * @param minCutoff minimum cutoff frequency in Hz; lower = less jitter, more lag at rest /
 *                  минимальная частота среза в Гц; меньше = меньше дрожания, больше задержки в покое
 * @param beta      speed coefficient; higher = less lag during fast movement /
 *                  коэффициент скорости; больше = меньше задержки при быстром движении
 * @param dCutoff   cutoff frequency for the derivative low-pass pre-filter /
 *                  частота среза для предварительной фильтрации производной
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.007,
    private val dCutoff: Double = 1.0,
) {
    private var xPrev: Double? = null
    private var dxFiltered: Double = 0.0
    private var tPrevNs: Long = 0L

    /**
     * EN: Feeds [x] through the filter and returns the smoothed value.
     * On the first call after construction or [reset], returns [x] unchanged.
     *
     * RU: Пропускает [x] через фильтр и возвращает сглаженное значение.
     * При первом вызове после создания или [reset] возвращает [x] без изменений.
     *
     * @param x           raw input value / сырое входное значение
     * @param timestampNs sample timestamp from [System.nanoTime] / временная метка из [System.nanoTime]
     * @return smoothed value / сглаженное значение
     */
    fun filter(x: Double, timestampNs: Long = System.nanoTime()): Double {
        val te = if (tPrevNs == 0L) 1.0 / 30.0 else (timestampNs - tPrevNs) / 1_000_000_000.0

        val dx = if (xPrev == null) 0.0 else (x - xPrev!!) / te
        dxFiltered = lowPass(dx, dxFiltered, alpha(dCutoff, te))

        val cutoff = minCutoff + beta * abs(dxFiltered)
        val xFiltered = lowPass(x, xPrev ?: x, alpha(cutoff, te))

        xPrev = xFiltered
        tPrevNs = timestampNs
        return xFiltered
    }

    /**
     * EN: Resets all internal state. Call when the tracked signal source disappears
     * to avoid a stale derivative spike on the next [filter] call.
     *
     * RU: Сбрасывает всё внутреннее состояние. Вызывай когда источник сигнала пропадает,
     * чтобы избежать всплеска производной при следующем вызове [filter].
     */
    fun reset() {
        xPrev = null
        dxFiltered = 0.0
        tPrevNs = 0L
    }

    private fun alpha(cutoff: Double, te: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / te)
    }

    private fun lowPass(x: Double, prev: Double, a: Double): Double = a * x + (1.0 - a) * prev
}
