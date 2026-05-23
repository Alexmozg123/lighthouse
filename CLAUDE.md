# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Lighthouse — face-tracking → DMX moving head

Kotlin/JVM desktop-приложение: веб-камера → детекция лица → выбор точки интереса → DMX (Art-Net) на световую голову, чтобы луч следил за выбранной точкой. Физическая голова есть; отладка также возможна через виртуальный Art-Net приёмник (QLC+).

## Стек

| Слой | Технология |
|---|---|
| Язык / JVM | Kotlin 2.0.20, JDK 21 (Temurin) |
| Сборка | Gradle 9 + Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| UI | Compose Multiplatform for Desktop 1.7.0 |
| Захват камеры | JavaCV 1.5.10 (FFmpegFrameGrabber + avfoundation на macOS) |
| Детекция лиц | OpenCV YuNet (`FaceDetectorYN`) — до ~6 лиц одновременно, 5 keypoints |
| DMX (план) | Art-Net через ArtNet4J |
| Конкурентность | Kotlin coroutines + `Flow` |
| DI | Koin 3.5.6 (`koin-core`) |

## Команды

```
./gradlew run                          # запустить приложение
./gradlew compileKotlin                # быстрая проверка ошибок
./gradlew tasks --group "compose desktop"
./gradlew --stop                       # рестарт демона
```

Тестов пока нет. Вся проверка — `compileKotlin` + ручной запуск.

## Структура и поток данных

```
CameraSource          — конфигурирует FrameGrabber (1280×720 @ 30fps, avfoundation на macOS)
  └─ TrackingPipeline — Flow<DetectedFrame> на Dispatchers.IO:
        grab() → Mat (детектор) + BufferedImage (UI) в одном цикле
        YuNetDetector.detect(mat) → FaceDetection?
        emit(DetectedFrame(ImageBitmap, w, h, FaceDetection?))
  └─ CameraPreview    — Image + Canvas FaceOverlay
        FaceOverlay   — перерисовывает bbox + 5 keypoints с учётом letterboxing
```

```
src/main/kotlin/tracker/
    Main.kt                                      # startKoin + when(AppState.screen)
    di/AppModule.kt                              # Koin: wire-up всех синглтонов

    # ── Domain (нет зависимостей на adapter/ui) ──────────────────────────────
    domain/entity/FaceDetection.kt               # bbox + 5 keypoints + score
    domain/entity/TrackedFace.kt                 # id + FaceDetection
    domain/entity/SceneData.kt                   # SceneData, FixtureConfig, CalibrationData, CalibrationPoint
    domain/entity/DmxFixture.kt                  # 16-bit pan/tilt DMX-буфер
    domain/entity/PanTilt.kt                     # нормализованные pan/tilt [0,1]
    domain/usecase/PositionMapper.kt              # интерфейс map(px,py)→(pan,tilt)
    domain/usecase/MapperFactory.kt              # output port: CalibrationData→Result<PositionMapper>
    domain/usecase/DmxSender.kt                  # output port: send(PanTilt?) к фикстурам
    domain/usecase/DmxSenderFactory.kt           # output port: создаёт DmxSender per-scene
    domain/usecase/FaceTracker.kt                # стабильные ID (IoU фаза1 + centroid фаза2)
    domain/usecase/CalibrationUseCase.kt         # isDuplicatePanTilt + buildMapper(points, MapperFactory)
    domain/repository/SceneRepository.kt         # интерфейс персистентности сцен

    # ── Adapter (зависит от domain) ──────────────────────────────────────────
    adapter/camera/CameraSource.kt               # фабрика FrameGrabber (avfoundation / OpenCV)
    adapter/camera/YuNetDetector.kt              # YuNet через JavaCV (FaceDetectorYN)
    adapter/calibration/HomographyMapper.kt      # findHomography + реализует PositionMapper
    adapter/dmx/ArtNetSender.kt                  # ArtNetClient + DmxFixture → unicastDmx
    adapter/persistence/SceneStore.kt            # JSON-файлы ~/.lighthouse/scenes/

    # ── App (orchestration, зависит от domain + adapter) ─────────────────────
    app/AppState.kt                              # AppScreen (sealed), CalibrationStatus, AppState
    app/AppViewModel.kt                          # навигация, scenes StateFlow, spotlight lifecycle
    app/TrackingPipeline.kt                      # Flow<DetectedFrame>: capture → detect → emit; владеет YuNetDetector
    app/FacePositionResolver.kt                  # resolve(frame,id,mapper?)→PanTilt?
    app/SpotlightController.kt                   # FacePositionResolver + DmxSender (оркестрация)

    # ── UI (только Compose, зависит от app + domain) ─────────────────────────
    ui/CameraPreview.kt                          # Image + FaceOverlay; onRawClick для калибровки
    ui/TrackingScreen.kt                         # превью + плавающий тулбар
    ui/SceneManagerScreen.kt                     # список сцен (только отображение, без repo)
    ui/SceneEditorScreen.kt                      # редактор + 4-точечный калибровочный визард

src/main/resources/models/
    face_detection_yunet_2023mar.onnx
```

