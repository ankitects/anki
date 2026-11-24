// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.contextmenu

import android.view.Menu
import android.view.MenuItem

/**
 * Interface for providing menu content and handling menu item selections.
 * This allows different components (DeckPicker, CardBrowser, etc.) to provide
 * their own menu logic while reusing the same inflation mechanism.
 */
interface MenuContentProvider {
    /**
     * Populates the given menu with items appropriate for the current context.
     * This could be from XML resources, programmatically added items, or a combination.
     */
    fun populateMenu(menu: Menu)

    /**
     * Handles selection of a menu item.
     * @param item The selected menu item
     * @return true if the item was handled, false otherwise
     */
    fun onMenuItemSelected(item: MenuItem): Boolean

    /**
     * Called when the menu is about to be shown, allowing for dynamic updates
     * based on current state (optional override)
     */
    fun onPrepareMenu(menu: Menu)
}
