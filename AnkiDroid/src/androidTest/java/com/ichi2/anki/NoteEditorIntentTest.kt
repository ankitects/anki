/*
 *  Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorFragment.Companion.intentLaunchedWithImage
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.getNoteEditorFragment
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.utils.AssetHelper.TEXT_PLAIN
import junit.framework.TestCase.assertFalse
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorIntentTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule: TestRule? = GrantStoragePermission.instance

    @get:Rule
    var activityRuleIntent: ActivityScenarioRule<NoteEditorActivity>? =
        ActivityScenarioRule(
            noteEditorTextIntent,
        )

    @Test
    @Flaky(OS.ALL, "Issue 15707 - java.lang.ArrayIndexOutOfBoundsException: length=0; index=0")
    fun launchActivityWithIntent() {
        col
        val scenario = activityRuleIntent!!.scenario
        scenario.moveToState(Lifecycle.State.RESUMED)

        var currentFieldStrings: String? = null
        scenario.onActivity { activity ->
            val editor = activity.getNoteEditorFragment()
            currentFieldStrings = editor.currentFieldStrings[0]
        }
        MatcherAssert.assertThat(currentFieldStrings!!, Matchers.equalTo("sample text"))
    }

    @Test
    fun intentLaunchedWithNonImageIntent() {
        val intent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = TEXT_PLAIN
            }
        assertFalse(intentLaunchedWithImage(intent))
    }

    private val noteEditorTextIntent: Intent
        get() {
            val bundle = Bundle().apply { putString(Intent.EXTRA_TEXT, "sample text") }
            return NoteEditorLauncher.PassArguments(bundle).toIntent(testContext, Intent.ACTION_SEND)
        }
}
