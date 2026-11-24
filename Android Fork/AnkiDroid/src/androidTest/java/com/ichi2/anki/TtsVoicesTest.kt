// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.i18n.normalize
import com.ichi2.anki.i18n.toAnkiTwoLetterCode
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TtsVoicesTest : InstrumentedTest() {
    @Test
    fun normalize() {
        fun assertEqual(
            l: Locale,
            str: String,
        ) {
            val normalized = l.normalize()
            assertThat(normalized.toLanguageTag(), equalTo(str))
        }

        assertEqual(Locale.forLanguageTag("en" + '-' + "GB"), "en-GB")
        assertEqual(Locale.forLanguageTag("es" + '-' + "MX"), "es-MX")
        // "spa" is an invalid language and historically was remapped from that very old ISO code
        // to "es" by the deprecated Locale constructor. It is discarded in modern times, but
        // the country still comes back as the "language" for us for display
        assertEqual(Locale.forLanguageTag("spa" + '-' + "MEX"), "mex")
        assertEqual(Locale.forLanguageTag("fil" + '-' + "PH"), "fil-PH")
        // TBC
        assertEqual(Locale.forLanguageTag("ar" + '-' + ""), "ar")
    }

    @Test
    fun toAnkiTwoLetterCodeUsesCurrentLanguageCodes() {
        assertThat(Locale.forLanguageTag("he-IL").toAnkiTwoLetterCode(), equalTo("he_IL"))
        assertThat(Locale.forLanguageTag("id-ID").toAnkiTwoLetterCode(), equalTo("id_ID"))
        assertThat(Locale.forLanguageTag("yi").toAnkiTwoLetterCode(), equalTo("yi"))
        assertThat(Locale.forLanguageTag("iw-IL").toAnkiTwoLetterCode(), equalTo("he_IL"))
        assertThat(Locale.forLanguageTag("in-ID").toAnkiTwoLetterCode(), equalTo("id_ID"))
        assertThat(Locale.forLanguageTag("ji").toAnkiTwoLetterCode(), equalTo("yi"))
    }
}
