/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.size

/**
 * Custom ActionMode.Callback implementation for adding and handling cloze deletion action
 * button in the text selection menu or long press.
 */
class CustomActionModeCallback(
    private val isClozeType: Boolean,
    private val clozeMenuTitle: String,
    private val clozeMenuId: Int,
    private val onActionItemSelected: (mode: ActionMode, item: MenuItem) -> Boolean,
) : ActionMode.Callback {
    private val setLanguageId = View.generateViewId()

    override fun onCreateActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean = true

    override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean {
        // Adding the cloze deletion floating context menu item, but only once.
        if (menu.findItem(clozeMenuId) != null) {
            return false
        }
        if (menu.findItem(setLanguageId) != null) {
            return false
        }

        val item: MenuItem? = menu.findItem(android.R.id.pasteAsPlainText)
        val platformPasteMenuItem: MenuItem? = menu.findItem(android.R.id.paste)
        if (item != null && platformPasteMenuItem != null) {
            item.isVisible = false
        }

        val initialSize = menu.size
        if (isClozeType) {
            menu.add(
                Menu.NONE,
                clozeMenuId,
                0,
                clozeMenuTitle,
            )
        }
        return initialSize != menu.size
    }

    override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem,
    ): Boolean = onActionItemSelected(mode, item)

    override fun onDestroyActionMode(mode: ActionMode) {
        // Left empty on purpose
    }
}
