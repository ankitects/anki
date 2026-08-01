/*
 * Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
