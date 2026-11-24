// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.view.View
import android.widget.AdapterView

/**
 * [AdapterView.OnItemSelectedListener] which handles [onItemSelected] and not [onNothingSelected]
 */
class BasicItemSelectedListener(
    private val onItemSelected: (position: Int, id: Long) -> Unit,
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long,
    ) = onItemSelected(position, id)

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // do nothing
    }
}
