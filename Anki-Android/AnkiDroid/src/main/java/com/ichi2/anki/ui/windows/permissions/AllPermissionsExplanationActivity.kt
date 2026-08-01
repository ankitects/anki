/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.themes.setTransparentStatusBar

/**
 * When the user opens the Android settings app and navigates to AnkiDroid's permissions,
 * there will be a "more info" button which will launch this activity. See
 * [the docs](https://developer.android.com/training/permissions/explaining-access#privacy-dashboard).
 * This button in the Android settings app is only visible at or above API 31.
 *
 * This activity is used to host the [AllPermissionsExplanationFragment] fragment.
 */
@RequiresApi(Build.VERSION_CODES.S)
class AllPermissionsExplanationActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_permissions_explanation)
        setTransparentStatusBar()

        supportFragmentManager.commit {
            replace(R.id.fragment_container, AllPermissionsExplanationFragment())
        }
    }
}
