/**
 * EN: Koin dependency injection module for the Lighthouse application.
 * Declares all singletons needed by [AppViewModel].
 *
 * Lifecycle: [AppViewModel] is a singleton tied to the application process.
 * Its [AutoCloseable.close] is called by the Compose window's DisposableEffect.
 *
 * RU: Koin-модуль зависимостей приложения Lighthouse.
 * Объявляет все синглтоны, необходимые для [AppViewModel].
 *
 * Жизненный цикл: [AppViewModel] — синглтон уровня процесса.
 * Его [AutoCloseable.close] вызывается DisposableEffect Compose-окна.
 */
package tracker.di

import org.koin.dsl.module
import tracker.app.AppViewModel
import tracker.app.TrackingPipeline
import tracker.capture.CameraSource
import tracker.detect.YuNetDetector

val appModule = module {
    single { CameraSource(deviceIndex = 0) }
    single { YuNetDetector() }
    single { TrackingPipeline(get(), get()) }
    single { AppViewModel(get(), get()) }
}
