// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.utils

import com.android.tools.lint.detector.api.ResourceContext
import org.w3c.dom.Element
import java.io.File
import java.util.Locale

/**
 * Identifier of an XML file in a Crowdin URL.
 * 8236 -> `20-search-preference.xml` in the URL https://crowdin.com/editor/ankidroid/8236/en-yu
 */
@JvmInline
value class CrowdinFileIdentifier(
    private val value: Long,
) {
    override fun toString(): String = value.toString()

    companion object {
        private val fileNameToIdentifier =
            mapOf(
                "01-core" to 7290,
                "02-strings" to 7291,
                "03-dialogs" to 7303,
                "04-network" to 8167,
                "05-feedback" to 8168,
                "06-statistics" to 8169,
                "07-cardbrowser" to 8170,
                "08-widget" to 8171,
                "09-backup" to 8172,
                "10-preferences" to 8173,
                "11-arrays" to 8174,
                "16-multimedia-editor" to 8229,
                "17-model-manager" to 8230,
                "18-standard-models" to 8232,
                "20-search-preference" to 8236,
            ).mapValues { CrowdinFileIdentifier(it.value.toLong()) }

        fun fromFile(file: File): CrowdinFileIdentifier? = fileNameToIdentifier[file.nameWithoutExtension]
    }
}

/**
 * The language key which Crowdin uses to represent the language: `yu`, NOT `yue`
 */
@JvmInline
value class CrowdinLanguageTag(
    private val tag: String,
) {
    override fun toString() = tag

    companion object {
        private val customMappings =
            mapOf(
                // ** from tools/localization/src/update.ts **
                "yue" to "yu",
                "heb" to "he",
                "iw" to "he",
                "ind" to "id",
                "tgl" to "tl",
                // ** Other weirdness **
                // Malayalam
                "ml" to "mlin",
                // Punjabi
                "pa" to "pain",
                // Norwegian Nynorsk
                "nn" to "nnno",
                // Tatar (Russia)
                "tt" to "ttru",
                // Urdu (Pakistan)
                "ur" to "urpk",
                // ** Crowdin does not handle 'Spanish (Spain)' as 'eses', it needs 'es' **
                "eses" to "es",
                "ptpt" to "pt",
            )

        fun fromFolderName(folderName: String): CrowdinLanguageTag? {
            if (!folderName.startsWith("values-")) return null

            val language =
                folderName
                    .substring("values-".length)
                    .replace("-r", "") // es-rAR -> esAR
                    .lowercase(Locale.ROOT) // esAR -> esar

            val crowdinLanguage = customMappings[language] ?: language

            return CrowdinLanguageTag(crowdinLanguage)
        }

        fun fromFolder(folder: File): CrowdinLanguageTag? = fromFolderName(folder.name)
    }
}

/**
 * How Crowdin represents the Android path of `values-yue/01-core.xml`
 * @param languageTag How 'values-zh-rCN' is represented. See [CrowdinLanguageTag]
 * @param fileIdentifier How `01-core` is represented. See [CrowdinFileIdentifier]
 */
data class CrowdinContext(
    val languageTag: CrowdinLanguageTag,
    val fileIdentifier: CrowdinFileIdentifier,
) {
    private fun getStringName(element: Element?): String? {
        if (element == null) return null
        return when (element.tagName) {
            // <string-array> or <plurals>
            "item" -> {
                val parentElement = element.parentNode as? Element? ?: return null
                getStringName(parentElement)
            }
            else -> if (element.hasAttribute("name")) element.getAttribute("name") else null
        }
    }

    fun getEditUrl(element: Element): String? {
        val stringName = getStringName(element) ?: return null
        return getEditUrl(stringName)
    }

    /** Example: [https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck](https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck) */
    fun getEditUrl(string: String): String = "https://crowdin.com/editor/ankidroid/$fileIdentifier/en-$languageTag#q=$string"

    companion object {
        fun ResourceContext.toCrowdinContext(): CrowdinContext? {
            return CrowdinContext(
                languageTag = CrowdinLanguageTag.fromFolder(file.parentFile) ?: return null,
                fileIdentifier = CrowdinFileIdentifier.fromFile(file) ?: return null,
            )
        }
    }
}
