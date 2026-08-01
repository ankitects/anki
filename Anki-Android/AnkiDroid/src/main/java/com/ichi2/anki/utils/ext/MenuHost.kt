/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider

/**
 * Adds a [MenuProvider] for modification of an existing combined menu
 *
 * @param onPrepare Called by the [MenuHost] right before the Menu is shown.
 * This should be called when the menu has been dynamically updated.
 */
fun MenuHost.addPrepareMenuProvider(onPrepare: (Menu) -> Unit) {
    this.addMenuProvider(
        object : MenuProvider {
            override fun onCreateMenu(
                menu: Menu,
                inflater: MenuInflater,
            ) {}

            override fun onPrepareMenu(menu: Menu) = onPrepare(menu)

            override fun onMenuItemSelected(item: MenuItem) = false
        },
    )
}
