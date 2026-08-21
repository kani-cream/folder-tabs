import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    // IntelliJ Platform 2026.2 baseline (plan/github-actions-ci-design.md section 4.1).
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        // Since 2025.3 IntelliJ IDEA ships as a single unified distribution, so the
        // unified helper is used rather than intellijIdeaCommunity(). Pinned to the
        // current 2026.2 patch; the baseline stays 2026.2 / since-build 262.
        intellijIdea("2026.2.1")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

tasks.test {
    // The unified distribution bundles language plugins whose listeners fail to
    // initialize in the headless test harness; load only this plugin.
    systemProperty("idea.load.plugins.id", "com.github.kanicream.foldertabs")
    testLogging {
        showStandardStreams = true
    }
}

tasks.buildSearchableOptions {
    // The headless IDE that indexes searchable options requires the JVM default locale
    // to be the IDE default (English); on a Japanese-locale machine it fails with
    // "Locale must be default". Pin the forked JVM to English.
    jvmArgs("-Duser.language=en", "-Duser.country=US")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.kanicream.foldertabs"
        name = "Folder Tabs"
        version = project.version.toString()
        changeNotes = """
            <b>1.0.0</b>
            <ul>
              <li>Two-row header above every editor: directory groups on top, files of the selected group below.</li>
              <li>Group label depth setting (default 2); same-name directories always get a distinguishing path;
                  labels reaching the project root read <code>~/&lt;project&gt;/…</code>.</li>
              <li>Drag &amp; drop to reorder group tabs; order is remembered per project.</li>
              <li>Follows rename / move / delete, shows a modified marker, file type icons, full-path tooltips.</li>
              <li>Works with split editors and with <i>Tab placement: None</i>; can be turned off in Settings &gt; Tools &gt; Folder Tabs.</li>
            </ul>
        """.trimIndent()
        vendor {
            name = "kani-cream"
            url = "https://github.com/kani-cream/folder-tabs"
        }
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }

    pluginVerification {
        // Stable Public API Only gate (plan/github-actions-ci-design.md section 11).
        // COMPATIBILITY_WARNINGS is intentionally left out and reviewed from the report.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
        ides {
            recommended()
        }
    }
}
