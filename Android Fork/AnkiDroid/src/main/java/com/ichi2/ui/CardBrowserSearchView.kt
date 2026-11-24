// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet

class CardBrowserSearchView : AccessibleSearchView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    /** Whether an action to set text should be ignored  */
    var ignoreValueChange = false
        private set

    override fun onActionViewCollapsed() {
        try {
            ignoreValueChange = true
            super.onActionViewCollapsed()
        } finally {
            ignoreValueChange = false
        }
    }

    override fun onActionViewExpanded() {
        try {
            ignoreValueChange = true
            super.onActionViewExpanded()
        } finally {
            ignoreValueChange = false
        }
    }

    override fun setQuery(
        query: CharSequence,
        submit: Boolean,
    ) {
        if (ignoreValueChange) {
            return
        }
        super.setQuery(query, submit)
    }
}
