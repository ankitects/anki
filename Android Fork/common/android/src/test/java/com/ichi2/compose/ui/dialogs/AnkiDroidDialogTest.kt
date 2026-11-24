// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnkiDroidDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun `large dialogs use the View dialog background insets`() {
        compose.setContent {
            AnkiDroidPreview {
                AnkiDroidDialog(onDismissRequest = {}) {
                    Box(Modifier.fillMaxSize().testTag("dialog_content"))
                }
            }
        }
        val content = compose.onNodeWithTag("dialog_content").fetchSemanticsNode().boundsInRoot
        val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val horizontalMargin = 24 * density
        val verticalMargin = 16 * density
        assertEquals(horizontalMargin, content.left - window.left, 1f, "The dialog must leave 24 dp on the left")
        assertEquals(horizontalMargin, window.right - content.right, 1f, "The dialog must leave 24 dp on the right")
        assertEquals(verticalMargin, content.top - window.top, 1f, "The dialog must leave 16 dp above its content")
        assertEquals(verticalMargin, window.bottom - content.bottom, 1f, "The dialog must leave 16 dp below its content")
    }
}
