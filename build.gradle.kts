plugins {
    // Kotlin 2.4.x: the same language line as the Kotlin 2.4 stdlib bundled with IntelliJ Platform 2026.2.
    kotlin("jvm") version "2.4.21" apply false
    id("org.jetbrains.intellij.platform") version "2.19.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11" apply false
}

allprojects {
    group = "com.github.kanicream.foldertabs"
    // Bumped in each release PR (plan/grouped-editor-tabs-design.md section 25 describes the release flow).
    version = "1.4.0"
}
