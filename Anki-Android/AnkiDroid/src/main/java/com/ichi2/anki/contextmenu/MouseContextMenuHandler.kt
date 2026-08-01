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

import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.ichi2.anki.R

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

        val popupMenu = PopupMenu(context, anchorView, 0, 0, R.style.OverflowMenuStyle)

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
