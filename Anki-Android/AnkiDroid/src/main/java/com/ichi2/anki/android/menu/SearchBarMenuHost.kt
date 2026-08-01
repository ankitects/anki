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

package com.ichi2.anki.android.menu

import android.view.Menu
import android.view.MenuInflater
import androidx.core.view.MenuHost
import androidx.core.view.MenuHostHelper
import androidx.core.view.MenuItemCompat
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.search.SearchBar
import com.ichi2.ui.RtlCompliantActionProvider
import timber.log.Timber

/**
 * Simplifies the implementation of a [MenuHost] delegating to a Material 3 [SearchBar]
 */
interface SearchBarMenuHost : MenuHost {
    /**
     * Required implementation of [MenuHost] functionality.
     *
     * Build using:
     * ```kotlin
     * MenuHostHelper { invalidateMenu() }
     * ```
     */
    val menuHostHelper: MenuHostHelper

    val searchBar: SearchBar?

    /**
     * Used to instantiate menu XML files into Menu objects.
     *
     * Typically `activity?.menuInflater`
     */
    val menuInflater: MenuInflater?

    override fun addMenuProvider(provider: MenuProvider) = menuHostHelper.addMenuProvider(provider)

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
    ) = menuHostHelper.addMenuProvider(provider, owner)

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
        state: Lifecycle.State,
    ) = menuHostHelper.addMenuProvider(provider, owner, state)

    override fun removeMenuProvider(provider: MenuProvider) = menuHostHelper.removeMenuProvider(provider)

    override fun invalidateMenu() = invalidateSearchBarMenu()

    // alias to make `MenuHostHelper { invalidateMenu() }` more readable
    // now: `MenuHostHelper { invalidateSearchBarMenu() }`
    fun invalidateSearchBarMenu() {
        searchBar?.menu?.rebuild(menuHostHelper, menuInflater)
    }
}

private fun Menu.rebuild(
    menuHostHelper: MenuHostHelper,
    menuInflater: MenuInflater?,
) {
    clear()

    // invalidateMenu may be called after `onDestroy`
    if (menuInflater == null) {
        Timber.d("unable to rebuild menu - no inflater")
        return
    }

    menuHostHelper.onCreateMenu(this, menuInflater)
    menuHostHelper.onPrepareMenu(this)

    forEach { menuItem ->
        // Setup RtlCompliantActionProvider (undo)
        val rtlActionProvider = MenuItemCompat.getActionProvider(menuItem) as? RtlCompliantActionProvider
        rtlActionProvider?.clickHandler = { _, menuItem -> menuHostHelper.onMenuItemSelected(menuItem) }

        menuItem.setOnMenuItemClickListener { item ->
            menuHostHelper.onMenuItemSelected(item)
        }
    }
}
