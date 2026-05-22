package tracker.scene

import kotlinx.serialization.Serializable

/**
 * EN: Persistent configuration for one lighting setup: fixtures and optional camera calibration.
 * Serialised to `~/.lighthouse/scenes/<name>.json` by [SceneStore].
 *
 * RU: Постоянная конфигурация одной световой установки: фикстуры и опциональная калибровка
 * камеры. Сериализуется в `~/.lighthouse/scenes/<name>.json` через [SceneStore].
 *
 * @param name        human-readable scene name (used as filename) / имя сцены (используется как имя файла)
 * @param fixtures    list of DMX moving-head fixtures in this scene / список DMX-голов в сцене
 * @param calibration camera→pan/tilt mapping; null means linear fallback /
 *                    маппинг камера→pan/tilt; null — линейный fallback
 */
@Serializable
data class SceneData(
    val name: String,
    val fixtures: List<FixtureConfig>,
    val calibration: CalibrationData? = null,
)

/**
 * EN: Art-Net addressing and DMX start channel for one moving-head fixture.
 *
 * RU: Art-Net адресация и стартовый DMX-канал для одной световой головы.
 *
 * @param host         IP address of the Art-Net node / IP-адрес Art-Net узла
 * @param subnet       Art-Net subnet (0–15) / Art-Net подсеть (0–15)
 * @param universe     Art-Net universe within the subnet (0–15) / юниверс внутри подсети
 * @param startChannel 1-based DMX start channel / 1-based стартовый DMX-канал
 */
@Serializable
data class FixtureConfig(
    val host: String = "127.0.0.1",
    val subnet: Int = 0,
    val universe: Int = 0,
    val startChannel: Int = 1,
)

/**
 * EN: Four point correspondences used to compute a projective homography from
 * camera-pixel space to normalised pan/tilt space [0, 1].
 * Exactly 4 points are required by [tracker.calibration.HomographyMapper].
 *
 * RU: Четыре соответствия точек для вычисления проективной гомографии из
 * пространства пикселей камеры в нормализованное пространство pan/tilt [0, 1].
 * Ровно 4 точки требуются [tracker.calibration.HomographyMapper].
 *
 * @param points exactly 4 [CalibrationPoint] entries / ровно 4 записи [CalibrationPoint]
 */
@Serializable
data class CalibrationData(
    val points: List<CalibrationPoint>,
)

/**
 * EN: One correspondence: a pixel in the original camera frame and the pan/tilt
 * position the fixture was aimed at when that pixel was captured.
 *
 * RU: Одно соответствие: пиксель в оригинальном кадре камеры и позиция pan/tilt,
 * на которую была направлена голова при захвате этого пикселя.
 *
 * @param cameraX pixel X in original frame / пиксель X в оригинальном кадре
 * @param cameraY pixel Y in original frame / пиксель Y в оригинальном кадре
 * @param pan     fixture pan, normalised [0, 1] / нормализованный pan [0, 1]
 * @param tilt    fixture tilt, normalised [0, 1] / нормализованный tilt [0, 1]
 */
@Serializable
data class CalibrationPoint(
    val cameraX: Float,
    val cameraY: Float,
    val pan: Float,
    val tilt: Float,
)
