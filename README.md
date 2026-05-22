# Lighthouse

Desktop-приложение, которое видит все лица на сцене через веб-камеру, позволяет выбрать одно из них кликом и управляет световой головой по DMX так, чтобы луч следил за выбранным человеком.

Стек: Kotlin / JVM, Compose Multiplatform for Desktop, OpenCV YuNet (через JavaCV), Art-Net для DMX, Koin для DI.

## Текущий статус

- [x] Bootstrap проекта (Gradle, Compose, JavaCV).
- [x] Захват веб-камеры и превью (1280×720, avfoundation на macOS).
- [x] YuNet face detection — все лица в кадре одновременно, bbox + 5 keypoints.
- [x] UI выбора POI: клик по лицу → это лицо становится целью прожектора.
- [x] Face tracking: стабильные ID между кадрами, чтобы выбор не терялся.
- [x] Art-Net DMX sender + DmxFixture (artnet4j, unicast, multi-fixture ready).
- [x] Сцены: сохранение/загрузка конфигурации фикстур и калибровки в `~/.lighthouse/scenes/`.
- [x] 4-точечная homography калибровка камера → pan/tilt (с визуальным визардом и полигоном покрытия).
- [x] Clean Architecture: AppState + AppViewModel + Koin DI; Main.kt — тонкий wire-up.
- [ ] One-Euro filter сглаживания.
- [ ] Edge cases: лицо пропало (blackout работает; hold-last в планах), потеря камеры.

## Требования

- macOS, Linux или Windows.
- JDK 21 (Temurin рекомендован).
- Веб-камера.
- Опционально: [QLC+](https://www.qlcplus.org/) для отладки без физической световой головы.

## Установка

```bash
git clone <repo>
cd lighthouse
```

JDK 21 ставим один раз. macOS:
```bash
brew install --cask temurin@21
```

ONNX-модели хранятся в Git LFS. Установи его до клонирования, иначе вместо модели скачается текстовый указатель и приложение упадёт на старте:
```bash
brew install git-lfs
git lfs install
```

Если Gradle не находит JDK 21 автоматически, пропиши путь в `~/.gradle/gradle.properties` (per-machine, в репо не лежит):
```properties
org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

## Запуск

```bash
./gradlew run
```

При первом запуске macOS попросит разрешение на доступ к камере — выдай его процессу, который её открыл (Terminal, iTerm или Android Studio).

## Сборка дистрибутива

```bash
./gradlew packageDmg
./gradlew packageDistributionForCurrentOS
```

## Архитектура

```
SceneManagerScreen → загрузить / создать SceneData (~/.lighthouse/scenes/)
       │
       ▼
CameraSource (JavaCV) → TrackingPipeline → StateFlow<DetectedFrame>
                              │                      │
                         YuNetDetector          CameraPreview (Compose)
                       (все лица в кадре)       Image + FaceOverlay
                              │
                     SpotlightController
                    (выбранное лицо → pan/tilt)
                              │
                    HomographyMapper (опционально)
                    4-точечная калибровка камера→pan/tilt
                    или линейный fallback если нет калибровки
                              │
                      List<DmxFixture>   ← по одному на голову
                              │
                      ArtNetClient (artnet4j)
                       unicastDmx → QLC+ / голова
```

Конвейер живёт в одном `Flow` на `Dispatchers.IO`. UI подписывается на `StateFlow` и перерисовывает превью с оверлеем поверх кадра. На каждый кадр `SpotlightController` пересчитывает координаты центра выбранного лица через гомографическую матрицу (или линейно если калибровки нет) в 16-bit pan/tilt и отправляет Art-Net пакет по unicast на каждую зарегистрированную голову.

## Структура проекта

```
gradle/libs.versions.toml     — version catalog
build.gradle.kts              — Kotlin 2.0 + Compose 1.7 + JavaCV + Koin
src/main/kotlin/tracker/
    Main.kt                   — тонкий wire-up: startKoin + when(AppState.screen)
    di/AppModule.kt           — Koin-модуль: CameraSource, YuNetDetector, Pipeline, AppViewModel
    app/AppState.kt           — AppState, AppScreen, CalibrationStatus (sealed, immutable)
    app/AppViewModel.kt       — StateHolder: навигация, spotlight lifecycle, pipeline-подписка
    app/TrackingPipeline.kt   — Flow<DetectedFrame>: capture → detect → emit
    capture/                  — захват с веб-камеры
    detect/                   — YuNet детекция + стабильные ID (FaceTracker)
    scene/                    — SceneData, SceneStore (~/.lighthouse/scenes/)
    calibration/              — HomographyMapper (findHomography + map)
    ui/                       — CameraPreview, TrackingScreen, SceneManagerScreen, SceneEditorScreen
    dmx/                      — Art-Net: DmxFixture, SpotlightController
src/main/resources/models/    — ONNX-модели (YuNet)
```

## Разработка

Все версии зависимостей — в [gradle/libs.versions.toml](gradle/libs.versions.toml). Шпаргалка по проекту и грабли — в [CLAUDE.md](CLAUDE.md).

```bash
./gradlew compileKotlin                   # быстрая проверка ошибок
./gradlew run                             # запустить
./gradlew tasks --group "compose desktop"
./gradlew --stop                          # рестарт демона
```

## Лицензия

TBD.