## Архитектура пакетов

Проект следует Clean Architecture с правилом направления зависимостей:

```
domain  ←─── adapter ←─── app ←─── ui
  ↑                         │
  └─────────────────────────┘ (app зависит от domain напрямую)
```

- **`domain/`** — чистые бизнес-объекты и правила; никаких зависимостей на JavaCV, OpenCV, artnet4j, Compose.
- **`adapter/`** — реализации интерфейсов из domain; зависит от нативных библиотек.
- **`app/`** — точка сборки: AppViewModel связывает adapter и domain; TrackingPipeline — единственное место, где `DetectedFrame` (с `ImageBitmap`) пересекает границу adapter→app; `FacePositionResolver` и `SpotlightController` — оркестрация domain+adapter.
- **`ui/`** — Compose-экраны; зависят только от `app/` и `domain/entity/`; не импортируют `adapter/` напрямую.

## Архитектурные принципы

- **Frame из JavaCV grabber — одноразовый**: каждый `grab()` перезаписывает буфер. Поэтому Mat (для детектора) и BufferedImage (для UI) получаем в одном цикле до следующего grab. См. `TrackingPipeline.frames()`.
- **Detection coords = пиксели исходного кадра**. UI mapping в `FaceOverlay` учитывает `ContentScale.Fit` (letterboxing): пересчёт через `offset + p * scale`.
- **ONNX/модели — в ресурсах**, на старте копируем в tmp-файл, отдаём путь нативной библиотеке (JavaCV/ONNX Runtime требуют файл на диске, не InputStream).
- **Все лица в кадре**: `YuNetDetector.detect()` возвращает `List<FaceDetection>` — все обнаруженные лица без фильтрации. `DetectedFrame.faces` — список.
- **Lifecycle-цепочка**: `AppViewModel.close()` → `scope.cancel()` (останавливает сбор pipeline) → `pipeline.close()` → `detector.close()`. Детектор закрывается последним; `synchronized` в `YuNetDetector` защищает от use-after-free если IO-поток ещё внутри `detect()`.
- **AppViewModel — единственная точка владения ресурсами** (`TrackingPipeline` + его `YuNetDetector`, `SpotlightController`). Koin создаёт его как синглтон (`single {}`); `close()` вызывается ровно один раз при закрытии окна. `startKoin` вызывается **до** `application {}`, чтобы не пересоздаваться при рекомпозиции.
- **Единая навигационная система**: вся навигация идёт через `AppState.screen` и `AppViewModel`. `SceneManagerScreen` не содержит внутренних экранов — переход в редактор всегда через `AppScreen.SceneEditor(fromManager=true/false)`.
- **SceneManagerScreen — pure UI**: никаких прямых вызовов `repo.save/delete`; только колбэки `onLoadScene/onEditScene/onDeleteScene/onNewScene`, реализованные в ViewModel.
- **AppViewModel владеет сценами**: `scenes: StateFlow<List<SceneData>>` обновляется после каждого save/delete. UI подписывается на поток, не читает repo напрямую.

