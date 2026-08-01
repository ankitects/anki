/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R

class CardInfoFragment : PageFragment() {
    override val pagePath: String by lazy {
        val cardId = requireArguments().getLong(KEY_CARD_ID)
        "card-info/$cardId"
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val title = requireArguments().getString(KEY_TITLE)
        if (title != null) {
            view.findViewById<MaterialToolbar>(R.id.toolbar)?.setTitle(title)
        }
    }

    companion object {
        const val KEY_CARD_ID = "cardId"
        const val KEY_TITLE = "title"
    }
}
