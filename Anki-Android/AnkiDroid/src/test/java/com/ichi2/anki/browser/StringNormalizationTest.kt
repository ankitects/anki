/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import com.ichi2.anki.utils.ext.normalizeForSearch
import org.junit.Assert.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StringNormalizationTest {
    @ParameterizedTest
    @CsvSource(
        "café Ábaco naïve résumé, cafe Abaco naive resume",
        "élégant déjà vu, elegant deja vu",
        "hello world, hello world",
        "'', ''",
        "1234!@# café, 1234!@# cafe",
    )
    fun `test normalizeForSearch`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, input.normalizeForSearch())
    }
}
