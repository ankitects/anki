// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SingleFragmentActivityTest : RobolectricTest() {
    @Test
    fun `launch without a fragment name shows an error and finishes`() {
        val intent = Intent(targetContext, AccountActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = startRegularActivity<AccountActivity>(intent)

        assertTrue(activity.isFinishing)
        assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `custom study results are left to the hosted fragment`() {
        // a generic host: a fragment which doesn't listen for the result must not trigger navigation
        val activity = startRegularActivity<SingleFragmentActivity>(SingleFragmentActivity.getIntent(targetContext, Fragment::class))

        for (action in CustomStudyAction.entries) {
            activity.supportFragmentManager.setFragmentResult(
                CustomStudyAction.REQUEST_KEY,
                Bundle().apply { putInt(CustomStudyAction.RESULT_CUSTOM_STUDY_ACTION, action.ordinal) },
            )
            advanceRobolectricLooper()

            assertNull(shadowOf(activity).nextStartedActivity, "$action started an activity")
            assertFalse(activity.isFinishing, "$action finished the activity")
        }
    }
}
