// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ActionButtonStatusTest : AndroidTest {
    @Test
    fun allCustomButtonsCanBeDisabled() {
        val reviewerExpectedKeys = customButtonsExpectedKeys
        val actualPreferenceKeys = PreferenceTestUtils.getAllCustomButtonKeys(targetContext)
        assertThat(
            "Each button in the Action Bar must be modifiable in Preferences - Reviewer - App Bar Buttons",
            reviewerExpectedKeys,
            containsInAnyOrder(*actualPreferenceKeys.toTypedArray()),
        )
    }

    @KotlinCleanup("Use SPMockBuilder")
    private val customButtonsExpectedKeys: Set<String>
        get() {
            val preferences = mock(SharedPreferences::class.java)
            val ret: MutableSet<String> = HashSet()
            whenever(preferences.getString(any(), any())).then { a: InvocationOnMock ->
                val key = a.getArgument<String>(0)
                ret.add(key)
                "0"
            }
            val status = ActionButtonStatus()
            status.setup(preferences)
            return ret
        }
}
