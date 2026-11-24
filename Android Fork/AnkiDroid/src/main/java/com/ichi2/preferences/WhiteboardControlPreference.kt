// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import com.ichi2.anki.preferences.allPreferences
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.ui.GesturePicker
import com.ichi2.ui.WhiteboardGesturePicker

class WhiteboardControlPreference : ReviewerControlPreference {
    override var side: CardSide? = CardSide.BOTH

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, android.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun createGesturePicker(): GesturePicker = WhiteboardGesturePicker(context)

    override fun getRelatedPreferences(): List<WhiteboardControlPreference> =
        preferenceManager.preferenceScreen.allPreferences().filterIsInstance<WhiteboardControlPreference>()
}
