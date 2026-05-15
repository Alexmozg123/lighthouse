# Lighthouse

Desktop-приложение, которое следит за выбранной точкой на лице через веб-камеру и управляет световой головой по DMX, чтобы её луч ехал за этой точкой.

Стек: Kotlin / JVM, Compose Multiplatform for Desktop, OpenCV (через JavaCV), MediaPipe FaceMesh (через ONNX Runtime), Art-Net для DMX.

## Возможности

- Захват видео с веб-камеры в реальном времени.
- Детекция лица и ключевых точек (YuNet → MediaPipe FaceMesh, 468 landmark'ов).
- Выбор любой точки на лице как «точку интереса».
- 4-точечная калибровка камеры в pan/tilt пространство световой головы (homography).
- Сглаживание движения (One-Euro filter), чтобы голова не дёргалась.
- Отправка DMX по Art-Net на физический Art-Net node, OLA или виртуальный приёмник (QLC+).

Текущий статус — см. [раздел «Дорожная карта»](#дорожная-карта).

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
./gradlew packageDmg              # macOS .dmg
./gradlew packageDistributionForCurrentOS
```

## Архитектура

```
┌──────────────┐  Frame   ┌──────────────┐  Landmarks  ┌──────────────┐  norm xy  ┌──────────────┐  DMX  ┌──────────────┐
│ CameraSource │ ───────▶ │ FaceDetector │ ──────────▶ │  Smoother    │ ────────▶ │   Mapper     │ ────▶ │ ArtNetSender │
│  (JavaCV)    │          │ (YuNet+Mesh) │             │ (One-Euro)   │           │ (homography) │       │  (ArtNet4J)  │
└──────────────┘          └──────────────┘             └──────────────┘           └──────────────┘       └──────────────┘
        │                         │                            │                          │
        └─────── StateFlow<DetectedFrame> ─────────────────────┴── UI overlay (Compose) ──┘
```

Конвейер живёт в одном `Flow`, на каждой стадии работает в своём `Dispatcher`. UI подписывается на `StateFlow` и перерисовывает превью с оверлеем поверх.

## Структура проекта

```
gradle/libs.versions.toml     — version catalog (единая точка для всех зависимостей)
build.gradle.kts              — Kotlin 2.0 + Compose 1.7 + JavaCV + ONNX Runtime
settings.gradle.kts
src/main/kotlin/tracker/
    Main.kt                   — Compose entry-point
    capture/                  — захват с веб-камеры
    detect/                   — детекция лица и landmark'ов
    smooth/                   — One-Euro filter
    calibration/              — homography камера → pan/tilt
    dmx/                      — Art-Net и описание fixture
    app/                      — TrackingPipeline, координация
    ui/                       — Compose-компоненты
src/main/resources/models/    — ONNX-модели
```

## Конфигурация фикстуры

DMX-карта световой головы описывается через `DmxFixture` — каналы pan, pan-fine, tilt, tilt-fine, dimmer и базовый адрес во вселенной. Перед использованием убедись, что значения совпадают с твоей моделью головы.

## Калибровка

В UI: «Калибровка» → последовательно навести луч на 4 угла кадра камеры (вручную крутишь pan/tilt слайдерами), зафиксировать каждый угол. Приложение посчитает `Imgproc.findHomography` и сохранит матрицу в `~/.lighthouse/calib.json`. Калибровать заново нужно при любом изменении взаимного положения камеры и головы.

## Дорожная карта

- [x] Bootstrap проекта (Gradle, Compose, JavaCV).
- [x] Захват веб-камеры и превью.
- [x] YuNet face detection с 5 keypoints.
- [ ] MediaPipe FaceMesh (468 landmark'ов) через ONNX Runtime.
- [ ] UI выбора точки интереса кликом.
- [ ] Art-Net DMX sender.
- [ ] 4-точечная homography калибровка.
- [ ] One-Euro filter.
- [ ] Edge cases: лицо пропало, потеря камеры, blackout.

## Разработка

Все версии зависимостей — в [gradle/libs.versions.toml](gradle/libs.versions.toml). Шпаргалка по проекту и грабли, на которые уже наступали, — в [CLAUDE.md](CLAUDE.md).

Полезные таски Gradle:
```bash
./gradlew compileKotlin
./gradlew tasks --group "compose desktop"
./gradlew --stop                                  # рестарт демона
```

## Лицензия

TBD.
