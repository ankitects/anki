// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageEditorToolbarTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each action invokes its own callback`() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            AnkiDroidTheme {
                ImageEditorToolbar(
                    hasImage = true,
                    hasExternalEditor = true,
                    replaceIcon = R.drawable.ic_photo_library,
                    onReplace = { actions.add("replace") },
                    onCrop = { actions.add("crop") },
                    onEditExternally = { actions.add("external") },
                )
            }
        }
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_positive_replace)).performClick()
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).performClick()
        composeRule.onNodeWithText(TR.studyingEdit()).performClick()
        assertEquals(listOf("replace", "crop", "external"), actions)
    }

    @Test
    fun `editing actions become available when an image is selected`() {
        val hasImage = mutableStateOf(false)
        composeRule.setContent {
            AnkiDroidTheme {
                ImageEditorToolbar(
                    hasImage = hasImage.value,
                    hasExternalEditor = true,
                    replaceIcon = R.drawable.ic_photo_library,
                    onReplace = {},
                    onCrop = {},
                    onEditExternally = {},
                )
            }
        }
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_positive_replace)).assertIsEnabled()
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).assertIsNotEnabled()
        composeRule.onNodeWithText(TR.studyingEdit()).assertIsNotEnabled()
        composeRule.runOnIdle { hasImage.value = true }
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).assertIsEnabled()
        composeRule.onNodeWithText(TR.studyingEdit()).assertIsEnabled()
    }

    @Test
    fun `edit is hidden when no external editor is available`() {
        val hasExternalEditor = mutableStateOf(false)
        composeRule.setContent {
            AnkiDroidTheme {
                ImageEditorToolbar(
                    hasImage = true,
                    hasExternalEditor = hasExternalEditor.value,
                    replaceIcon = R.drawable.ic_photo_library,
                    onReplace = {},
                    onCrop = {},
                    onEditExternally = {},
                )
            }
        }
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_positive_replace)).assertIsEnabled()
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).assertIsEnabled()
        composeRule.onNodeWithText(TR.studyingEdit()).assertDoesNotExist()
        composeRule.runOnIdle { hasExternalEditor.value = true }
        composeRule.onNodeWithText(TR.studyingEdit()).assertIsEnabled()
        composeRule.runOnIdle { hasExternalEditor.value = false }
        composeRule.onNodeWithText(TR.studyingEdit()).assertDoesNotExist()
    }
}
