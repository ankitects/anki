// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesScreenTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    private val default = ProfileItem(id = ProfileId.DEFAULT, name = "Default")

    @Test
    fun `a profile can be renamed to a different case of its own name`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "work")
        var renamedTo: ProfileName? = null
        setScreen(profiles = listOf(default, work), profileBeingRenamed = work, onRenameProfileConfirm = { renamedTo = it })

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Work")
        composeRule.onNodeWithText(targetContext.getString(CommonString.rename)).performClick()

        assertEquals("Work", renamedTo?.value)
    }

    @Test
    fun `tapping a profile asks to switch to it`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "Work")
        var tapped: ProfileItem? = null
        setScreen(profiles = listOf(default, work), onProfileClick = { tapped = it })

        composeRule.onNodeWithText("Work").performClick()

        assertEquals(work, tapped)
    }

    @Test
    fun `confirming the switch dialog switches the profile`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "Work")
        var confirmed = false
        setScreen(profiles = listOf(default, work), profileToSwitchTo = work, onSwitchProfileConfirm = { confirmed = true })
        val appName = targetContext.getString(CommonString.app_name)

        composeRule.onNodeWithText(targetContext.getString(CommonString.switch_profile_title, "Work")).assertIsDisplayed()
        composeRule.onNodeWithText(targetContext.getString(CommonString.switch_profile_message, appName)).assertIsDisplayed()
        composeRule.onNodeWithText(targetContext.getString(CommonString.switch_profile_confirm)).performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `a blocked switch explains why`() {
        setScreen(profiles = listOf(default), isSwitchBlocked = true)
        val appName = targetContext.getString(CommonString.app_name)

        composeRule.onNodeWithText(targetContext.getString(CommonString.switch_profile_blocked, appName)).assertIsDisplayed()
    }

    @Test
    fun `a failed switch names the profile it could not open`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "Work")
        setScreen(profiles = listOf(default, work), profileSwitchFailed = work)

        composeRule.onNodeWithText(targetContext.getString(CommonString.switch_profile_failed, "Work")).assertIsDisplayed()
    }

    @Test
    fun `the failure message closes with OK`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "Work")
        var dismissed = false
        setScreen(profiles = listOf(default, work), profileSwitchFailed = work, onSwitchFailedDismiss = { dismissed = true })

        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_ok)).performClick()

        assertTrue(dismissed)
    }

    private fun setScreen(
        profiles: List<ProfileItem>,
        profileBeingRenamed: ProfileItem? = null,
        profileToSwitchTo: ProfileItem? = null,
        isSwitchBlocked: Boolean = false,
        profileSwitchFailed: ProfileItem? = null,
        onProfileClick: (ProfileItem) -> Unit = {},
        onRenameProfileConfirm: (ProfileName) -> Unit = {},
        onSwitchProfileConfirm: () -> Unit = {},
        onSwitchFailedDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            AnkiDroidTheme {
                SwitchProfilesScreen(
                    profiles = profiles,
                    isAddProfileDialogVisible = false,
                    profileBeingRenamed = profileBeingRenamed,
                    profileToSwitchTo = profileToSwitchTo,
                    isSwitchBlocked = isSwitchBlocked,
                    profileSwitchFailed = profileSwitchFailed,
                    onNavigateUp = {},
                    onProfileClick = onProfileClick,
                    onAddProfileClick = {},
                    onAddProfileConfirm = {},
                    onAddProfileDismiss = {},
                    onEditProfile = {},
                    onRenameProfileConfirm = onRenameProfileConfirm,
                    onRenameProfileDismiss = {},
                    onSwitchProfileConfirm = onSwitchProfileConfirm,
                    onSwitchProfileDismiss = {},
                    onSwitchBlockedDismiss = {},
                    onSwitchFailedDismiss = onSwitchFailedDismiss,
                    onDeleteProfile = {},
                )
            }
        }
    }
}
