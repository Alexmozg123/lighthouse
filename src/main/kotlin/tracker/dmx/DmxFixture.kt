package tracker.dmx

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
 * Хранит 512-байтовый фрейм DMX-юниверса и предоставляет хелперы для записи
 * значений pan/tilt/dimmer.
 *
 * Раскладка каналов по умолчанию (1-based смещения от [startChannel], профиль Generic Moving Head):
 * ```
 * смещение 0 — Pan coarse  (биты 15–8)
 * смещение 1 — Pan fine    (биты  7–0)
 * смещение 2 — Tilt coarse (биты 15–8)
 * смещение 3 — Tilt fine   (биты  7–0)
 * смещение 4 — Dimmer      (8-bit, 0–255)
 * ```
 *
 * @param host         IP address of the Art-Net node or QLC+ / IP-адрес Art-Net узла или QLC+
 * @param subnet       Art-Net subnet (0–15) / Art-Net подсеть (0–15)
 * @param universe     Art-Net universe within the subnet (0–15) / юниверс внутри подсети (0–15)
 * @param startChannel 1-based DMX address of the fixture's first channel / 1-based DMX-адрес
 *                     первого канала головы
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
     * Values are clamped to [0.0, 1.0] before encoding. Pan and tilt are packed as
     * 16-bit big-endian; dimmer as an 8-bit value.
     *
     * RU: Записывает нормализованные pan, tilt и dimmer в буфер DMX-фрейма.
     * Значения обрезаются до [0.0, 1.0] перед кодированием. Pan и tilt упаковываются
     * как 16-bit big-endian; dimmer — как 8-bit значение.
     *
     * @param pan    normalised pan position [0.0, 1.0], 0 = leftmost / нормализованная позиция [0.0, 1.0], 0 = крайний левый
     * @param tilt   normalised tilt position [0.0, 1.0], 0 = top / нормализованная позиция [0.0, 1.0], 0 = верх
     * @param dimmer normalised dimmer level [0.0, 1.0] / нормализованный уровень диммера [0.0, 1.0]
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
     * EN: Updates only the dimmer channel without changing pan/tilt.
     * Used for blackout when the selected face is not in frame.
     *
     * RU: Обновляет только канал диммера без изменения pan/tilt.
     * Используется для блэкаута, когда выбранное лицо не в кадре.
     *
     * @param dimmer normalised dimmer level [0.0, 1.0] / нормализованный уровень диммера [0.0, 1.0]
     */
    fun setDimmer(dimmer: Float) {
        data[startChannel - 1 + 4] = (dimmer.coerceIn(0f, 1f) * 255).toInt().toByte()
    }

    /**
     * EN: Returns a defensive copy of the full 512-byte DMX universe buffer.
     * The caller may modify the returned array freely.
     *
     * RU: Возвращает защитную копию полного 512-байтового буфера DMX-юниверса.
     * Вызывающий код может свободно изменять возвращённый массив.
     */
    fun dmxData(): ByteArray = data.copyOf()
}
