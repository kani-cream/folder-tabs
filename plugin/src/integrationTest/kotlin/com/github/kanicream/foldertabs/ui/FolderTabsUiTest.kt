package com.github.kanicream.foldertabs.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.ReusableLocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the real IDE with the built plugin installed (`./gradlew :plugin:uiTest`). Screenshots go
 * to `ui.screenshots.dir` so a run can also be judged by eye.
 */
class FolderTabsUiTest {

    private fun requiredProperty(name: String): String =
        checkNotNull(System.getProperty(name)?.takeIf { it.isNotBlank() }) { "System property '$name' is not set" }

    /** A build number such as 263.7307.112 is a pre-release; a version such as 2026.2.4 is a release. */
    private fun <T : ProjectInfoSpec> TestCase<T>.useIde(version: String): TestCase<T> =
        if (version.startsWith("20")) useRelease(version) else useEAP(version)

    private fun context(testName: String): IDETestContext =
        Starter.newContext(
            testName,
            TestCase(
                IdeInfo.IdeaUltimate,
                ReusableLocalProjectInfo(Path(requiredProperty("ui.sample.project"))),
            ).useIde(requiredProperty("ui.ide.version")),
        )
            .apply { PluginConfigurator(this).installPluginFromPath(Path(requiredProperty("path.to.build.plugin"))) }
            .prepareProjectCleanImport()
            .applyVMOptionsPatch {
                addSystemProperty("ide.show.tips.on.startup.default.value", false)
                addSystemProperty("jb.consents.confirmation.enabled", false)
                addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
                addSystemProperty("shared.indexes.download.auto.consent", true)
            }
            .addProjectToTrustedLocations()
            .also { skipAutoTrial(it) }

    /** A fresh config directory would otherwise start a trial whose page covers the editor. */
    private fun skipAutoTrial(context: IDETestContext) {
        val configDir = context.paths.configDir
        configDir.createDirectories()
        configDir.resolve(".ce_migration_attempted").createFile()
    }

    private fun Driver.shot(name: String) {
        val dir = Path(requiredProperty("ui.screenshots.dir"), name).createDirectories()
        takeScreenshot(dir.toString())
    }

    private fun IdeaFrameUI.headers() =
        xx(xQuery { byAttribute("name", GroupedTabsPanel.HEADER_COMPONENT_NAME) }).list().sortedBy { it.getLocationOnScreen().x }

    private fun IdeaFrameUI.editorTexts() =
        xx(xQuery { byClass("EditorComponentImpl") }, JEditorUiComponent::class.java).list()
            .sortedBy { it.getLocationOnScreen().x }
            .map { it.text.lineSequence().first() }

    private fun IdeaFrameUI.collapsedBars() =
        xx(xQuery { byAttribute("name", CollapsedHeaderBar.COMPONENT_NAME) }).list()

    private fun UiComponent.tab(label: String) = x(xQuery { byVisibleText(label) })

    private fun IdeaFrameUI.waitForPanes(left: String, right: String) {
        waitFor("left pane shows $left and right pane shows $right", 15.seconds) {
            editorTexts() == listOf(left, right)
        }
    }

    @Test
    fun theHeaderDrivesItsOwnPane() {
        val context = context("header-drives-its-own-pane")
        context.runIdeWithDriver().useDriverAndCloseIde {
            assertTrue(isPluginLoaded(PLUGIN_ID)) { "$PLUGIN_ID was not loaded" }
            waitForIndicators(5.minutes)

            openFile("alpha/one.txt")
            openFile("alpha/two.txt")
            openFile("beta/three.txt")
            shot("01-files-open")

            invokeAction("SplitVertically")
            ideFrame {
                waitForPanes(left = "THREE", right = "THREE")
                shot("02-split")

                // Issue #29: the focus is in the right pane, so this click has to move it.
                headers().first().tab(GROUP_ALPHA).click()
                waitForPanes(left = "TWO", right = "THREE")
                shot("03-left-group-clicked")

                headers().first().tab("one.txt").click()
                waitForPanes(left = "ONE", right = "THREE")
                shot("04-left-file-clicked")

                repeat(2) { headers().last().tab("three.txt").click() }
                waitForPanes(left = "ONE", right = "THREE")
                shot("05-right-tab-clicked-twice")

                headers().first().tab("one.txt").click()
                invokeAction("FolderTabs.NextGroup")
                waitForPanes(left = "THREE", right = "THREE")
                shot("06-next-group")

                invokeAction("FolderTabs.ToggleCollapsed")
                waitFor("the header collapses", 15.seconds) { collapsedBars().isNotEmpty() }
                shot("07-collapsed")

                invokeAction("FolderTabs.ToggleCollapsed")
                waitFor("the header expands", 15.seconds) { collapsedBars().isEmpty() }
                shot("08-expanded")
            }
        }
        assertNoPluginErrors(context)
    }

    private fun assertNoPluginErrors(context: IDETestContext) {
        val errors = context.paths.testHome.resolve("log/idea.log").readLines()
            .filter { " ERROR " in it && "foldertabs" in it }
        assertTrue(errors.isEmpty()) { "Plugin errors in idea.log: $errors" }
    }

    private companion object {
        const val PLUGIN_ID = "com.github.kanicream.foldertabs"
        const val GROUP_ALPHA = "~/sample-project/alpha"
    }
}
