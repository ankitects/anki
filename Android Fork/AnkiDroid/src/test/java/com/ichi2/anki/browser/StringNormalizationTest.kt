// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

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
