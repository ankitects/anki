// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytehamster.lib.preferencesearch.PreferenceItem
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.requireAccessibleJavaField
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PrefsSearchBarTest : RobolectricTest() {
    @Test
    fun `indexed resources resolve with legacy reminders`() = assertIndexedResources(newReviewRemindersEnabled = false)

    @Test
    fun `indexed resources resolve with new reminders`() = assertIndexedResources(newReviewRemindersEnabled = true)

    @Suppress("UNCHECKED_CAST")
    private fun assertIndexedResources(newReviewRemindersEnabled: Boolean) {
        Prefs.newReviewRemindersEnabled = newReviewRemindersEnabled
        // TODO try mocking the activity
        val preferencesActivity = getPreferencesActivity()
        val searchConfig = SearchConfiguration(preferencesActivity)
        HeaderFragment.configureSearchBar(preferencesActivity, searchConfig)

        // Use reflection to access some private fields
        val filesToIndexField = requireAccessibleJavaField<SearchConfiguration>("filesToIndex")
        val searchItemResIdField = requireAccessibleJavaField<SearchConfiguration.SearchIndexItem>("resId")
        val preferencesToIndexField = requireAccessibleJavaField<SearchConfiguration>("preferencesToIndex")
        val prefItemResIdField = requireAccessibleJavaField<PreferenceItem>("resId")

        // Get the resIds of the files indexed with `SearchConfiguration.index`
        val filesToIndex = filesToIndexField.get(searchConfig) as ArrayList<SearchConfiguration.SearchIndexItem>
        val filesResIds =
            filesToIndex.map {
                searchItemResIdField.get(it)
            }

        // Get the resIds of preferences indexed with `SearchConfiguration.indexItem`
        val preferencesToIndex = preferencesToIndexField.get(searchConfig) as ArrayList<PreferenceItem>
        val prefItemsResIds =
            preferencesToIndex.map {
                prefItemResIdField.get(it)
            }

        // Join both lists
        val allResIds =
            filesResIds
                .plus(prefItemsResIds)
                .distinct() as List<Int>

        assertEquals(newReviewRemindersEnabled, R.xml.preferences_review_reminders in allResIds)
        assertEquals(!newReviewRemindersEnabled, R.xml.preferences_notifications in allResIds)

        // Check both the normal settings mapping and the custom review reminders destination.
        for (resId in allResIds) {
            if (resId == R.xml.preferences_review_reminders) {
                assertReviewRemindersDestination(preferencesActivity)
                continue
            }

            val fragment = getFragmentFromXmlRes(resId)

            val resName = targetContext.resources.getResourceName(resId)

            assertNotNull(fragment, message = "Could not resolve fragment for resource: $resName")

            // Special handling for ControlsSettingsFragment which handles multiple XML resources
            val expectedResourceId =
                when (fragment) {
                    is ControlsSettingsFragment -> fragment.preferenceResource
                    else -> resId
                }

            assertThat(
                "${targetContext.resources.getResourceName(resId)} should be handled by ${fragment::class.simpleName}",
                fragment.preferenceResource,
                equalTo(expectedResourceId),
            )
        }
    }

    private fun assertReviewRemindersDestination(activity: PreferencesActivity) {
        val result =
            mock<SearchPreferenceResult> {
                on { resourceFile } doReturn R.xml.preferences_review_reminders
            }
        activity.onSearchResultClicked(result)
        advanceRobolectricLooper()

        val preferences = activity.fragment as PreferencesFragment
        assertIs<ScheduleRemindersFragment>(preferences.childFragmentManager.findFragmentById(R.id.settings_container))
    }

    private fun getPreferencesActivity(): PreferencesActivity {
        val intent = PreferencesDestination.Root.toIntent(targetContext)
        val controller =
            Robolectric
                .buildActivity(PreferencesActivity::class.java, intent)
                .create()
                .start()
                .resume()
        saveControllerForCleanup(controller)
        return controller.get()
    }
}
