// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.common.android.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun `long confirmation scrolls without shrinking the buttons at large font sizes`() {
        RuntimeEnvironment.setFontScale(2f)
        var confirmed = false
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Delete item",
                    message = "This item and its children will be permanently deleted. This action cannot be undone. ".repeat(8),
                    confirmText = "Delete",
                    onConfirm = { confirmed = true },
                    onDismissRequest = {},
                )
            }
        }
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val confirm = compose.onNodeWithText("Delete").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val message = compose.onNode(hasScrollAction())
        val scroll = message.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.maxValue() > 0)
        message.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(scroll.value() > 0)
        confirm.performTouchInput { click() }
        assertTrue(confirmed)
    }

    @Test
    fun `short messages use the AnkiDroid dialog animation`() {
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Confirm",
                    message = "Short message",
                    confirmText = "Clear",
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        compose.onNodeWithText("Short message").assertIsDisplayed()
        compose.onNodeWithText("Clear").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val window = ShadowDialog.getLatestDialog().window!!
        assertEquals(R.style.Animation_AnkiDroidDialog, window.attributes.windowAnimations)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun `long action labels stack and remain clickable`() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Delete item",
                    message = "This action cannot be undone.",
                    confirmText = "Delete permanently",
                    dismissText = "Keep this item",
                    onConfirm = { confirmed = true },
                    onDismissRequest = { dismissed = true },
                )
            }
        }
        val confirm = compose.onNodeWithText("Delete permanently").assertIsDisplayed()
        val dismiss = compose.onNodeWithText("Keep this item").assertIsDisplayed()
        val confirmBounds = confirm.fetchSemanticsNode().boundsInRoot
        val dismissBounds = dismiss.fetchSemanticsNode().boundsInRoot
        assertTrue(confirmBounds.bottom <= dismissBounds.top, "The confirm action must stack above the dismiss action")
        confirm.performTouchInput { click() }
        dismiss.performTouchInput { click() }
        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun `custom dismiss action stays available while confirmation is disabled`() {
        val enabled = mutableStateOf(false)
        var confirmed = false
        var dismissed = 0
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Could not load items",
                    message = "Check your connection and try again.",
                    confirmText = "Retry",
                    dismissText = "Close",
                    onConfirm = { confirmed = true },
                    onDismissRequest = { dismissed++ },
                    enabled = enabled.value,
                )
            }
        }

        val retry = compose.onNodeWithText("Retry").assertIsNotEnabled()
        retry.performClick()
        assertFalse(confirmed)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
        assertFalse(confirmed)

        compose.runOnIdle { enabled.value = true }
        retry.assertIsEnabled().performClick()
        assertTrue(confirmed)
        assertEquals(1, dismissed)
    }
}
