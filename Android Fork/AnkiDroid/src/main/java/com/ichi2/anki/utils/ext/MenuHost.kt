// SPDX-License-Identifier: GPL-3.0-or-later

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
