package tracker.dmx

import ch.bildspur.artnet.ArtNetClient
import tracker.app.DetectedFrame

/**
 * Связывает трекер лиц с набором DMX-голов через Art-Net.
 *
 * Каждая [DmxFixture] — отдельная голова с собственным universe/host.
 * Сейчас все головы отслеживают одно выбранное лицо; в будущем каждой
 * можно назначить свой targetFaceId.
 *
 * Маппинг координат: центр bbox нормализуется в [0,1] → pan/tilt.
 * Если выбранное лицо не найдено — dimmer = 0 (блэкаут), позиция держится.
 */
class SpotlightController(
    private val fixtures: List<DmxFixture>,
) : AutoCloseable {

    private val artnet = ArtNetClient().also { it.start() }

    fun update(frame: DetectedFrame, selectedId: Int?) {
        fixtures.forEach { fixture ->
            val face = frame.faces.find { it.id == selectedId }
            if (face != null) {
                val cx = (face.detection.boxX + face.detection.boxW / 2f) / frame.imageWidth
                val cy = (face.detection.boxY + face.detection.boxH / 2f) / frame.imageHeight
                fixture.setPanTilt(cx, cy, dimmer = 1f)
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
