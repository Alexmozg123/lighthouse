package tracker.adapter.dmx

import ch.bildspur.artnet.ArtNetClient
import tracker.domain.entity.DmxFixture
import tracker.domain.entity.PanTilt
import tracker.domain.usecase.DmxSender

/**
 * EN: Art-Net UDP transport for a fixed set of [DmxFixture]s.
 * Owns the [ArtNetClient] lifecycle; must be closed when the active scene changes.
 *
 * Thread-safety: [send] performs a blocking UDP send — call from IO dispatcher.
 * [close] may be called from any thread.
 *
 * RU: Art-Net UDP транспорт для фиксированного набора [DmxFixture].
 * Владеет жизненным циклом [ArtNetClient]; закрывается при смене активной сцены.
 *
 * @param fixtures fixtures to drive / управляемые фикстуры
 */
class ArtNetSender(private val fixtures: List<DmxFixture>) : DmxSender {

    private val artnet = ArtNetClient().also { it.start() }

    /**
     * EN: Encodes [position] into every fixture's DMX buffer and unicasts the result.
     * Null sets all fixtures to blackout (dimmer = 0) while preserving last pan/tilt.
     *
     * RU: Кодирует [position] в DMX-буфер каждой фикстуры и отправляет по unicast.
     * Null выставляет блэкаут (dimmer = 0), сохраняя последний pan/tilt.
     *
     * @param position target position or null for blackout / целевая позиция или null для блэкаута
     */
    override fun send(position: PanTilt?) {
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
