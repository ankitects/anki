/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R

private const val DEFAULT_HORIZONTAL_PADDING = 4F

private fun Menu.itemsRecursive(): Sequence<MenuItem> =
    sequence {
        forEach { item ->
            yield(item)
            item.subMenu?.let { yieldAll(it.itemsRecursive()) }
        }
    }

private fun Menu.overflowItemsRecursive(): Sequence<MenuItem> =
    sequence {
        (this@overflowItemsRecursive as? MenuBuilder)?.flagActionItems()

        forEach { item ->
            if ((item as? MenuItemImpl)?.isActionButton == false) {
                yield(item)
            }
            item.subMenu?.let {
                yieldAll(it.overflowItemsRecursive())
            }
        }
    }

private fun Context.increaseHorizontalPaddingOfMenuIcons(
    items: Sequence<MenuItem>,
    paddingDp: Float = DEFAULT_HORIZONTAL_PADDING,
) {
    val paddingPx = paddingDp.dp.toPx(this)

    items.forEach { item ->
        item.icon?.let { icon ->
            if (icon !is InsetDrawable) {
                item.icon = InsetDrawable(icon, paddingPx, 0, paddingPx, 0)
            }
        }
    }
}

fun MenuItem.setPaddedIcon(
    context: Context,
    @DrawableRes drawableResId: Int,
    horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING,
) {
    val padding = horizontalPaddingDp.dp.toPx(context)
    val drawable = ContextCompat.getDrawable(context, drawableResId)
    icon = InsetDrawable(drawable, padding, 0, padding, 0)
}

/**
 * Recursively increase horizontal icon padding for the overflown items of the given menu,
 * so that the icon visually appears at the same distance from the starting edge of the popup
 * as from the top and the bottom edges, as well as from the label.
 * Has no effect for items that have no icon, or for items this has processed before.
 */
fun Context.increaseHorizontalPaddingOfOverflowMenuIcons(
    menu: Menu,
    paddingDp: Float = DEFAULT_HORIZONTAL_PADDING,
) = increaseHorizontalPaddingOfMenuIcons(menu.overflowItemsRecursive(), paddingDp)

/**
 * Recursively increase horizontal icon padding for the items of the given menu,
 * so that the icon visually appears at the same distance from the starting edge of the popup
 * as from the top and the bottom edges, as well as from the label.
 * Has no effect for items that have no icon, or for items this has processed before.
 */
fun Context.increaseHorizontalPaddingOfMenuIcons(
    menu: Menu,
    paddingDp: Float = DEFAULT_HORIZONTAL_PADDING,
) = increaseHorizontalPaddingOfMenuIcons(menu.itemsRecursive(), paddingDp)

/**
 * Recursively mutates and tints the icons of the items of the given overflow or popup menu
 * with the color [R.attr.overflowAndPopupMenuIconColor] that is specified in the theme.
 * Has no effect for items that have no icon.
 */
fun Context.tintOverflowMenuIcons(
    menu: Menu,
    skipIf: ((MenuItem) -> Boolean)? = null,
) {
    val iconColor = MaterialColors.getColor(this, R.attr.overflowAndPopupMenuIconColor, 0)

    menu.overflowItemsRecursive().forEach { item ->
        if (skipIf == null || !skipIf(item)) {
            item.icon?.mutate()?.setTint(iconColor)
        }
    }
}

fun Menu.configureIconsDirection(context: Context) {
    val direction = context.resources.configuration.layoutDirection
    itemsRecursive().forEach { item ->
        item.icon?.layoutDirection = direction
    }
}
