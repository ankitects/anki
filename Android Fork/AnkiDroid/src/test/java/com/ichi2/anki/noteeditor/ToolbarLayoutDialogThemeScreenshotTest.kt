// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.Context
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.ThemeConfig
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Covers selection and keyboard focus, vertical layout and large text.
 *
 * Record: `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests '*.ToolbarLayoutDialogThemeScreenshotTest'`
 * Verify: `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests '*.ToolbarLayoutDialogThemeScreenshotTest'`
 */
class ToolbarLayoutDialogThemeScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun scrollWithStackFocused() = captureLayout("scroll_stack_focused", ToolbarLayoutMode.SCROLL, focused = ToolbarLayoutMode.STACK)

    @Test
    @Config(qualifiers = "w300dp-h640dp-mdpi")
    fun vertical() = captureLayout("stack_vertical", ToolbarLayoutMode.STACK)

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun hiddenLargeText() = captureLayout("hidden_large_text", ToolbarLayoutMode.HIDDEN, fontScale = 2f)

    private fun captureLayout(
        name: String,
        selected: ToolbarLayoutMode,
        fontScale: Float = 1f,
        focused: ToolbarLayoutMode? = null,
    ) {
        capture(
            name,
            node = isDialog(),
            fontScale = fontScale,
            beforeCapture = {
                if (focused != null) {
                    val label = ApplicationProvider.getApplicationContext<Context>().getString(focused.label())
                    val choice = composeRule.onNodeWithText(label)
                    val view = (choice.fetchSemanticsNode().root as ViewRootForTest).view
                    // Switch the dialog's own Compose root to keyboard mode.
                    composeRule.runOnIdle { view.requestFocusFromTouch() }
                    choice.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                    choice.assertIsFocused()
                    composeRule.mainClock.advanceTimeBy(200)
                }
            },
        ) {
            ToolbarLayoutDialog(
                selected = selected,
                onSelect = {},
                onDismiss = {},
                showSelectionBackground = theme != ThemeConfig.EINK,
            )
        }
    }
}
