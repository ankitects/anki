// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.DialogExportOptionsBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ExportDialogFragmentKeyboardTest : RobolectricTest(loadBackend = false) {
    @Test
    @Config(sdk = [24, 25, 36])
    fun `deck selector can receive keyboard focus`() {
        val context = ContextThemeWrapper(targetContext, CommonR.style.Theme_AnkiDroid)
        val selector = DialogExportOptionsBinding.inflate(LayoutInflater.from(context)).deckSelector
        selector.setOnClickListener { }

        assertTrue(selector.isFocusable, "Deck selection must be reachable with Tab or D-pad")
    }
}
