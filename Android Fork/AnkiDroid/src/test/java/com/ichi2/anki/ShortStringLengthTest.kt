// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ShortStringLengthTest : AndroidTest {
    @Test
    fun `short strings are shorter than the strings they replace in every language`() {
        val failures = mutableListOf<String>()
        for (shortField in CommonString::class.java.fields.filter { it.name.endsWith(SHORT_SUFFIX) }) {
            val replacedName = shortField.name.removeSuffix(SHORT_SUFFIX)
            val replacedField = runCatching { CommonString::class.java.getField(replacedName) }.getOrNull()
            if (replacedField == null) {
                failures.add("'${shortField.name}' has no '$replacedName' to replace")
                continue
            }
            val shortId = shortField.getInt(null)
            val replacedId = replacedField.getInt(null)
            val defaultShortText = targetContext.getString(shortId)
            val translations =
                targetContext.assets.locales
                    .map { tag -> tag to targetContext.inLocale(Locale.forLanguageTag(tag)) }
                    .filter { (_, localized) -> localized.getString(shortId) != defaultShortText }
                    .distinctBy { (_, localized) -> localized.getString(shortId) to localized.getString(replacedId) }
            for ((tag, localized) in listOf("default" to targetContext) + translations) {
                val shortText = localized.getString(shortId)
                val replacedText = localized.getString(replacedId)
                if (shortText.length >= replacedText.length) {
                    failures.add("$tag: '${shortField.name}' ($shortText) is not shorter than '$replacedName' ($replacedText)")
                }
            }
        }
        if (failures.any()) {
            fail("Strings ending in '$SHORT_SUFFIX' replace a longer string when space is tight:\n" + failures.joinToString("\n"))
        }
    }

    private fun Context.inLocale(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })

    companion object {
        private const val SHORT_SUFFIX = "_short"
    }
}
