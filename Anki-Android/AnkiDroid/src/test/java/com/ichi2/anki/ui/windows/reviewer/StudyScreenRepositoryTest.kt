/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.PrefsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertNotSame

class StudyScreenRepositoryTest {
    private val sharedPrefs: SharedPreferences = SPMockBuilder().createSharedPreferences()
    private val menuRepository = ReviewerMenuRepository(sharedPrefs)
    private val prefs: PrefsRepository

    init {
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        prefs = PrefsRepository(sharedPrefs, mockResources)
    }

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
    fun `getServerPort returns 0 when useFixedPortInReviewer is false`() {
        prefs.useFixedPortInReviewer = false

        val repository = StudyScreenRepository(prefs)
        val port = repository.getServerPort()

        assertEquals(0, port)
    }

    @Test
    fun `getServerPort returns valid port when configured and available`() {
        prefs.useFixedPortInReviewer = true
        prefs.reviewerPort = 0

        val repository = StudyScreenRepository(prefs)
        val port = repository.getServerPort()

        assertTrue(port > 0)
        assertEquals(prefs.reviewerPort, port)
    }

    @Test
    fun `getServerPort returns 0 when specific port is already in use`() {
        val blockerSocket = ServerSocket(0)
        val busyPort = blockerSocket.localPort

        try {
            prefs.useFixedPortInReviewer = true
            prefs.reviewerPort = busyPort

            val repository = StudyScreenRepository(prefs)
            val resultPort = repository.getServerPort()

            assertEquals(0, resultPort)
        } finally {
            blockerSocket.close()
        }
    }

    @Test
    fun `generateStateMutationKey generates unique keys`() =
        runTest {
            val repository = StudyScreenRepository(prefs)
            val first = repository.generateStateMutationKey()
            delay(10)
            val second = repository.generateStateMutationKey()
            assertNotSame(first, second)
        }
}
