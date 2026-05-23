package tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tracker.app.DetectedFrame
import tracker.domain.entity.CalibrationData
import tracker.domain.entity.CalibrationPoint
import tracker.domain.entity.FixtureConfig
import tracker.domain.entity.SceneData
import tracker.domain.usecase.CalibrationUseCase

/**
 * EN: Composable for creating or editing a [SceneData].
 *
 * Sections:
 * 1. Scene name field.
 * 2. Fixture configuration (one fixture for now; host / subnet / universe / start channel).
 * 3. Calibration wizard: optional 4-point camera→pan/tilt calibration.
 *    The wizard embeds a live [CameraPreview]; the user clicks a point on the frame,
 *    then manually enters the corresponding pan/tilt values (0–1) and confirms.
 *    Existing calibration can be cleared to start over.
 *
 * [frameFlow] must be provided by the caller (same pipeline as the main screen).
 *
 * RU: Компосабл для создания или редактирования [SceneData].
 *
 * Разделы:
 * 1. Поле имени сцены.
 * 2. Конфигурация фикстуры (host / subnet / universe / start channel).
 * 3. Визард калибровки: опциональная 4-точечная калибровка камера→pan/tilt.
 *    Визард встраивает живое превью [CameraPreview]; пользователь кликает точку на кадре,
 *    затем вручную вводит соответствующие pan/tilt (0–1) и подтверждает.
 *    Существующую калибровку можно сбросить и начать заново.
 *
 * [frameFlow] должен предоставляться вызывающей стороной (тот же pipeline что и на главном экране).
 *
 * @param initial              scene to pre-populate fields with, or null for a blank scene /
 *                             сцена для предзаполнения полей, null — пустая сцена
 * @param frameFlow            live camera frames for the calibration preview /
 *                             живые кадры камеры для превью калибровки
 * @param validateCalibration  validates 4 points before saving; returns failure with a message on bad geometry /
 *                             валидирует 4 точки перед сохранением; возвращает failure с сообщением при плохой геометрии
 * @param onSaved              called with the final [SceneData] / вызывается с итоговой [SceneData]
 * @param onCancelled          called when the user discards changes / вызывается при отмене
 */
