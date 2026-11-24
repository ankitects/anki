// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import android.content.SharedPreferences
import androidx.core.content.edit

class ReviewerMenuRepository(
    private val preferences: SharedPreferences,
) {
    /**
     * @return a map of the [selected] display types and its actions.
     */
    fun getActionsByMenuDisplayTypes(
        vararg selected: MenuDisplayType = MenuDisplayType.entries.toTypedArray(),
    ): Map<MenuDisplayType, List<ViewerAction>> {
        val menuActions = ViewerAction.entries.filter { it.defaultDisplayType != null }
        val actionsNameMap = menuActions.associateBy { it.name }

        val allConfiguredActions = mutableSetOf<ViewerAction>()
        val actionsMap = LinkedHashMap<MenuDisplayType, MutableList<ViewerAction>>(selected.size)
        for (displayType in MenuDisplayType.entries) {
            val prefValue = preferences.getString(displayType.preferenceKey, null)
            val configuredActions =
                if (prefValue.isNullOrEmpty()) {
                    mutableListOf()
                } else {
                    val actionNames = prefValue.split(SEPARATOR)
                    actionNames.mapNotNullTo(mutableListOf()) { name ->
                        actionsNameMap[name]
                    }
                }
            if (displayType in selected) {
                actionsMap[displayType] = configuredActions
            }
            allConfiguredActions.addAll(configuredActions)
        }

        // Add any menu action that hasn't been configured to its default display type
        for (action in menuActions) {
            if (action !in allConfiguredActions && action.defaultDisplayType in actionsMap) {
                actionsMap[action.defaultDisplayType]?.add(action)
            }
        }
        return actionsMap
    }

    fun setDisplayTypeActions(
        alwaysShowActions: Iterable<ViewerAction>,
        menuOnlyActions: Iterable<ViewerAction>,
        disabledActions: Iterable<ViewerAction>,
    ) {
        fun Iterable<ViewerAction>.toPreferenceString() = this.joinToString(SEPARATOR) { it.name }
        preferences.edit {
            putString(MenuDisplayType.ALWAYS.preferenceKey, alwaysShowActions.toPreferenceString())
            putString(MenuDisplayType.MENU_ONLY.preferenceKey, menuOnlyActions.toPreferenceString())
            putString(MenuDisplayType.DISABLED.preferenceKey, disabledActions.toPreferenceString())
        }
    }

    companion object {
        private const val SEPARATOR = ","
    }
}
