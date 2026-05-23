package tracker.domain.usecase

import tracker.domain.entity.DmxFixture

/**
 * EN: Output port that creates a [DmxSender] bound to a specific set of fixtures.
 * A new sender is created for each active scene because the fixture list is per-scene.
 *
 * RU: Выходной порт, создающий [DmxSender] для конкретного набора фикстур.
 * Новый отправитель создаётся для каждой активной сцены, т. к. список фикстур уникален для сцены.
 */
fun interface DmxSenderFactory {

    /**
     * EN: Creates a new [DmxSender] bound to [fixtures].
     * The caller is responsible for closing the returned sender when the scene is deactivated.
     *
     * RU: Создаёт новый [DmxSender], привязанный к [fixtures].
     * Вызывающий код отвечает за закрытие отправителя при деактивации сцены.
     *
     * @param fixtures fixtures to drive / управляемые фикстуры
     * @return new sender instance / новый экземпляр отправителя
     */
    fun create(fixtures: List<DmxFixture>): DmxSender
}
