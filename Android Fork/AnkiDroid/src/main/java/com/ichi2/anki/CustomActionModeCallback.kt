// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

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
