// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import com.ichi2.anki.utils.getAccessibleJavaField

fun MenuItemImpl.removeSubMenu() {
    val subMenuField = getAccessibleJavaField<MenuItemImpl>("mSubMenu")
    subMenuField?.set(this, null)
}

val MenuItemImpl.menu: MenuBuilder get() {
    val menuField = getAccessibleJavaField<MenuItemImpl>("mMenu")
    return menuField?.get(this) as MenuBuilder
}
