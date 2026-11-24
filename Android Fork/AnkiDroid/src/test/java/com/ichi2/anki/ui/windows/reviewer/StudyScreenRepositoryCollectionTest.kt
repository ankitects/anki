// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.inMemoryPrefs
import org.junit.Test
import kotlin.test.assertEquals

class StudyScreenRepositoryCollectionTest : JvmTest() {
    private val repository = StudyScreenRepository(inMemoryPrefs())

    @Test
    fun `custom scheduling js is correctly retrieved`() =
        runTest {
            val js = CollectionManager.withCol { config.cardStateCustomizer }
            assertEquals(js, repository.getCustomSchedulingJs())

            val newJs = "console.log('Anki is awesome!');"
            CollectionManager.withCol { config.cardStateCustomizer = newJs }
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
