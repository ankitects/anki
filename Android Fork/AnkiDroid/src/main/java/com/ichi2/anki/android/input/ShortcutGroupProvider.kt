// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.android.input

interface ShortcutGroupProvider {
    /**
     * Lists of shortcuts for this fragment, and the IdRes of the name of this shortcut group.
     */
    val shortcuts: ShortcutGroup?
}
