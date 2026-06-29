// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.ui.windows.reviewer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.previewer.CardViewerActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ReviewerBigScreenTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "w1000dp-h1000dp-480dpi")
    fun `sw600dp layout is initialized`() {
        ensureCollectionLoadIsSynchronous()
        val intent = ReviewerFragment.getIntent(targetContext)
        ActivityScenario.launch<CardViewerActivity>(intent).close()
    }
}
