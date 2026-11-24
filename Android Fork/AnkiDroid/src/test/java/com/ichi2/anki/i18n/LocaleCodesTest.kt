// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.i18n

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class LocaleCodesTest {
    @Test
    fun `obsolete language codes are replaced`() {
        assertThat("iw_IL".replaceObsoleteLanguageCode(), equalTo("he_IL"))
        assertThat("in_ID".replaceObsoleteLanguageCode(), equalTo("id_ID"))
        assertThat("ji".replaceObsoleteLanguageCode(), equalTo("yi"))
    }

    @Test
    fun `current language codes are unchanged`() {
        assertThat("he_IL".replaceObsoleteLanguageCode(), equalTo("he_IL"))
        assertThat("en_IN".replaceObsoleteLanguageCode(), equalTo("en_IN"))
        assertThat("ind_ID".replaceObsoleteLanguageCode(), equalTo("ind_ID"))
    }
}
