// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.testutils.EmptyApplication
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Compose content with an AnkiDroid XML theme, without starting an app screen.
 *
 * Subclasses call [capture] with their content. For a dialog, pass `node = isDialog()`.
 * Each test runs with the themes selected with `-Ptheme`, as [ScreenshotTest] does.
 * Use [capture]'s `beforeCapture` callback to interact with the content before capturing it.
 * Screenshots participate in the root `recordScreenshots`, `compareScreenshots` and
 * `verifyScreenshots` tasks with `-Pscreenshot`.
 */
@RunWith(RobolectricTestParameterInjector::class)
@Category(ScreenshotTestCategory::class, EmptyApplicationCategory::class)
@Config(application = EmptyApplication::class, sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ComposeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @TestParameter(valuesProvider = ThemeProvider::class)
    lateinit var theme: ThemeConfig

    @OptIn(ExperimentalRoborazziApi::class)
    protected fun capture(
        name: String,
        node: SemanticsMatcher = isRoot(),
        fontScale: Float = 1f,
        beforeCapture: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        val themeConfig = theme
        val previewTheme = themeConfig.theme
        if (previewTheme is NightTheme) RuntimeEnvironment.setQualifiers("+night")
        RuntimeEnvironment.setFontScale(fontScale)
        composeRule.setContent {
            AnkiDroidPreview(theme = previewTheme, content = content)
        }
        composeRule.waitForIdle()
        beforeCapture()
        composeRule.waitForIdle()
        val classDir = "build/outputs/roborazzi/${javaClass.simpleName}"
        val options = provideRoborazziContext().options
        composeRule.onNode(node).assertIsDisplayed().captureRoboImage(
            filePath = "$classDir/${composeScreenshotFileName(name, themeConfig)}",
            roborazziOptions = options.copy(compareOptions = options.compareOptions.copy(outputDirectoryPath = "$classDir/diffs")),
        )
    }
}

internal fun composeScreenshotFileName(
    name: String,
    theme: ThemeConfig,
): String = "${name}_${theme.name.lowercase()}.png"
