// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider

/**
 * A general-purpose menu provider for multimedia options.
 * This class implements the MenuProvider interface and can be used in various fragments or activities.
 *
 * @property menuResId The resource ID of the menu to inflate.
 * @property initialMenuItemsVisibility A map containing the visibility state of specific menu items.
 * @property onCreateMenuCondition A lambda function to handle additional conditions when creating the menu.
 * @property onMenuItemClicked A lambda function to handle menu item selections.
 */
class MultimediaMenuProvider(
    private val menuResId: Int,
    private val onCreateMenuCondition: ((Menu) -> Unit)? = null,
    private val onMenuItemClicked: (menuItem: MenuItem) -> Boolean,
) : MenuProvider {
    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menu.clear()
        menuInflater.inflate(menuResId, menu)

        // Apply additional conditions for menu items
        onCreateMenuCondition?.invoke(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = onMenuItemClicked(menuItem)
}
