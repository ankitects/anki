/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.os.Build
import android.webkit.WebView
import androidx.annotation.RequiresApi

object WebViewDebugging {
    private var sHasSetDataDirectory = false

    /** Throws IllegalStateException if a WebView has been initialized  */
    @RequiresApi(api = Build.VERSION_CODES.P)
    fun setDataDirectorySuffix(suffix: String) {
        WebView.setDataDirectorySuffix(suffix)
        sHasSetDataDirectory = true
    }

    fun hasSetDataDirectory(): Boolean {
        // Implicitly truth requires API >= P
        return sHasSetDataDirectory
    }
}
