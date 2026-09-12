import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    // Coverage: `./gradlew :plugin:koverHtmlReport` (plugin/build/reports/kover/html) and koverXmlReport.
    id("org.jetbrains.kotlinx.kover")
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

kover {
    currentProject {
        instrumentation {
            // Only the plugin's own classes: without this the agent transforms every class the
            // headless IDE loads in the test JVM (about +50% test time, and a log of frame-computation
            // errors for platform classes) for a report that is filtered to this package anyway.
            includedClasses.add("com.github.kanicream.foldertabs.*")
        }
    }
    reports {
        verify {
            // 80% line coverage gate (README > Development, plan section 24.x); koverVerify runs with `check`.
            rule("plugin line coverage") {
                minBound(80)
            }
        }
    }
}

tasks.koverVerify {
    // Without a test run there is no report and the rule would fail as "0% covered": skip the gate
    // when tests were excluded on purpose (`./gradlew build -x test`).
    onlyIf { "test" !in gradle.startParameter.excludedTaskNames }
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
            <b>1.4.0</b>
            <ul>
              <li>Keyboard navigation between groups: new <i>Next Folder Group</i> / <i>Previous Folder Group</i>
                  actions (Window &gt; Editor Tabs, Find Action) move to the adjacent directory group and open the
                  file you last used there, wrapping at the ends; in split editors they cycle through the focused
                  pane's groups only. No default shortcut: assign one in Settings &gt; Keymap &gt; Plugins &gt; Folder Tabs.</li>
              <li>Collapse the header when you need the space: <i>Collapse Folder Tabs</i> (Window &gt; Editor Tabs,
                  any tab's right-click menu, or a shortcut of your choice) shrinks the header to a single line showing
                  the current group and file; click that line or toggle again to expand. Per project, remembered until
                  the IDE is closed; open files, ordering and settings are untouched.</li>
            </ul>
            <b>1.3.1</b>
            <ul>
              <li>Saved group and file order now follows a directory or file rename, move or delete even
                  while no editor is open; previously the reordered position was lost the next time
                  those files were opened.</li>
            </ul>
            <b>1.3.0</b>
            <ul>
              <li>File tabs can now be reordered by drag &amp; drop within their group, like the group tabs;
                  the order is remembered per project and follows rename / move / delete.</li>
              <li>Split editors: each pane's header now shows only the files open in that pane, in the
                  IDE's own tab order, and clicking a group or file tab opens the file in the pane whose
                  header you clicked (previously it could land in the other pane).</li>
            </ul>
            <b>1.2.2</b>
            <ul>
              <li>No more jump on the first switch to a file after startup: the header's close buttons are
                  sized in its first layout, and the tab strip is primed before it is shown so the platform's
                  first-paint layout state (Islands theme) no longer shifts the tabs afterwards.</li>
              <li>A file saved right after an edit (e.g. reformat on save) no longer keeps a stale modified marker.</li>
              <li>Group tabs are keyed by their directory, so a modified-flag change no longer rebuilds the
                  group strip on the next refresh.</li>
            </ul>
            <b>1.2.1</b>
            <ul>
              <li>The selected tab in the header now gets the same blue underline as the standard editor
                  tab, so the current file and group are easy to spot. In split editors only the focused
                  pane's header is highlighted, in sync with the standard tabs.</li>
              <li>Clicking a header tab keeps the focus in the editor, like the standard tabs.</li>
            </ul>
            <b>1.1.0</b>
            <ul>
              <li>Close from the header: a close button and a right-click <i>Close</i> entry on every
                  file tab, and <i>Close Group</i> in a group tab's right-click menu. Closing delegates to
                  the IDE's own <i>Close Editor</i> action, so split panes behave as usual.</li>
            </ul>
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
