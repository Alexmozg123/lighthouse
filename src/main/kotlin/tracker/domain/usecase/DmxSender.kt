package tracker.domain.usecase

import tracker.domain.entity.PanTilt

/**
 * EN: Output port for sending pan/tilt positions to DMX fixtures.
 * Implementations handle transport details (Art-Net, sACN, etc.) in the adapter layer.
 *
 * RU: Выходной порт для отправки позиций pan/tilt на DMX-фикстуры.
 * Реализации обрабатывают детали транспорта (Art-Net, sACN и т. д.) в слое adapter.
 */
interface DmxSender : AutoCloseable {

    /**
     * EN: Sends [position] to all managed fixtures. Pass null to blackout every fixture.
     * Must be called from a background thread (IO dispatcher).
     *
     * RU: Отправляет [position] всем управляемым фикстурам. null — блэкаут каждой фикстуры.
     * Вызывать из фонового потока (IO-диспетчер).
     *
     * @param position target position, or null for blackout / целевая позиция или null для блэкаута
     */
    fun send(position: PanTilt?)
}
