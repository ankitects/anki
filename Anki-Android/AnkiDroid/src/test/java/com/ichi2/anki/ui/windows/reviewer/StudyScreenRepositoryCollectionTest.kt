/*
 * Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.cardStateCustomizer
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class StudyScreenRepositoryCollectionTest : JvmTest() {
    private val repository: StudyScreenRepository

    init {
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        val prefs = PrefsRepository(SPMockBuilder().createSharedPreferences(), mockResources)
        repository = StudyScreenRepository(prefs)
    }

    @Test
    fun `custom scheduling js is correctly retrieved`() =
        runTest {
            val js = CollectionManager.withCol { cardStateCustomizer }
            assertEquals(js, repository.getCustomSchedulingJs())

            val newJs = "console.log('Anki is awesome!');"
            CollectionManager.withCol { cardStateCustomizer = newJs }
            assertEquals(newJs, repository.getCustomSchedulingJs())
        }

    @Test
    fun `shouldShowNextTimes is correctly retrieved`() =
        runTest {
            val defaultValue = CollectionPreferences.getShowIntervalOnButtons()
            assertEquals(defaultValue, repository.getShouldShowNextTimes())

            suspend fun assertNewValue(newValue: Boolean) {
                CollectionPreferences.setShowIntervalsOnButtons(newValue)
                assertEquals(newValue, repository.getShouldShowNextTimes())
            }

            assertNewValue(true)
            assertNewValue(false)
        }
}
