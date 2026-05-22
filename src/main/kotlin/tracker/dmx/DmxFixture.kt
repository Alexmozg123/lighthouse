package tracker.dmx

/**
 * Represents a 16-bit pan/tilt moving head fixture on a specific Art-Net universe.
 *
 * Default channel layout (1-based, Generic Moving Head profile):
 *   offset 0 — Pan coarse
 *   offset 1 — Pan fine
 *   offset 2 — Tilt coarse
 *   offset 3 — Tilt fine
 *   offset 4 — Dimmer
 *
 * [host]         — IP-адрес получателя (узел или QLC+)
 * [subnet]       — Art-Net subnet (0–15)
 * [universe]     — Art-Net universe внутри subnet (0–15)
 * [startChannel] — 1-based DMX-адрес первого канала головы
 */
class DmxFixture(
    val host: String = "127.0.0.1",
    val subnet: Int = 0,
    val universe: Int = 0,
    val startChannel: Int = 1,
) {
    private val data = ByteArray(512)

    /**
     * @param pan    [0.0, 1.0]  (0 = крайний левый,  1 = крайний правый)
     * @param tilt   [0.0, 1.0]  (0 = крайний верх,   1 = крайний низ)
     * @param dimmer [0.0, 1.0]
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

    fun setDimmer(dimmer: Float) {
        data[startChannel - 1 + 4] = (dimmer.coerceIn(0f, 1f) * 255).toInt().toByte()
    }

    fun dmxData(): ByteArray = data.copyOf()
}
