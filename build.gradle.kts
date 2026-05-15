plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "tracker"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvmToolchain(21)
}

// Ограничиваем платформенные классификаторы JavaCPP только текущей машиной,
// чтобы не тянуть ~1 ГБ натива для всех ОС.
val javacppPlatform: String = when {
    System.getProperty("os.name").lowercase().contains("mac") ->
        if (System.getProperty("os.arch").lowercase().contains("aarch64")) "macosx-arm64" else "macosx-x86_64"
    System.getProperty("os.name").lowercase().contains("windows") -> "windows-x86_64"
    else -> "linux-x86_64"
}
System.setProperty("javacpp.platform", javacppPlatform)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.javacv.platform)
}

compose.desktop {
    application {
        mainClass = "tracker.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "Lighthouse"
            packageVersion = "1.0.0"
            macOS {
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>Lighthouse uses the webcam to track a point on a face and drive a DMX moving head.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
