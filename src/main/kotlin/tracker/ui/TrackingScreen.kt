/**
 * EN: Full-screen composable shown while a scene is active and face tracking is running.
 * Displays the live camera preview with face overlay and a floating toolbar.
 *
 * RU: Полноэкранный компосабл, показываемый пока сцена активна и идёт трекинг лиц.
 * Отображает живое превью камеры с оверлеем лиц и плавающую панель управления.
 *
 * @param scene                   EN: active scene / RU: активная сцена
 * @param calibrationStatus       EN: current coordinate-mapping mode / RU: текущий режим маппинга координат
 * @param frameFlow               EN: live camera frames / RU: живые кадры камеры
 * @param selectedIdFlow          EN: ID of the currently selected face / RU: ID выбранного лица
 * @param onFaceSelected          EN: called when user taps a face / RU: вызывается при нажатии на лицо
 * @param onNavigateToEditor      EN: opens the scene editor / RU: открывает редактор сцены
 * @param onNavigateToSceneManager EN: returns to the scene list / RU: возвращает к списку сцен
 */
package tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import tracker.app.CalibrationStatus
import tracker.app.DetectedFrame
import tracker.scene.SceneData

@Composable
fun TrackingScreen(
    scene: SceneData,
    calibrationStatus: CalibrationStatus,
    frameFlow: StateFlow<DetectedFrame?>,
    selectedIdFlow: StateFlow<Int?>,
    onFaceSelected: (Int) -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToSceneManager: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            state = frameFlow,
            selectedFaceId = selectedIdFlow,
            onFaceSelected = onFaceSelected,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                scene.name,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.caption,
            )
            when (calibrationStatus) {
                CalibrationStatus.Active -> Text(
                    "✓ Калибровка",
                    color = Color(0xFF81C784),
                    style = MaterialTheme.typography.caption,
                )
                CalibrationStatus.Fallback -> Text(
                    "⚠ Линейный режим",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.caption,
                )
                CalibrationStatus.None -> {}
            }
            Button(
                onClick = onNavigateToEditor,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("Изменить") }
            Button(
                onClick = onNavigateToSceneManager,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("Сцены") }
        }
    }
}
