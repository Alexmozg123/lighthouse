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
import tracker.domain.entity.SceneData

/**
 * EN: Full-screen composable shown on startup. Lists saved scenes and lets the user
 * load one, edit it, delete it, or create a new one.
 *
 * All mutations (save, delete) and all navigation decisions are delegated to
 * [AppViewModel][tracker.app.AppViewModel] via callbacks — this composable is purely
 * presentational.
 *
 * RU: Полноэкранный компосабл, показываемый при запуске. Отображает сохранённые сцены
 * и позволяет загрузить, отредактировать, удалить или создать новую.
 *
 * Все мутации (сохранение, удаление) и навигационные решения делегируются
 * [AppViewModel][tracker.app.AppViewModel] через колбэки — компосабл только отображает данные.
 *
 * @param scenes        current list of saved scenes / текущий список сохранённых сцен
 * @param onLoadScene   called when the user picks a scene to activate / вызывается при выборе сцены для активации
 * @param onEditScene   called when the user wants to edit a scene / вызывается при запросе редактирования
 * @param onDeleteScene called when the user deletes a scene / вызывается при удалении сцены
 * @param onNewScene    called when the user wants to create a new scene / вызывается при создании новой сцены
 */
@Composable
fun SceneManagerScreen(
    scenes: List<SceneData>,
    onLoadScene: (SceneData) -> Unit,
    onEditScene: (SceneData) -> Unit,
    onDeleteScene: (SceneData) -> Unit,
    onNewScene: () -> Unit,
) {
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
                        onLoad = { onLoadScene(scene) },
                        onEdit = { onEditScene(scene) },
                        onDelete = { onDeleteScene(scene) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = onNewScene) {
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
