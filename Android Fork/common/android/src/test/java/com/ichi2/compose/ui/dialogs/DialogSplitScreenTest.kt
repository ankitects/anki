// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.testutils.ShadowLowerPaneView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The app is in the lower half of split screen: its display metrics describe the 400px pane,
 * while the dialog window's visible display frame is reported in screen coordinates, below the 440px top pane.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, Config.TARGET_SDK], qualifiers = "w411dp-h400dp-mdpi", shadows = [ShadowLowerPaneView::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogSplitScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dialog stays visible in the lower split screen pane`() {
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog("Delete profile", "This cannot be undone.", "Delete", {}, {})
            }
        }
        compose.onNodeWithText("Delete profile").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
    }

    @Test
    fun `long messages scroll without losing the actions in the lower pane`() {
        var confirmed = false
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog("Delete profile", "This cannot be undone. ".repeat(100), "Delete", { confirmed = true }, {})
            }
        }
        compose.onNodeWithText("Delete profile").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val confirm = compose.onNodeWithText("Delete").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        confirm.performTouchInput { click() }
        assertTrue(confirmed)
    }
}
