// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.fail

/**
 * Preference titles are limited to 41 characters and menu titles to 28, with a matching
 * `maxLength` attribute on the `<string>` so CrowdIn rejects over-long translations.
 */
class PreferenceTitleLengthTest {
    private companion object {
        const val PREFERENCES_TITLE_MAX_LENGTH = 41
        const val MENU_TITLE_MAX_LENGTH = 28

        val STRING_RESOURCE_DIRS =
            listOf(
                // test working directory is the module directory: `AnkiDroid/`
                File("src/main/res/values"),
                File("../common/android/src/main/res/values"),
            )
    }

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    /** name -> (maxLength attribute or null, text content) */
    private data class StringResource(
        val maxLength: Int?,
        val text: String,
    )

    private val stringResources: Map<String, StringResource> by lazy {
        val result = mutableMapOf<String, StringResource>()
        for (dir in STRING_RESOURCE_DIRS) {
            val files = dir.listFiles { file -> file.extension == "xml" } ?: error("Could not list files in $dir")
            for (file in files) {
                val strings = documentBuilder.parse(file).getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val element = strings.item(i)
                    val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
                    val maxLength =
                        element.attributes
                            .getNamedItem("maxLength")
                            ?.nodeValue
                            ?.toInt()
                    result[name] = StringResource(maxLength, element.textContent)
                }
            }
        }
        result
    }

    /** Names of the string resources used as `android:title` in the given res folder. */
    private fun titlesIn(folder: String): Set<String> {
        val files = File("src/main/res/$folder").listFiles { file -> file.extension == "xml" } ?: error("Could not list res/$folder")
        val titles = mutableSetOf<String>()
        for (file in files) {
            val elements = documentBuilder.parse(file).getElementsByTagName("*")
            for (i in 0 until elements.length) {
                val title =
                    elements
                        .item(i)
                        .attributes
                        ?.getNamedItem("android:title")
                        ?.nodeValue ?: continue
                val stringName = title.substringAfter("@string/", "").ifEmpty { continue }
                titles.add(stringName)
            }
        }
        return titles
    }

    private fun checkTitles(
        folder: String,
        maxLength: Int,
    ) {
        val failures = mutableListOf<String>()
        for (title in titlesIn(folder)) {
            val resource = stringResources[title]
            if (resource == null) {
                failures.add("'$title' is not defined in any of $STRING_RESOURCE_DIRS")
                continue
            }
            when {
                resource.maxLength == null ->
                    failures.add("'$title' is missing the maxLength=\"$maxLength\" attribute")
                resource.maxLength > maxLength ->
                    failures.add("'$title' has maxLength=\"${resource.maxLength}\"; it should be at most $maxLength")
            }
            if (resource.text.length > maxLength) {
                failures.add("'$title' must be less than $maxLength characters (currently ${resource.text.length})")
            }
        }
        if (failures.any()) {
            fail("res/$folder titles are too long for small screens/translations:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `preference titles are constrained to 41 characters`() = checkTitles("xml", PREFERENCES_TITLE_MAX_LENGTH)

    @Test
    fun `menu titles are constrained to 28 characters`() = checkTitles("menu", MENU_TITLE_MAX_LENGTH)
}
