// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.testutils.inMemoryPrefs
import io.mockk.every
import io.mockk.spyk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest {
    @Test
    fun `getters and setters use the same key`() {
        val readKeys = mutableListOf<String>()
        val sharedPrefs =
            spyk(SPMockBuilder().createSharedPreferences()) {
                every { getBoolean(capture(readKeys), any()) } answers { callOriginal() }
                every { getString(capture(readKeys), any()) } answers { callOriginal() }
                every { getInt(capture(readKeys), any()) } answers { callOriginal() }
                every { getLong(capture(readKeys), any()) } answers { callOriginal() }
            }
        val prefs = inMemoryPrefs(sharedPrefs)

        val checked =
            PrefsRepository::class.memberProperties.filter { property ->
                property.visibility == KVisibility.PUBLIC && property is KMutableProperty1<*, *>
            }
        assertThat("mutable preferences are checked", checked, not(empty()))

        for (property in checked) {
            property as KMutableProperty1<*, *>

            readKeys.clear()
            property.getter.call(prefs)
            val getterKeys = readKeys.toSet()

            sharedPrefs.edit { clear() }
            property.setter.call(prefs, sampleValue(property.returnType))
            val setterKeys = sharedPrefs.all.keys

            assertThat("The setter of '${property.name}' writes a key", setterKeys, not(empty()))
            assertThat("The getter and setter of '${property.name}' use the same key", getterKeys, equalTo(setterKeys))
        }
    }

    private fun sampleValue(type: KType): Any =
        when (val kClass = type.classifier as KClass<*>) {
            Boolean::class -> false
            String::class -> "foo"
            Int::class -> 1
            Long::class -> 1L
            else -> kClass.java.enumConstants?.first() ?: error("Unsupported preference type: $type")
        }
}
