plugins {
    // Kotlin 2.3.x: the IntelliJ Platform 2026.2 jars carry Kotlin 2.4 metadata, which a
    // 2.3.x compiler reads without -Xskip-metadata-version-check.
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.intellij.platform") version "2.18.1" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

allprojects {
    group = "com.github.kanicream.foldertabs"
    // Bumped in each release PR (plan/grouped-editor-tabs-design.md section 25 describes the release flow).
    version = "1.4.0"
}
