# Lighthouse

Desktop-приложение, которое видит все лица на сцене через веб-камеру, позволяет выбрать одно из них кликом и управляет световой головой по DMX так, чтобы луч следил за выбранным человеком.

Стек: Kotlin / JVM, Compose Multiplatform for Desktop, OpenCV YuNet (через JavaCV), Art-Net для DMX.

## Текущий статус

- [x] Bootstrap проекта (Gradle, Compose, JavaCV).
- [x] Захват веб-камеры и превью (1280×720, avfoundation на macOS).
- [x] YuNet face detection — все лица в кадре одновременно, bbox + 5 keypoints.
- [x] UI выбора POI: клик по лицу → это лицо становится целью прожектора.
- [x] Face tracking: стабильные ID между кадрами, чтобы выбор не терялся.
- [ ] Art-Net DMX sender + DmxFixture.
- [ ] 4-точечная homography калибровка камера → pan/tilt.
- [ ] One-Euro filter сглаживания.
- [ ] Edge cases: лицо пропало, потеря камеры, blackout.

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
CameraSource (JavaCV) → TrackingPipeline → StateFlow<DetectedFrame>
                              │                      │
                         YuNetDetector          CameraPreview (Compose)
                       (все лица в кадре)       Image + FaceOverlay
```

Конвейер живёт в одном `Flow` на `Dispatchers.IO`. UI подписывается на `StateFlow` и перерисовывает превью с оверлеем поверх кадра.

## Структура проекта

```
gradle/libs.versions.toml     — version catalog
build.gradle.kts              — Kotlin 2.0 + Compose 1.7 + JavaCV
src/main/kotlin/tracker/
    Main.kt                   — Compose entry-point
    capture/                  — захват с веб-камеры
    detect/                   — YuNet детекция лиц
    app/                      — TrackingPipeline, DetectedFrame
    ui/                       — Compose-компоненты
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