## Запуск

```
./gradlew run
```

Первый запуск — macOS попросит разрешение на камеру (привязывается к процессу, который её открыл: Terminal / iTerm / Android Studio).

## Грабли, которые уже наступали

- **Compose `run` запускался на JDK демона (17), а компилировал на toolchain (21)** → `UnsupportedClassVersionError`. Решено: в `~/.gradle/gradle.properties` прописан `org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`. Это per-machine, в репо не лежит.
- **`application` plugin конфликтует с Compose Desktop** — оба регистрируют `:run`. Используем только Compose-таск.
- **JavaCV-platform тянет нативки на все ОС (~1 ГБ)** → в `build.gradle.kts` ставим `javacpp.platform` под текущую машину.
- **Compose 1.7: `BufferedImage.toComposeImageBitmap` неоднозначен** с Skia-шным `Image.toComposeImageBitmap`. Спасает явная типизация `val bi: BufferedImage = …`.
- **Dmg/jpackage не принимает версии `0.x.y`** — MAJOR должен быть > 0. Поэтому `version = "1.0.0"`.
- **Gradle 9 + foojay-resolver 0.8.0** падает с `IBM_SEMERU`. Использовать 0.10.0+ или ставить JDK руками.
- **`FaceDetectorYN.create` в JavaCV 1.5.10** требует 8 аргументов (добавили `backend_id`, `target_id`), а не 6 как в других обёртках.
- **SIGSEGV при закрытии окна (exit 134)**: `DisposableEffect.onDispose` вызывает `detector.close()` на главном потоке, пока IO-поток ещё внутри нативного `FaceDetectorYN.detect()` — use-after-free в `libopencv_dnn`. Решено: `detect()` и `close()` в `YuNetDetector` обёрнуты в `synchronized(lock)` с `@Volatile closed` флагом.
- **`findHomography` возвращает пустой Mat на вырожденных точках**: если 4 точки коллинеарны или все destination (pan/tilt) одинаковы — `createIndexer()` падает с NPE (`ptr is null`). Решено: `check(!H.isNull && !H.empty())` в `HomographyMapper.init`; `runCatching` в `Main.kt` при создании маппера; валидация дубликатов и пробный `HomographyMapper` перед сохранением в `SceneEditorScreen`.
- **`derivedStateOf` не подходит для ресурсов с lifecycle**: `SpotlightController` (держит `ArtNetClient`) создавался в `derivedStateOf` — при исключении внутри него падал весь AWT-поток. Заменено на `DisposableEffect(activeScene)` с явным `close()` в `onDispose`.

## Документация в коде

Весь Kotlin-код должен иметь KDoc-комментарии на **двух языках**: сначала английский блок, затем русский. Структура для классов и функций:

```kotlin
/**
 * EN: English description.
 * Additional EN detail.
 *
 * RU: Описание на русском.
 * Дополнительная деталь на русском.
 *
 * @param foo EN description / RU описание
 * @return  EN description / RU описание
 */
```

Правила:
- **Классы** — KDoc обязателен: назначение, контракт потокобезопасности (если есть), важные инварианты.
- **Публичные и internal функции** — KDoc обязателен: что делает, параметры (`@param`), возвращаемое значение (`@return`), исключения если бросает.
- **Приватные вспомогательные функции** — KDoc если логика нетривиальна.
- **Data-классы и их поля** — KDoc на классе; инлайн-комментарий на свойстве если смысл неочевиден.
- **Константы** — однострочный KDoc если имя недостаточно самодокументируемо.
- Не дублировать в KDoc то, что очевидно из сигнатуры. Документировать *почему*, а не *что*.

## Что не сделано

- One-Euro filter сглаживания движения выбранного лица.
- Edge cases: лицо пропало (сейчас blackout; hold-last — в планах), потеря камеры.
