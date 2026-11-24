// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.help

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ichi2.anki.common.analytics.LinkAction
import kotlinx.parcelize.Parcelize

private typealias HelpItemId = Long

/**
 * A class containing all the information required to show an entry in the application help/support
 * menus.
 */
@Parcelize
data class HelpItem(
    /**
     * User visible text of the menu item.
     */
    @StringRes val titleResId: Int,
    /**
     * The icon associated with this menu item.
     */
    @DrawableRes val iconResId: Int,
    val analyticsId: LinkAction,
    /**
     * Unique(per menu) identifier for this menu item.
     */
    val id: HelpItemId,
    /**
     * If not null this property indicates that this menu item belongs to the submenu of the
     * referenced menu item.
     */
    val parentId: HelpItemId? = null,
    /**
     * Reference to the action that needs to be done when the user selects this menu item. Can be
     * null, in which case this is a top level item(with or without children).
     */
    val action: Action? = null,
) : Parcelable {
    /**
     * Possible actions that could be done if the user selects one on the help/support menu items.
     *
     * @see [HelpDialog]
     */
    sealed class Action : Parcelable {
        /**
         * Action to open an url. Used to show the url for feedback or the manual.
         *
         * @see [com.ichi2.anki.AnkiDroidApp]
         */
        @Parcelize
        data class OpenUrl(
            val url: String,
        ) : Action()

        @Parcelize
        data class OpenUrlResource(
            @StringRes val urlResourceId: Int,
        ) : Action()

        @Parcelize
        data object SendReport : Action()

        /**
         * Action to allow the user to rate the application. Note that this item's availability will
         * depend on the system having an app to do the action.
         */
        @Parcelize
        data object Rate : Action()
    }
}
