// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.timebudget

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.launchCatchingTask

/**
 * The RWKV settings that are not part of a time budget. The Android counterpart of
 * RwkvOptionsDialog in qt/aqt/rwkv_options.py.
 *
 * @param onSaved called after the options were saved, eg. to refresh the deck list
 */
fun FragmentActivity.showRwkvOptionsDialog(onSaved: () -> Unit) {
    launchCatchingTask {
        val options = withCol { loadRwkvOptions() }
        val padding = 16.dp.toPx(this@showRwkvOptionsDialog)
        val allDecks =
            CheckBox(this@showRwkvOptionsDialog).apply {
                setText(R.string.rwkv_all_decks)
                isChecked = options.allDecks
            }
        val help =
            TextView(this@showRwkvOptionsDialog).apply {
                setText(R.string.rwkv_all_decks_help)
                alpha = 0.7f
            }
        val content =
            LinearLayout(this@showRwkvOptionsDialog).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
                addView(allDecks, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(help, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        AlertDialog
            .Builder(this@showRwkvOptionsDialog)
            .setTitle(R.string.rwkv_options_title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launchCatchingTask {
                    withCol { saveRwkvOptions(RwkvOptions(allDecks = allDecks.isChecked)) }
                    onSaved()
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
