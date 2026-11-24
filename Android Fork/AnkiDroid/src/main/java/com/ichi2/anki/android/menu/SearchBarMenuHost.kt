// SPDX-License-Identifier: GPL-3.0-or-later

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
