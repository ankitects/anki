/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.ActionMenuView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentPreferencesReviewerMenuBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.sharedPrefs
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch

class ReviewerMenuSettingsFragment :
    Fragment(R.layout.fragment_preferences_reviewer_menu),
    OnClearViewListener<ReviewerMenuSettingsRecyclerItem>,
    ActionMenuView.OnMenuItemClickListener {
    private lateinit var repository: ReviewerMenuRepository

    private val binding by viewBinding(FragmentPreferencesReviewerMenuBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        repository = ReviewerMenuRepository(sharedPrefs())
        setupRecyclerView()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.reviewerMenuView.setOnMenuItemClickListener(this@ReviewerMenuSettingsFragment)
    }

    private fun setupRecyclerView() {
        val menuItems = repository.getActionsByMenuDisplayTypes()

        fun section(displayType: MenuDisplayType): List<ReviewerMenuSettingsRecyclerItem> =
            listOf(ReviewerMenuSettingsRecyclerItem.DisplayType(displayType)) +
                menuItems.getValue(displayType).map { ReviewerMenuSettingsRecyclerItem.Action(it) }

        val recyclerViewItems = MenuDisplayType.entries.flatMap { section(it) }.toMutableList()

        val callback = ReviewerMenuSettingsTouchHelperCallback(recyclerViewItems)
        callback.setOnClearViewListener(this)
        val itemTouchHelper = ItemTouchHelper(callback)

        val adapter =
            ReviewerMenuSettingsAdapter(recyclerViewItems).apply {
                setOnDragHandleTouchedListener { viewHolder ->
                    itemTouchHelper.startDrag(viewHolder)
                }
            }

        binding.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    override fun onClearView(items: List<ReviewerMenuSettingsRecyclerItem>) {
        fun getIndex(type: MenuDisplayType): Int =
            items.indexOfFirst {
                it is ReviewerMenuSettingsRecyclerItem.DisplayType && it.menuDisplayType == type
            }

        fun getSubList(
            fromIndex: Int,
            toIndex: Int,
        ): List<ViewerAction> =
            items.subList(fromIndex, toIndex).mapNotNull {
                (it as? ReviewerMenuSettingsRecyclerItem.Action)?.viewerAction
            }

        val menuOnlyItemsIndex = getIndex(MenuDisplayType.MENU_ONLY)
        val disabledItemsIndex = getIndex(MenuDisplayType.DISABLED)

        val alwaysShowActions = getSubList(1, menuOnlyItemsIndex)
        val menuOnlyActions = getSubList(menuOnlyItemsIndex, disabledItemsIndex)
        val disabledActions = getSubList(disabledItemsIndex, items.lastIndex)

        repository.setDisplayTypeActions(
            alwaysShowActions = alwaysShowActions,
            menuOnlyActions = menuOnlyActions,
            disabledActions = disabledActions,
        )

        lifecycleScope.launch {
            val menu = binding.reviewerMenuView
            menu.clear()
            menu.addActions(alwaysShowActions, menuOnlyActions)
            menu.setFlagTitles()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val action = ViewerAction.fromId(item.itemId)
        if (action.isSubMenu()) return false

        item.title?.let { showSnackbar(it, Snackbar.LENGTH_SHORT) }
        return true
    }
}
