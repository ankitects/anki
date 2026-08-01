/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.testutil.GrantStoragePermission
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule

abstract class NoteEditorTest protected constructor() {
    /*
     * Rules mean that we get a failure on API 25.
     * Even if we ignore the tests, the rules cause a failure.
     * We can't ignore the test in @BeforeClass ("Test run failed to complete. Expected 150 tests, received 149")
     * and @Before executes after the rule.
     * So, disable the rules in the constructor, and ignore in before.
     */
    private val isInvalid = invalidSdksImpl.contains(Build.VERSION.SDK_INT)

    @get:Rule
    var runtimePermissionRule: TestRule? =
        GrantStoragePermission.instance
            .takeUnless { isInvalid }

    @get:Rule
    var activityRule: ActivityScenarioRule<NoteEditorActivity>? =
        ActivityScenarioRule<NoteEditorActivity>(
            noteEditorIntent,
        ).takeUnless { isInvalid }

    private val noteEditorIntent: Intent
        get() {
            return NoteEditorLauncher.AddNote().toIntent(targetContext)
        }

    @Before
    fun before() {
        for (invalid in invalidSdksImpl) {
            Assume.assumeThat(
                "Test fails on API $invalid",
                Build.VERSION.SDK_INT,
                not(
                    equalTo(invalid),
                ),
            )
        }
    }

    private val invalidSdksImpl: List<Int>
        /*
         java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
         at androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:301)
         */
        get() = listOf<Int>(Build.VERSION_CODES.N_MR1) + invalidSdks

    protected open val invalidSdks: List<Int>
        get() = ArrayList()
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
}
