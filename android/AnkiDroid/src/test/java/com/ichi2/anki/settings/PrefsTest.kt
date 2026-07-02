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
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest {
    @Test
    fun `getters and setters use the same key`() {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val mockResources = mockk<Resources>()
        var lastKey = 0
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            lastKey = resId
            resId.toString()
        }
        val prefs = PrefsRepository(sharedPrefs, mockResources)

        for (property in PrefsRepository::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC || property !is KMutableProperty<*>) continue

            property.getter.call(prefs)
            val getterKey = lastKey

            when (property.returnType.classifier) {
                Boolean::class -> property.setter.call(prefs, false)
                String::class -> property.setter.call(prefs, "foo")
                else -> continue
            }
            val setterKey = lastKey

            assertThat("The getter and setter of '${property.name}' use the same key", getterKey, equalTo(setterKey))
        }
    }
}