@Composable
fun SceneEditorScreen(
    initial: SceneData?,
    frameFlow: StateFlow<DetectedFrame?>,
    validateCalibration: (List<CalibrationPoint>) -> Result<Unit>,
    onSaved: (SceneData) -> Unit,
    onCancelled: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val fix = initial?.fixtures?.firstOrNull() ?: FixtureConfig()
    var host by remember { mutableStateOf(fix.host) }
    var subnet by remember { mutableStateOf(fix.subnet.toString()) }
    var universe by remember { mutableStateOf(fix.universe.toString()) }
    var startCh by remember { mutableStateOf(fix.startChannel.toString()) }

    // Calibration state
    var calibPoints by remember { mutableStateOf(initial?.calibration?.points ?: emptyList<CalibrationPoint>()) }
    // Pending point: camera coords captured by a click, waiting for manual pan/tilt entry
    var pendingCamera by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingPan by remember { mutableStateOf("") }
    var pendingTilt by remember { mutableStateOf("") }
    // Validation error shown when findHomography rejects the 4 points
    var calibError by remember { mutableStateOf<String?>(null) }

    val selectedIdFlow = remember { MutableStateFlow<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)).padding(24.dp),
    ) {
        Text(
            if (initial == null) "Новая сцена" else "Редактировать: ${initial.name}",
            style = MaterialTheme.typography.h5,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Left column: scene settings ───────────────────────────────────────────
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorField("Имя сцены", name) { name = it }

                Text("Фикстура", color = Color.Gray, style = MaterialTheme.typography.caption)
                EditorField("IP-адрес (host)", host) { host = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorField("Subnet", subnet, Modifier.weight(1f), KeyboardType.Number) { subnet = it }
                    EditorField("Universe", universe, Modifier.weight(1f), KeyboardType.Number) { universe = it }
                    EditorField("Канал DMX", startCh, Modifier.weight(1f), KeyboardType.Number) { startCh = it }
                }

                Spacer(Modifier.height(8.dp))

                // ── Calibration section ───────────────────────────────────────────────
                Divider(color = Color(0xFF2A2A4A))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Калибровка (${calibPoints.size} / 4) — опционально",
                    color = Color.White,
                    style = MaterialTheme.typography.body2,
                )

                // Persistent help: what pan/tilt is and where to get the numbers
                Text(
                    "Pan — горизонтальный угол головы (0.0 = левый край, 1.0 = правый).\n" +
                    "Tilt — вертикальный угол (0.0 = вверх, 1.0 = вниз).\n" +
                    "Значения берёте из QLC+: канал 0–255 → делите на 255. Пример: канал 128 → 0.50.",
                    color = Color(0xFF9E9E9E),
                    style = MaterialTheme.typography.caption,
                )
                Spacer(Modifier.height(4.dp))

                if (calibPoints.isEmpty() && pendingCamera == null) {
                    Text(
                        "Как сделать: направьте голову на точку в QLC+, запишите Pan/Tilt, " +
                        "потом кликните на ту же точку на превью справа и введите значения. " +
                        "Нужно 4 разные точки — в углах площадки.",
                        color = Color(0xFF9E9E9E),
                        style = MaterialTheme.typography.caption,
                    )
                }

                // Already confirmed points
                calibPoints.forEachIndexed { i, pt ->
                    Text(
                        "  ✓ ${i + 1}. pan=${pt.pan}  tilt=${pt.tilt}",
                        color = Color(0xFF81C784),
                        style = MaterialTheme.typography.caption,
                    )
                }

                if (calibPoints.size < 4) {
                    if (pendingCamera == null) {
                        Text(
                            "→ Направьте голову на точку ${calibPoints.size + 1}, запишите pan/tilt из QLC+, " +
                            "затем кликните на эту точку на превью справа.",
                            color = Color(0xFFFFCC80),
                            style = MaterialTheme.typography.body2,
                        )
                    } else {
                        Text(
                            "Точка ${calibPoints.size + 1} на превью зафиксирована. " +
                            "Введите pan и tilt из QLC+ для этой позиции:",
                            color = Color(0xFFFFCC80),
                            style = MaterialTheme.typography.body2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditorField(
                                "Pan (0.0–1.0)\nнапр. 128/255=0.50",
                                pendingPan, Modifier.weight(1f), KeyboardType.Decimal,
                            ) { pendingPan = it }
                            EditorField(
                                "Tilt (0.0–1.0)\nнапр. 64/255=0.25",
                                pendingTilt, Modifier.weight(1f), KeyboardType.Decimal,
                            ) { pendingTilt = it }
                        }
                        val pan = pendingPan.toFloatOrNull()?.coerceIn(0f, 1f)
                        val tilt = pendingTilt.toFloatOrNull()?.coerceIn(0f, 1f)
                        val isDuplicate = pan != null && tilt != null &&
                            CalibrationUseCase.isDuplicatePanTilt(calibPoints, pan, tilt)
                        if (isDuplicate) {
                            Text(
                                "Такие pan/tilt уже есть в списке — у каждой точки должны быть разные значения.",
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.caption,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (pan != null && tilt != null && !isDuplicate) {
                                        calibPoints = calibPoints + CalibrationPoint(
                                            cameraX = pendingCamera!!.first,
                                            cameraY = pendingCamera!!.second,
                                            pan = pan,
                                            tilt = tilt,
                                        )
                                        pendingCamera = null
                                        pendingPan = ""
                                        pendingTilt = ""
                                        calibError = null
                                    }
                                },
                                enabled = pan != null && tilt != null && !isDuplicate,
                            ) { Text("Зафиксировать") }
                            TextButton(onClick = { pendingCamera = null; pendingPan = ""; pendingTilt = "" }) {
                                Text("Отменить точку", color = Color.Gray)
                            }
                        }
                    }
                }

                if (calibPoints.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            calibPoints = emptyList()
                            pendingCamera = null; pendingPan = ""; pendingTilt = ""
                            calibError = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679)),
                    ) { Text("Сбросить калибровку") }
                }

                if (calibError != null) {
                    Text(
                        calibError!!,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.caption,
                    )
                }
            }

            // ── Right column: camera preview + calibration overlay ────────────────────
            Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9f)) {
                CameraPreview(
                    state = frameFlow,
                    selectedFaceId = selectedIdFlow,
                    onFaceSelected = {},
                    onRawClick = { x, y ->
                        if (calibPoints.size < 4 && pendingCamera == null) {
                            pendingCamera = x to y
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                val frameForOverlay by frameFlow.collectAsState()
                CalibrationOverlay(
                    frame = frameForOverlay,
                    confirmedPoints = calibPoints,
                    pendingPoint = pendingCamera,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val nameOk = name.isNotBlank()
            val subnetVal = subnet.toIntOrNull()?.coerceIn(0, 15) ?: 0
            val universeVal = universe.toIntOrNull()?.coerceIn(0, 15) ?: 0
            val startChVal = startCh.toIntOrNull()?.coerceIn(1, 512) ?: 1

            TextButton(onClick = onCancelled) { Text("Отмена", color = Color.Gray) }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    val calibData = if (calibPoints.size == 4) {
                        val result = validateCalibration(calibPoints)
                        if (result.isFailure) {
                            calibError = "Калибровка недействительна: точки слишком близко или лежат на одной прямой. " +
                                "Расположите 4 точки в углах площадки (как крест или прямоугольник)."
                            return@Button
                        }
                        CalibrationData(calibPoints)
                    } else null
                    calibError = null
                    onSaved(SceneData(
                        name = name.trim(),
                        fixtures = listOf(FixtureConfig(host.trim(), subnetVal, universeVal, startChVal)),
                        calibration = calibData,
                    ))
                },
                enabled = nameOk,
            ) { Text("Сохранить") }
        }
    }
}

