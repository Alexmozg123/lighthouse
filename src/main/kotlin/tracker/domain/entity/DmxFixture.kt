package tracker.domain.entity

/**
 * EN: Represents a single 16-bit pan/tilt moving-head DMX fixture addressable over Art-Net.
 * Holds a 512-byte DMX universe frame and provides helpers to write pan/tilt/dimmer values.
 *
 * Default channel layout (1-based offsets from [startChannel], Generic Moving Head profile):
 * ```
 * offset 0 — Pan coarse  (bits 15–8)
 * offset 1 — Pan fine    (bits  7–0)
 * offset 2 — Tilt coarse (bits 15–8)
 * offset 3 — Tilt fine   (bits  7–0)
 * offset 4 — Dimmer      (8-bit, 0–255)
 * ```
 *
 * RU: Представляет одну 16-bit pan/tilt DMX-голову, адресуемую через Art-Net.
 * Хранит 512-байтовый фрейм и предоставляет хелперы для записи pan/tilt/dimmer.
 *
 * @param host         IP address of the Art-Net node / IP-адрес Art-Net узла
 * @param subnet       Art-Net subnet (0–15) / Art-Net подсеть (0–15)
 * @param universe     Art-Net universe (0–15) / юниверс (0–15)
 * @param startChannel 1-based DMX address of the fixture's first channel / 1-based DMX-адрес первого канала
 */
class DmxFixture(
    val host: String = "127.0.0.1",
    val subnet: Int = 0,
    val universe: Int = 0,
    val startChannel: Int = 1,
) {
    private val data = ByteArray(512)

    /**
     * EN: Writes normalised pan, tilt, and dimmer into the DMX frame buffer.
     * Values are clamped to [0.0, 1.0] before encoding.
     *
     * RU: Записывает нормализованные pan, tilt и dimmer в буфер DMX-фрейма.
     * Значения обрезаются до [0.0, 1.0] перед кодированием.
     *
     * @param pan    normalised pan [0.0, 1.0] / нормализованный pan [0.0, 1.0]
     * @param tilt   normalised tilt [0.0, 1.0] / нормализованный tilt [0.0, 1.0]
     * @param dimmer normalised dimmer [0.0, 1.0] / нормализованный dimmer [0.0, 1.0]
     */
    fun setPanTilt(pan: Float, tilt: Float, dimmer: Float = 1f) {
        val base    = startChannel - 1
        val panVal  = (pan.coerceIn(0f, 1f)    * 65535).toInt()
        val tiltVal = (tilt.coerceIn(0f, 1f)   * 65535).toInt()
        val dimVal  = (dimmer.coerceIn(0f, 1f) * 255).toInt()
        data[base + 0] = (panVal  ushr 8).toByte()
        data[base + 1] = (panVal  and 0xFF).toByte()
        data[base + 2] = (tiltVal ushr 8).toByte()
        data[base + 3] = (tiltVal and 0xFF).toByte()
        data[base + 4] = dimVal.toByte()
    }

    /**
     * EN: Updates only the dimmer channel without changing pan/tilt. Used for blackout.
     * RU: Обновляет только канал диммера без изменения pan/tilt. Используется для блэкаута.
     *
     * @param dimmer normalised dimmer [0.0, 1.0] / нормализованный dimmer [0.0, 1.0]
     */
    fun setDimmer(dimmer: Float) {
        data[startChannel - 1 + 4] = (dimmer.coerceIn(0f, 1f) * 255).toInt().toByte()
    }

    /**
     * EN: Returns a defensive copy of the full 512-byte DMX universe buffer.
     * RU: Возвращает защитную копию 512-байтового буфера DMX-юниверса.
     */
    fun dmxData(): ByteArray = data.copyOf()
}
