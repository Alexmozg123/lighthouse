package tracker.dmx

import ch.bildspur.artnet.ArtNetClient
import tracker.app.DetectedFrame
import tracker.calibration.HomographyMapper

/**
 * EN: Bridges the face tracker with a set of DMX moving heads over Art-Net.
 *
 * Each [DmxFixture] represents one physical (or virtual) head with its own
 * Art-Net host/subnet/universe. Currently all fixtures track the same selected face;
 * per-fixture target assignment is a planned future feature.
 *
 * Coordinate mapping: the bounding-box centre is normalised to [0, 1] and mapped
 * directly to pan/tilt. If the selected face is absent from the frame, the dimmer is
 * set to 0 (blackout) while the last pan/tilt position is preserved.
 *
 * One `unicastDmx` packet is sent per fixture on every call to [update], regardless
 * of whether the position changed.
 *
 * RU: Связывает трекер лиц с набором DMX-голов через Art-Net.
 *
 * Каждая [DmxFixture] представляет одну физическую (или виртуальную) голову
 * со своим Art-Net host/subnet/universe. Сейчас все головы отслеживают одно выбранное
 * лицо; назначение отдельных целей каждой голове — запланированная функция.
 *
 * Маппинг координат: центр bbox нормализуется в [0, 1] и напрямую отображается
 * на pan/tilt. Если выбранное лицо отсутствует в кадре, диммер обнуляется (блэкаут),
 * а последняя позиция pan/tilt сохраняется.
 *
 * На каждый вызов [update] отправляется один `unicastDmx`-пакет на фикстуру,
 * независимо от того, изменилась ли позиция.
 *
 * @param fixtures list of DMX fixtures to drive / список DMX-фикстур для управления
 * @param mapper   optional homography-based coordinate mapper; null falls back to linear /
 *                 опциональный гомографический маппер координат; null — линейный fallback
 */
class SpotlightController(
    private val fixtures: List<DmxFixture>,
    private val mapper: HomographyMapper? = null,
) : AutoCloseable {

    private val artnet = ArtNetClient().also { it.start() }

    /**
     * EN: Processes one [DetectedFrame] and sends an Art-Net packet to every fixture.
     * Must be called from a background thread (IO dispatcher); [ArtNetClient.unicastDmx]
     * performs a blocking UDP send.
     *
     * RU: Обрабатывает один [DetectedFrame] и отправляет Art-Net пакет каждой фикстуре.
     * Должен вызываться из фонового потока (IO-диспетчер); [ArtNetClient.unicastDmx]
     * выполняет блокирующую UDP-отправку.
     *
     * @param frame      current tracking frame / текущий кадр трекинга
     * @param selectedId ID of the face to follow, or null to blackout /
     *                   ID лица для отслеживания, или null для блэкаута
     */
    fun update(frame: DetectedFrame, selectedId: Int?) {
        fixtures.forEach { fixture ->
            val face = frame.faces.find { it.id == selectedId }
            if (face != null) {
                val faceCx = face.detection.boxX + face.detection.boxW / 2f
                val faceCy = face.detection.boxY + face.detection.boxH / 2f
                val (pan, tilt) = mapper?.map(faceCx, faceCy)
                    ?: (faceCx / frame.imageWidth to faceCy / frame.imageHeight)
                fixture.setPanTilt(pan, tilt, dimmer = 1f)
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
