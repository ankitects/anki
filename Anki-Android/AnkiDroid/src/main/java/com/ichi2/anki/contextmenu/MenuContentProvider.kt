/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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
