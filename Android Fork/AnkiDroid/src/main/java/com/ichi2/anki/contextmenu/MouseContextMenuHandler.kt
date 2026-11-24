// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.contextmenu

import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.ichi2.anki.common.android.R as CommonR

/**
 * Helper class to build a context menu for mouse right-click events.
 *
 * This class can be attached to any view's rightClick listener it provides a context menu
 * using a provided [MenuContentProvider] for menu content and handling.
 */
open class MouseContextMenuHandler(
    private val viewGroup: ViewGroup,
    private val menuContentProvider: MenuContentProvider,
) {
    /**
     * Shows a context menu at the mouse cursor position using the configured MenuContentProvider.
     *
     * @param view The view that was right-clicked
     * @param x The x coordinate of the mouse click
     * @param y The y coordinate of the mouse click
     */
    fun showContextMenu(
        view: View,
        x: Float,
        y: Float,
    ) {
        val context = view.context

        val anchorView =
            View(context).apply {
                /*
                Position the anchor at the mouse coordinates this will not cause menu to
                appear off-screen because popup menu will automatically adjust its position
                if it would otherwise be off-screen.
                 */
                this.x = x
                this.y = y
                this.layoutParams = ViewGroup.LayoutParams(1, 1)
            }

        viewGroup.addView(anchorView)

        val popupMenu = PopupMenu(context, anchorView, 0, 0, CommonR.style.OverflowMenuStyle)

        menuContentProvider.populateMenu(popupMenu.menu)

        menuContentProvider.onPrepareMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            menuContentProvider.onMenuItemSelected(menuItem)
        }

        popupMenu.setOnDismissListener {
            viewGroup.removeView(anchorView)
        }

        popupMenu.show()
    }
}
