/**
 * EN: Art-Net UDP transport for a fixed set of [DmxFixture]s.
 * Owns the [ArtNetClient] lifecycle; must be closed when the active scene changes.
 *
 * Responsibilities (single):
 * - Translate a [PanTilt] command (or null for blackout) into DMX byte buffers.
 * - Unicast one Art-Net packet per fixture on every [send] call.
 *
 * Thread-safety: [send] performs a blocking UDP send and must be called from a
 * background thread (IO dispatcher). [close] may be called from any thread.
 *
 * RU: Art-Net UDP транспорт для фиксированного набора [DmxFixture].
 * Владеет жизненным циклом [ArtNetClient]; должен быть закрыт при смене активной сцены.
 *
 * Ответственность (единственная):
 * - Перевести команду [PanTilt] (или null для блэкаута) в байтовые буферы DMX.
 * - Отправить один Art-Net пакет на каждую фикстуру при каждом вызове [send].
 *
 * Потокобезопасность: [send] выполняет блокирующую UDP-отправку и должен вызываться
 * из фонового потока (IO-диспетчер). [close] можно вызывать из любого потока.
 *
 * @param fixtures EN: fixtures to drive / RU: управляемые фикстуры
 */
package tracker.dmx

import ch.bildspur.artnet.ArtNetClient
import tracker.usecase.PanTilt

class ArtNetSender(private val fixtures: List<DmxFixture>) : AutoCloseable {

    private val artnet = ArtNetClient().also { it.start() }

    /**
     * EN: Encodes [position] into every fixture's DMX buffer and unicasts the result.
     * Passing null sets all fixtures to blackout (dimmer = 0) while preserving last pan/tilt.
     *
     * RU: Кодирует [position] в DMX-буфер каждой фикстуры и отправляет по unicast.
     * null выставляет блэкаут (dimmer = 0) для всех фикстур, сохраняя последний pan/tilt.
     *
     * @param position EN: target position or null for blackout / RU: целевая позиция или null для блэкаута
     */
    fun send(position: PanTilt?) {
        fixtures.forEach { fixture ->
            if (position != null) {
                fixture.setPanTilt(position.pan, position.tilt, dimmer = 1f)
            } else {
                fixture.setDimmer(0f)
            }
            artnet.unicastDmx(fixture.host, fixture.subnet, fixture.universe, fixture.dmxData())
        }
    }

    override fun close() {
        runCatching { artnet.stop() }
    }
}
