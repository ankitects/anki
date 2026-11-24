// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.testutil

import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.IntroductionActivity

fun disableIntroductionSlide() {
    AnkiDroidApp.sharedPrefs().edit {
        putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, true)
    }
}
