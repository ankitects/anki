// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Arthur Milchior <Arthur@Milchior.fr>

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.ichi2.anki.CommonString
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
            ?.setTooltipTextCompat(context.getString(CommonString.discard))
    }
    // SearchView contains four buttons. The three others seems never to appear in ankidroid.
    // there is also an arrow to the trailing side, that should get a tooltip. Alas, I fail to see the id of this button, so I can't add it.
}
