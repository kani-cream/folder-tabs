plugins {
    // Kotlin 2.3.x: the IntelliJ Platform 2026.2 jars carry Kotlin 2.4 metadata, which a
    // 2.3.x compiler reads without -Xskip-metadata-version-check.
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.intellij.platform") version "2.18.1" apply false
}

allprojects {
    group = "com.github.kanicream.foldertabs"
    // Release phases per plan/grouped-editor-tabs-design.md section 25:
    // 0.1.0 = Core, 0.5.0 = Sync & UX, 1.0.0 = Usability & Stabilization.
    version = "1.1.0"
}
