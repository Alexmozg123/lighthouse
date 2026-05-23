/**
 * EN: Koin dependency injection module for the Lighthouse application.
 * Declares all singletons needed by [AppViewModel].
 *
 * Lifecycle: [AppViewModel] is a singleton tied to the application process.
 * Its [AutoCloseable.close] is called by the Compose window's DisposableEffect,
 * which in turn closes [TrackingPipeline] (and its owned [YuNetDetector]).
 *
 * RU: Koin-модуль зависимостей приложения Lighthouse.
 * Объявляет все синглтоны, необходимые для [AppViewModel].
 *
 * Жизненный цикл: [AppViewModel] — синглтон уровня процесса.
 * Его [AutoCloseable.close] вызывается DisposableEffect Compose-окна,
 * что в свою очередь закрывает [TrackingPipeline] (и принадлежащий ему [YuNetDetector]).
 */
package tracker.di

import org.koin.dsl.module
import tracker.adapter.calibration.HomographyMapper
import tracker.adapter.camera.CameraSource
import tracker.adapter.camera.YuNetDetector
import tracker.adapter.dmx.ArtNetSender
import tracker.adapter.persistence.SceneStore
import tracker.app.AppViewModel
import tracker.app.TrackingPipeline
import tracker.domain.repository.SceneRepository
import tracker.domain.usecase.DmxSenderFactory
import tracker.domain.usecase.MapperFactory

val appModule = module {
    single { CameraSource(deviceIndex = 0) }
    single { YuNetDetector() }
    single { TrackingPipeline(get(), get()) }
    single<SceneRepository> { SceneStore }
    single<MapperFactory> { MapperFactory { data -> runCatching { HomographyMapper(data) } } }
    single<DmxSenderFactory> { DmxSenderFactory { fixtures -> ArtNetSender(fixtures) } }
    single { AppViewModel(get(), get(), get(), get()) }
}
