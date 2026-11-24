// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CommonString

enum class MenuDisplayType(
    @StringRes val title: Int,
) {
    /** Shows the action as [MenuItem.SHOW_AS_ACTION_ALWAYS] */
    ALWAYS(CommonString.custom_buttons_setting_always_show),

    /** Shows the action as [MenuItem.SHOW_AS_ACTION_NEVER] */
    MENU_ONLY(CommonString.custom_buttons_setting_menu_only),

    /** Action isn't added to the menu */
    DISABLED(CommonString.disabled),
    ;

    @VisibleForTesting
    val preferenceKey get() = "ReviewerMenuDisplayType_$name"
}
