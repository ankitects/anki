/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.R

enum class MenuDisplayType(
    @StringRes val title: Int,
) {
    /** Shows the action as [MenuItem.SHOW_AS_ACTION_ALWAYS] */
    ALWAYS(R.string.custom_buttons_setting_always_show),

    /** Shows the action as [MenuItem.SHOW_AS_ACTION_NEVER] */
    MENU_ONLY(R.string.custom_buttons_setting_menu_only),

    /** Action isn't added to the menu */
    DISABLED(R.string.disabled),
    ;

    @VisibleForTesting
    val preferenceKey get() = "ReviewerMenuDisplayType_$name"
}
