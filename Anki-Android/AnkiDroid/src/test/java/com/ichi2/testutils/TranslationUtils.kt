// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A backend translation: its [methodName] and the English [text] it produces.
 */
data class BackendTranslation(
    val methodName: String,
    val text: String,
)

/**
 * An XML string resource: its [name] and English [text].
 */
data class XmlStringResource(
    val name: String,
    val text: String,
)

/**
 * Returns all English strings from the backend which:
 *
 * * Do not contain placeholders: `{name}`
 * * Are non-plural
 *
 * @see TR
 */
fun getBackendNonArgStrings(): List<BackendTranslation> {
    val nonArgMethods =
        GeneratedTranslations::class.java.declaredMethods.filter { method ->
            method.parameterCount == 0 && method.returnType == String::class.java
        }

    return nonArgMethods.mapNotNull { method ->
        runCatching {
            BackendTranslation(method.name, method.invoke(TR) as String)
        }.getOrNull()
    }
}

/**
 * Returns the names of the string resources referenced from `AndroidManifest.xml`
 * (`@string/app_name` -> `app_name`).
 */
fun getAndroidManifestStringResourceNames(): Set<String> =
    Regex("@string/(\\w+)")
        .findAll(File("src/main/AndroidManifest.xml").readText())
        .mapTo(mutableSetOf()) { it.groupValues[1] }

/**
 * Parses all translatable XML files from `res/values/` and extracts
 * `<string>` element names and their text values.
 */
fun getTranslatableXmlStrings(): List<XmlStringResource> {
    val resDir = File("src/main/res/values")
    val translatableFiles =
        resDir
            .listFiles { file ->
                val name = file.name
                name.matches(Regex("\\d+-.*\\.xml")) && name != "12-dont-translate.xml"
            }?.toList() ?: error("Could not list files in $resDir")

    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()

    val result = mutableListOf<XmlStringResource>()
    for (file in translatableFiles) {
        val doc = builder.parse(file)
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i)
            val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
            val value = element.textContent?.trim() ?: continue
            if (value.isNotEmpty()) {
                result.add(XmlStringResource(name, value))
            }
        }
    }
    return result
}
