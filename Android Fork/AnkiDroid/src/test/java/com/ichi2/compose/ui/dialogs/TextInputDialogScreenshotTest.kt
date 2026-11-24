// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.preferences.profiles.AddProfileDialog
import org.junit.Test
import org.robolectric.annotation.Config

/** Captures Add profile at default text size and scrolling dialogs with fixed actions at 200%. */
@Config(qualifiers = "w320dp-h320dp-mdpi")
class TextInputDialogScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun addProfile(
        @TestParameter nameEntered: Boolean,
    ) {
        capture(
            name = if (nameEntered) "add_profile_valid" else "add_profile_empty",
            node = isDialog(),
            beforeCapture = {
                if (nameEntered) {
                    composeRule.onNode(hasSetTextAction()).performTextInput("Personal")
                    composeRule.onNodeWithText("Add").assertIsEnabled()
                } else {
                    composeRule.onNodeWithText("Add").assertIsNotEnabled()
                }
            },
        ) {
            AddProfileDialog(existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
        }
    }

    @Test
    fun largeTextInShortWindow(
        @TestParameter inputVisible: Boolean,
    ) {
        val position = if (inputVisible) "input" else "title"
        capture(
            "large_text_short_window_$position",
            node = isDialog(),
            fontScale = 2f,
            beforeCapture = {
                val body = if (inputVisible) composeRule.onNode(hasSetTextAction()) else composeRule.onNodeWithText("Rename tag")
                body.performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
                composeRule.onNodeWithText("Rename").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
            },
        ) {
            TextInputDialog(
                title = "Rename tag",
                description = "Source tag: parent::Science",
                label = "Tag name",
                confirmText = "Rename",
                dismissText = "Cancel",
                initialText = "History",
                onConfirm = {},
                onDismissRequest = {},
                validation = InputValidation.Immediate(validateOnInput = false) { null },
            )
        }
    }
}
