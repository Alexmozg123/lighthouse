/**
 * EN: Application entry point. Initialises the Koin container and wires the Compose
 * window to [AppViewModel]. Navigation is driven by [AppState.screen]; frame delivery
 * and spotlight control are owned by [AppViewModel].
 *
 * RU: Точка входа приложения. Инициализирует Koin-контейнер и связывает Compose-окно
 * с [AppViewModel]. Навигация управляется [AppState.screen]; доставка кадров и управление
 * прожектором принадлежат [AppViewModel].
 */
package tracker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import tracker.app.AppScreen
import tracker.app.AppViewModel
import tracker.di.appModule
import tracker.ui.SceneEditorScreen
import tracker.ui.SceneManagerScreen
import tracker.ui.TrackingScreen

fun main() {
    startKoin { modules(appModule) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Lighthouse — face → DMX",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            val viewModel = remember { GlobalContext.get().get<AppViewModel>() }
            val state by viewModel.state.collectAsState()

            DisposableEffect(Unit) {
                onDispose { viewModel.close() }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val screen = state.screen) {
                        is AppScreen.SceneManager -> {
                            val scenes by viewModel.scenes.collectAsState()
                            SceneManagerScreen(
                                scenes = scenes,
                                onLoadScene = { viewModel.loadScene(it) },
                                onEditScene = { viewModel.navigateToSceneEditorFor(it) },
                                onDeleteScene = { viewModel.deleteScene(it) },
                                onNewScene = { viewModel.navigateToNewSceneEditor() },
                            )
                        }
                        is AppScreen.SceneEditor -> SceneEditorScreen(
                            initial = screen.scene,
                            frameFlow = viewModel.frameFlow,
                            validateCalibration = { viewModel.validateCalibration(it) },
                            isDuplicatePanTilt = { pts, pan, tilt -> viewModel.isDuplicatePanTilt(pts, pan, tilt) },
                            onSaved = { viewModel.onSceneSaved(it) },
                            onCancelled = { viewModel.navigateBack() },
                        )
                        is AppScreen.Tracking -> TrackingScreen(
                            scene = screen.scene,
                            calibrationStatus = state.calibrationStatus,
                            frameFlow = viewModel.frameFlow,
                            selectedIdFlow = viewModel.selectedIdFlow,
                            onFaceSelected = { viewModel.selectFace(it) },
                            onNavigateToEditor = { viewModel.navigateToSceneEditor() },
                            onNavigateToSceneManager = { viewModel.navigateToSceneManager() },
                        )
                    }
                }
            }
        }
    }
}
