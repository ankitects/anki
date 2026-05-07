/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.PreferenceTestUtils.getAttrsFromXml
import com.ichi2.anki.preferences.PreferenceTestUtils.resValue
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PrefsRobolectricTest : RobolectricTest() {
    private fun getKeysAndDefaultValues(): MutableMap<String, Any?> {
        val sharedPrefsSpy = spy(SPMockBuilder().createSharedPreferences())
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        val prefs = PrefsRepository(sharedPrefsSpy, mockResources)

        val keysAndDefaultValues: MutableMap<String, Any?> = mutableMapOf()
        doAnswer { invocation ->
            val key = invocation.arguments[0] as String
            keysAndDefaultValues[key] = invocation.arguments[1]
            invocation.callRealMethod()
        }.run {
            whenever(sharedPrefsSpy).getBoolean(any(), any())
            whenever(sharedPrefsSpy).getString(any(), anyOrNull())
            whenever(sharedPrefsSpy).getInt(any(), any())
        }

        for (property in PrefsRepository::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            property.getter.call(prefs)
        }

        return keysAndDefaultValues
    }

    @Test
    fun `all default values match the preference XMLs`() {
        val keysAndDefaultValues = getKeysAndDefaultValues()
        val developerOptionsKeys = PreferenceTestUtils.getDeveloperOptionsKeys(targetContext)
        val prefs =
            PreferenceTestUtils
                .getAllPreferencesFragments(targetContext)
                .asSequence()
                .filterIsInstance<SettingsFragment>()
                .map { it.preferenceResource }
                .flatMap { getAttrsFromXml(targetContext, it, listOf("defaultValue", "key")) }
                .filter { it["key"] != null }
                .associate { it["key"]!!.resValue(targetContext) to it["defaultValue"] }

        for ((key, defaultValue) in keysAndDefaultValues.entries) {
            if (key !in prefs || key in developerOptionsKeys) continue
            val prefsDefaultValue = prefs.getValue(key)
            assertThat("The default value of '$key' matches the preference XML", defaultValue.toString(), equalTo(prefsDefaultValue))
        }
    }

    private fun getPropertyNamesAndKeys(): MutableMap<String, String> {
        val sharedPrefsSpy = spy(SPMockBuilder().createSharedPreferences())
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        val prefs = PrefsRepository(sharedPrefsSpy, mockResources)
        val keys = mutableListOf<String>()

        doAnswer { invocation ->
            val key = "@${invocation.arguments[0]}".resValue(targetContext)
            keys.append(key)
            invocation.callRealMethod()
        }.run {
            whenever(sharedPrefsSpy).getBoolean(any(), any())
            whenever(sharedPrefsSpy).getString(any(), anyOrNull())
            whenever(sharedPrefsSpy).getInt(any(), any())
        }
        val propertyNamesAndKeys = mutableMapOf<String, String>()
        for (property in PrefsRepository::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            property.getter.call(prefs)
            propertyNamesAndKeys[property.name] = keys.last()
        }
        return propertyNamesAndKeys
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `PrefEnum values match their preference entries`() {
        // Prefs property name (String) -> Key (String)
        val allPropertiesAndKeys = getPropertyNamesAndKeys()
        val enumProperties =
            Prefs::class.memberProperties.filter {
                it.returnType.isSubtypeOf(PrefEnum::class.createType())
            }
        // Key (String) -> Prefs property
        val enumPropertiesMap = enumProperties.associateBy { allPropertiesAndKeys.getValue(it.name) }
        // Key (String) -> PrefEnum entryValues (List<String>)
        val prefsEnumKeysAndValues = mutableMapOf<String, List<String>>()
        for ((key, property) in enumPropertiesMap.entries) {
            val enumConstants = ((property.returnType.classifier as KClass<*>).java.enumConstants) as Array<PrefEnum>
            prefsEnumKeysAndValues[key] = enumConstants.map { targetContext.resources.getString(it.entryResId) }
        }

        val listPreferences =
            PreferenceTestUtils
                .getAllPreferencesFragments(targetContext)
                .filterIsInstance<SettingsFragment>()
                .map { it.preferenceResource }
                .flatMap { getAttrsFromXml(targetContext, it, listOf("key", "entryValues")) }
                .filter { it["entryValues"] != null }
                .associate {
                    it["key"]!!.resValue(targetContext) to
                        PreferenceTestUtils.attrToStringArray(it["entryValues"]!!, targetContext).toList()
                }

        for ((key, enumValues) in prefsEnumKeysAndValues) {
            assertContains(listPreferences, key)
            assertEquals(enumValues, listPreferences[key])
        }
    }
}
