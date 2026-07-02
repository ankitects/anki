/*
 *  Copyright (c) 2024 Arthur Milchior <Arthur@Milchior.fr>
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

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.ichi2.anki.R
import com.ichi2.anki.compat.setTooltipTextCompat

/**
 * Same as androidx's SearchView, with an extra tooltip.
 * Use this class instead of [androidx.appcompat.widget.SearchView].
 * @see androidx.appcompat.widget.SearchView
 */
open class AccessibleSearchView : androidx.appcompat.widget.SearchView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        // close_btn is the cross that deletes the search field content. It does not close the search view.
        findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
            ?.setTooltipTextCompat(context.getString(R.string.discard))
    }
    // SearchView contains four buttons. The three others seems never to appear in ankidroid.
    // there is also an arrow to the trailing side, that should get a tooltip. Alas, I fail to see the id of this button, so I can't add it.
}
