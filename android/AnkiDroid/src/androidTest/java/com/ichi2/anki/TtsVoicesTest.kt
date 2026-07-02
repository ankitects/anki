/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.i18n.normalize
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
}
