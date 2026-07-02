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
package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.Menu
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuItemImpl
import com.ichi2.anki.R

class ActionButtons {
    // DEFECT: This should be private - it breaks the law of demeter, but it'll be a large refactoring to get
    // to this point
    val status: ActionButtonStatus = ActionButtonStatus()
    private var menu: Menu? = null

    fun setup(preferences: SharedPreferences) {
        status.setup(preferences)
    }

    /** Sets the order of the Action Buttons in the action bar  */
    fun setCustomButtonsStatus(menu: Menu) {
        status.setCustomButtons(menu)
        this.menu = menu
    }

    fun findMenuItem(
        @IdRes resId: Int,
    ) = menu?.findItem(resId) as? MenuItemImpl

    companion object {
        @IdRes
        val RES_FLAG = R.id.action_flag

        @IdRes
        val RES_MARK = R.id.action_mark_card
    }
}
