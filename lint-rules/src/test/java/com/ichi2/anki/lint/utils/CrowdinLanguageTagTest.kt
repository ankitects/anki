// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test

typealias ManuallyVerifiedString = String?

class CrowdinLanguageTagTest {
    @Test
    fun manuallyTestLinks() {
        // when adding a new string, define it as 'null' and this test will
        // provide a URL for you to manually test:
        // example: "values-xx" to null
        // Once the URL works, replace 'null' with the language Crowdin uses

        // manually verified by David (2024-09-30)
        val folderNames =
            mapOf<String, ManuallyVerifiedString>(
                "values-af" to "af",
                "values-am" to "am",
                "values-ar" to "ar",
                "values-az" to "az",
                "values-be" to "be",
                "values-bg" to "bg",
                "values-bn" to "bn",
                "values-ca" to "ca",
                "values-ckb" to "ckb",
                "values-cs" to "cs",
                "values-da" to "da",
                "values-de" to "de",
                "values-el" to "el",
                "values-en" to "en",
                "values-eo" to "eo",
                "values-es-rAR" to "esar",
                "values-es-rES" to "es",
                "values-et" to "et",
                "values-eu" to "eu",
                "values-fa" to "fa",
                "values-fi" to "fi",
                "values-fil" to "fil",
                "values-fr" to "fr",
                "values-fy" to "fy",
                "values-ga" to "ga",
                "values-gl" to "gl",
                "values-got" to "got",
                "values-gu" to "gu",
                "values-heb" to "he",
                "values-hi" to "hi",
                "values-hr" to "hr",
                "values-hu" to "hu",
                "values-hy" to "hy",
                "values-ind" to "id",
                "values-is" to "is",
                "values-it" to "it",
                "values-iw" to "he",
                "values-ja" to "ja",
                "values-jv" to "jv",
                "values-ka" to "ka",
                "values-kk" to "kk",
                "values-km" to "km",
                "values-kn" to "kn",
                "values-ko" to "ko",
                "values-ku" to "ku",
                "values-ky" to "ky",
                "values-lt" to "lt",
                "values-lv" to "lv",
                "values-mk" to "mk",
                "values-ml" to "mlin",
                "values-mn" to "mn",
                "values-mr" to "mr",
                "values-ms" to "ms",
                "values-my" to "my",
                "values-nl" to "nl",
                "values-nn" to "nnno",
                "values-no" to "no",
                "values-or" to "or",
                "values-pa" to "pain",
                "values-pl" to "pl",
                "values-pt-rBR" to "ptbr",
                "values-pt-rPT" to "pt",
                "values-ro" to "ro",
                "values-ru" to "ru",
                "values-sat" to "sat",
                "values-sc" to "sc",
                "values-sk" to "sk",
                "values-sl" to "sl",
                "values-sq" to "sq",
                "values-sr" to "sr",
                "values-ss" to "ss",
                "values-sv" to "sv",
                "values-ta" to "ta",
                "values-te" to "te",
                "values-tg" to "tg",
                "values-tgl" to "tl",
                "values-th" to "th",
                "values-ti" to "ti",
                "values-tn" to "tn",
                "values-tr" to "tr",
                "values-ts" to "ts",
                "values-tt" to "ttru",
                "values-uk" to "uk",
                "values-ur" to "urpk",
                "values-uz" to "uz",
                "values-ve" to "ve",
                "values-vi" to "vi",
                "values-wo" to "wo",
                "values-xh" to "xh",
                "values-yue" to "yu",
                "values-zh-rCN" to "zhcn",
                "values-zh-rTW" to "zhtw",
                "values-zu" to "zu",
            )

        val fileIdentifier = CrowdinFileIdentifier(7290)

        val stringsToTest = mutableListOf<String>()
        for ((folderName, expected) in folderNames) {
            val languageTag = CrowdinLanguageTag.fromFolderName(folderName)!!

            if (expected != null) {
                assertThat(languageTag.toString(), equalTo(expected))
                continue
            }

            val context = CrowdinContext(languageTag, fileIdentifier)

            // the '_' in the name ensures the strings only match names, and not the English
            val actual = context.getEditUrl("send_feedback")

            stringsToTest.add(actual)
        }

        // display a list of the languages to test (if any)
        println(stringsToTest.joinToString(separator = "\n"))
        assertThat(
            "all values should be manually tested, test the links printed above",
            stringsToTest,
            hasSize(0),
        )
    }
}
