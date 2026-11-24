// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.testutils.inMemoryPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotSame
import kotlin.time.Duration.Companion.milliseconds

class StudyScreenRepositoryTest {
    private val sharedPrefs: SharedPreferences = SPMockBuilder().createSharedPreferences()
    private val menuRepository = ReviewerMenuRepository(sharedPrefs)
    private val prefs = inMemoryPrefs(sharedPrefs)

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are true when toolbar is enabled and action is set to ALWAYS`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            menuOnlyActions = listOf(),
            disabledActions = listOf(),
        )
        val repository = StudyScreenRepository(prefs)

        assertTrue(repository.isMarkShownInToolbar)
    }

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are false when action is NOT in ALWAYS list`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions = listOf(),
            menuOnlyActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            disabledActions = listOf(),
        )
        val repository = StudyScreenRepository(prefs)

        assertFalse(repository.isMarkShownInToolbar)
    }

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are false when toolbar is completely hidden`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            menuOnlyActions = listOf(),
            disabledActions = listOf(),
        )
        sharedPrefs.edit {
            putString(R.string.reviewer_toolbar_position_key.toString(), R.string.reviewer_toolbar_value_none.toString())
        }
        val repository = StudyScreenRepository(prefs)

        assertFalse(repository.isMarkShownInToolbar)
    }

    @Test
    fun `generateStateMutationKey generates unique keys`() =
        runTest {
            val repository = StudyScreenRepository(prefs)
            val first = repository.generateStateMutationKey()
            delay(10.milliseconds)
            val second = repository.generateStateMutationKey()
            assertNotSame(first, second)
        }
}
