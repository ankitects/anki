// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddProfileDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a name already in use cannot be added, ignoring case`() {
        composeRule.setContent {
            AnkiDroidTheme {
                AddProfileDialog(existingNames = listOf("Work"), onDismissRequest = {}, onConfirm = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("work")

        composeRule.onNodeWithText(targetContext.getString(CommonString.error_name_exists)).assertIsDisplayed()
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_add)).assertIsNotEnabled()
    }

    @Test
    fun `the length counter ignores spaces around the name`() {
        composeRule.setContent {
            AnkiDroidTheme {
                AddProfileDialog(existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput(" Work ")

        composeRule.onNodeWithText("4/${ProfileName.MAX_LENGTH}").assertIsDisplayed()
    }
}
