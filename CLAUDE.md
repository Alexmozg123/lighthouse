# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Lighthouse — face-tracking → DMX moving head

Kotlin/JVM desktop-приложение: веб-камера → детекция лица → выбор точки интереса → DMX (Art-Net) на световую голову, чтобы луч следил за выбранной точкой. Сейчас в разработке; физической головы нет, отладка через виртуальный Art-Net приёмник (QLC+).

## Стек

| Слой | Технология |
|---|---|
| Язык / JVM | Kotlin 2.0.20, JDK 21 (Temurin) |
| Сборка | Gradle 9 + Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| UI | Compose Multiplatform for Desktop 1.7.0 |
| Захват камеры | JavaCV 1.5.10 (FFmpegFrameGrabber + avfoundation на macOS) |
| Детекция лица | OpenCV YuNet (`FaceDetectorYN`) — 5 keypoints |
| Landmarks (в работе) | MediaPipe FaceMesh → конвертация TFLite→ONNX → ONNX Runtime Java |
| DMX (план) | Art-Net через ArtNet4J |
| Конкурентность | Kotlin coroutines + `Flow` |

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
    Main.kt                       # Compose entry, поднимает TrackingPipeline
    capture/CameraSource.kt       # фабрика FrameGrabber'а
    detect/FaceDetection.kt       # DTO: bbox + 5 keypoints в координатах кадра
    detect/YuNetDetector.kt       # YuNet через JavaCV (FaceDetectorYN)
    app/TrackingPipeline.kt       # единый Flow<DetectedFrame>: capture → detect → emit
    ui/CameraPreview.kt           # Image + Canvas-оверлей (bbox + keypoints)
src/main/resources/models/
    face_detection_yunet_2023mar.onnx
```

## Архитектурные принципы

- **Frame из JavaCV grabber — одноразовый**: каждый `grab()` перезаписывает буфер. Поэтому Mat (для детектора) и BufferedImage (для UI) получаем в одном цикле до следующего grab. См. `TrackingPipeline.frames()`.
- **Detection coords = пиксели исходного кадра**. UI mapping в `FaceOverlay` учитывает `ContentScale.Fit` (letterboxing): пересчёт через `offset + p * scale`.
- **ONNX/модели — в ресурсах**, на старте копируем в tmp-файл, отдаём путь нативной библиотеке (JavaCV/ONNX Runtime требуют файл на диске, не InputStream).
- **Берём самое крупное лицо**. Multi-face не поддерживается.
- **YuNetDetector реализует `AutoCloseable`** — закрывать через `DisposableEffect` в Compose (см. `Main.kt`), иначе утечка нативного детектора и tmp-файла модели.

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

## Что in-progress

- Замена YuNet keypoints (5 точек) на MediaPipe FaceMesh (468 точек) через ONNX Runtime. Сейчас идёт конвертация tflite→onnx через `tf2onnx` в `/tmp/tf2onnx-venv/`.

## Что не сделано

- Калибровка камера→pan/tilt (homography через `Imgproc.findHomography`).
- One-Euro filter сглаживания landmark'ов.
- Art-Net sender + `DmxFixture` (каналы pan/tilt/dimmer).
- UI для выбора POI кликом по точке.
- Edge cases: лицо пропало (hold-last → blackout), потеря камеры, несколько лиц.
