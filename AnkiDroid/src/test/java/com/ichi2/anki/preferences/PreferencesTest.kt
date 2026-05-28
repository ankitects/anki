/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.preferences.HeaderFragment.Companion.getHeaderKeyForFragment
import com.ichi2.anki.preferences.PreferenceTestUtils.getAttrFromXml
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.preferences.HeaderPreference
import com.ichi2.testutils.getInstanceFromClassName
import com.ichi2.testutils.getJavaMethodAsAccessible
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PreferencesTest : RobolectricTest() {
    private lateinit var preferences: PreferencesActivity

    @Before
    override fun setUp() {
        super.setUp()
        preferences = PreferencesActivity()
        val attachBaseContext =
            getJavaMethodAsAccessible(
                AppCompatActivity::class.java,
                "attachBaseContext",
                Context::class.java,
            )
        attachBaseContext.invoke(preferences, targetContext)
    }

    @Test
    fun testDayOffsetExhaustive() {
        runTest {
            for (i in 0..23) {
                setDayOffset(preferences, i)
                assertThat(CollectionPreferences.getDayOffset(), equalTo(i))
            }
        }
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun testDayOffsetExhaustiveV2() {
        runTest {
            for (i in 0..23) {
                setDayOffset(preferences, i)
                assertThat(CollectionPreferences.getDayOffset(), equalTo(i))
            }
        }
    }

    /** checks if any of the Preferences fragments throws while being created */
    @Test
    fun fragmentsDoNotThrowOnCreation() {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { activityScenario ->
            activityScenario.onActivity { activity ->
                PreferenceTestUtils.getAllPreferencesFragments(activity).forEach {
                    activity.supportFragmentManager.commitNow {
                        add(R.id.settings_container, it)
                    }
                }
            }
        }
    }

    @Test
    fun `All preferences fragments highlight the correct header`() {
        val headers =
            PreferenceTestUtils
                .getAttrsFromXml(
                    targetContext,
                    R.xml.preference_headers,
                    listOf("key", "fragment"),
                ).filter { it["fragment"] != null }

        assertTrue(headers.all { it["key"] != null })

        fun assertThatFragmentLeadsToHeaderKey(
            fragmentClass: String,
            parentFragmentClass: String? = null,
        ) {
            val fragment = getInstanceFromClassName<Fragment>(fragmentClass)
            val headerFragmentClass = parentFragmentClass ?: fragmentClass
            val expectedKey = headers.first { it["fragment"] == headerFragmentClass }["key"]!!.removePrefix("@").toInt()
            val key = getHeaderKeyForFragment(fragment)
            assertEquals(expectedKey, key, "$fragment (parent $parentFragmentClass) handle error")

            if (fragment is SettingsFragment) {
                val subFragments = getAttrFromXml(targetContext, fragment.preferenceResource, "fragment")
                for (subFragment in subFragments) {
                    assertThatFragmentLeadsToHeaderKey(subFragment, headerFragmentClass)
                }
            }
        }

        for (header in headers) {
            assertThatFragmentLeadsToHeaderKey(header["fragment"]!!)
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun buildHeaderSummary_RTL_Test() {
        assertThat(HeaderPreference.buildHeaderSummary("حساب أنكي ويب", "مزامنة تلقائية"), equalTo("مزامنة تلقائية • حساب أنكي ويب"))
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun setDayOffsetSetsConfig() {
        runTest {
            setDayOffset(preferences, 2)
            assertThat("rollover config should be set to new value", col.config.get("rollover") ?: 4, equalTo(2))
        }
    }
}
