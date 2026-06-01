/*
 * Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 */

package com.ichi2.anki

import android.app.Activity
import android.os.Bundle
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.themes.Themes

/**
 * This class is an abstract base class that extends Activity and provides common initialization logic for [IntentHandler] and [IntentHandler2].
 * By centralizing common setup tasks here, it promotes code reuse.
 */
abstract class AbstractIntentHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.setTheme(this)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.activity_progress_bar)
    }
}