/**
 * EN: Canvas overlay drawn on top of the calibration preview. Renders confirmed calibration
 * points as numbered cyan circles, the pending (clicked but not yet confirmed) point as an
 * orange circle, and a polygon connecting the confirmed points so the covered area is visible.
 *
 * All coordinates are in original image-pixel space and are mapped to canvas space via
 * [computeLetterbox], matching the `ContentScale.Fit` transform of the [CameraPreview] below.
 *
 * RU: Canvas-оверлей поверх превью калибровки. Рисует подтверждённые точки как нумерованные
 * голубые кружки, ожидающую точку (кликнутую, но не подтверждённую) — оранжевым кружком,
 * и многоугольник, соединяющий подтверждённые точки, чтобы была видна охваченная область.
 *
 * Все координаты в пикселях исходного кадра; маппинг в canvas через [computeLetterbox].
 *
 * @param frame           current camera frame for image dimensions / текущий кадр для размеров изображения
 * @param confirmedPoints calibration points already confirmed by the user / подтверждённые точки калибровки
 * @param pendingPoint    clicked-but-not-yet-confirmed image-space point, or null /
 *                        кликнутая, но ещё не подтверждённая точка, или null
 */
@Composable
private fun CalibrationOverlay(
    frame: DetectedFrame?,
    confirmedPoints: List<CalibrationPoint>,
    pendingPoint: Pair<Float, Float>?,
    modifier: Modifier = Modifier,
) {
    if (frame == null && confirmedPoints.isEmpty() && pendingPoint == null) return
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(modifier = modifier.onSizeChanged { containerSize = it }) {
        val f = frame ?: return@Canvas
        if (containerSize.width == 0 || containerSize.height == 0) return@Canvas

        val lb = computeLetterbox(size.width, size.height, f.imageWidth, f.imageHeight)
        fun ox(x: Float) = lb.offsetX + x * lb.scale
        fun oy(y: Float) = lb.offsetY + y * lb.scale

        val confirmed = confirmedPoints.map { Offset(ox(it.cameraX), oy(it.cameraY)) }
        val pending = pendingPoint?.let { Offset(ox(it.first), oy(it.second)) }

        // Sort points by angle around centroid so the polygon is never self-intersecting
        // regardless of the order the user clicked them.
        val hull = if (confirmed.size >= 3) {
            val cx = confirmed.map { it.x }.average().toFloat()
            val cy = confirmed.map { it.y }.average().toFloat()
            confirmed.sortedBy { kotlin.math.atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        } else confirmed

        if (hull.size >= 3) {
            val path = Path().apply {
                moveTo(hull[0].x, hull[0].y)
                for (i in 1 until hull.size) lineTo(hull[i].x, hull[i].y)
                close()
            }
            drawPath(path, Color.Cyan.copy(alpha = 0.12f))
            drawPath(path, Color.Cyan.copy(alpha = 0.55f), style = Stroke(width = 2f))
        } else if (confirmed.size == 2) {
            drawLine(Color.Cyan.copy(alpha = 0.55f), confirmed[0], confirmed[1], strokeWidth = 2f)
        }

        // Confirmed points — cyan circles with numbers
        confirmed.forEachIndexed { i, pt ->
            drawCircle(Color.Cyan, radius = 10f, center = pt)
            drawCircle(Color(0xFF0D1B2A), radius = 7f, center = pt)
            // Number label drawn via a small filled circle as background; text via separate layer
            drawCircle(Color.Cyan, radius = 10f, center = pt, style = Stroke(width = 2f))
        }

        // Pending point — orange circle
        if (pending != null) {
            drawCircle(Color(0xFFFF9800).copy(alpha = 0.3f), radius = 14f, center = pending)
            drawCircle(Color(0xFFFF9800), radius = 10f, center = pending, style = Stroke(width = 2.5f))
            drawCircle(Color(0xFFFF9800), radius = 3f, center = pending)
        }
    }

    // Render point numbers as Text composables (Canvas drawText not available in Compose)
    if (frame != null && containerSize.width > 0) {
        val lb = computeLetterbox(
            containerSize.width.toFloat(), containerSize.height.toFloat(),
            frame.imageWidth, frame.imageHeight,
        )
        Box(modifier = modifier) {
            confirmedPoints.forEachIndexed { i, pt ->
                val cx = lb.offsetX + pt.cameraX * lb.scale
                val cy = lb.offsetY + pt.cameraY * lb.scale
                Text(
                    text = "${i + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.offset { IntOffset((cx - 4f).roundToInt(), (cy - 8f).roundToInt()) },
                )
            }
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            unfocusedBorderColor = Color.Gray,
            focusedBorderColor = Color(0xFF90CAF9),
            cursorColor = Color(0xFF90CAF9),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
