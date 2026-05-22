package tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import tracker.app.DetectedFrame
import tracker.repository.SceneRepository
import tracker.scene.SceneData

/**
 * EN: Full-screen composable shown on startup. Lists saved scenes and lets the user
 * load one or open the editor to create a new one.
 *
 * [onSceneSelected] is called when the user confirms a scene (either loaded or newly created).
 * The composable re-reads [repo] when [SceneEditorScreen] closes so the list stays current.
 *
 * RU: Полноэкранный компосабл, показываемый при запуске. Отображает сохранённые сцены
 * и позволяет загрузить одну из них или открыть редактор для создания новой.
 *
 * [onSceneSelected] вызывается когда пользователь подтверждает сцену (загруженную или новую).
 * Список перечитывается из [repo] после закрытия [SceneEditorScreen].
 *
 * @param repo            persistence operations for scenes / операции персистентности сцен
 * @param frameFlow       live camera frames, forwarded to [SceneEditorScreen] for the calibration preview /
 *                        живые кадры камеры, передаются в [SceneEditorScreen] для превью калибровки
 * @param onSceneSelected called with the chosen [SceneData] / вызывается с выбранной [SceneData]
 */
@Composable
fun SceneManagerScreen(
    repo: SceneRepository,
    frameFlow: StateFlow<DetectedFrame?>,
    onSceneSelected: (SceneData) -> Unit,
) {
    var scenes by remember { mutableStateOf(repo.listScenes()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingScene by remember { mutableStateOf<SceneData?>(null) }

    if (showEditor) {
        SceneEditorScreen(
            initial = editingScene,
            frameFlow = frameFlow,
            onSaved = { scene: SceneData ->
                repo.save(scene)
                scenes = repo.listScenes()
                showEditor = false
                editingScene = null
            },
            onCancelled = {
                showEditor = false
                editingScene = null
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Lighthouse", style = MaterialTheme.typography.h4, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Выберите сцену", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        if (scenes.isEmpty()) {
            Text("Нет сохранённых сцен", color = Color.Gray)
            Spacer(Modifier.height(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(0.6f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scenes) { scene ->
                    SceneRow(
                        scene = scene,
                        onLoad = { onSceneSelected(scene) },
                        onEdit = { editingScene = scene; showEditor = true },
                        onDelete = { repo.delete(scene); scenes = repo.listScenes() },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = { editingScene = null; showEditor = true }) {
            Text("Новая сцена")
        }
    }
}

@Composable
private fun SceneRow(
    scene: SceneData,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(backgroundColor = Color(0xFF16213E), elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scene.name, color = Color.White, style = MaterialTheme.typography.body1)
                val detail = buildString {
                    append("${scene.fixtures.size} fixture(s)")
                    if (scene.calibration != null) append(" · калибровка ✓") else append(" · без калибровки")
                }
                Text(detail, color = Color.Gray, style = MaterialTheme.typography.caption)
            }
            TextButton(onClick = onEdit) { Text("Изменить") }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))) {
                Text("Удалить")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onLoad) { Text("Загрузить") }
        }
    }
}
