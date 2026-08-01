/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences.reviewer

import android.content.SharedPreferences
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.testutils.common.assertThrows
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContentEquals

class ReviewerMenuRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ReviewerMenuRepository

    @Before
    fun setUp() {
        prefs = SPMockBuilder().createSharedPreferences()
        repository = ReviewerMenuRepository(prefs)
    }

    // commas can't be part of an enum object name, so they are safe as separators.
    // This test serves as a safeguard against changes in the separator, which would need
    // a preference upgrade
    @Test
    fun `setPreferenceValue stores actions as comma-separated string`() {
        val actions = listOf(ViewerAction.UNDO, ViewerAction.REDO)
        repository.setDisplayTypeActions(alwaysShowActions = actions, emptyList(), emptyList())

        val expectedValue = "UNDO,REDO"
        assertEquals(expectedValue, prefs.getString(MenuDisplayType.ALWAYS.preferenceKey, null))
    }

    @Test
    fun `getActionsByMenuDisplayType returns correctly categorized items`() {
        // assuming that UNDO is the only action with ALWAYS as default, put it in another list
        repository.setDisplayTypeActions(
            alwaysShowActions = emptyList(),
            menuOnlyActions = listOf(ViewerAction.UNDO),
            disabledActions = emptyList(),
        )
        val result = repository.getActionsByMenuDisplayTypes(MenuDisplayType.ALWAYS).getValue(MenuDisplayType.ALWAYS)
        assertThat(result, empty())
    }

    @Test
    fun `getActionsByMenuDisplayType returns not configured items that have a default display type`() {
        // assuming that USER_ACTION_1 and USER_ACTION_2 don't have MENU_ONLY as default
        val userActions1and2 = listOf(ViewerAction.USER_ACTION_1, ViewerAction.USER_ACTION_2)
        repository.setDisplayTypeActions(alwaysShowActions = emptyList(), menuOnlyActions = userActions1and2, disabledActions = emptyList())
        val result = repository.getActionsByMenuDisplayTypes(MenuDisplayType.MENU_ONLY).getValue(MenuDisplayType.MENU_ONLY)
        val expected = userActions1and2 + ViewerAction.entries.filter { it.defaultDisplayType == MenuDisplayType.MENU_ONLY }
        assertContentEquals(expected, result)
    }

    @Test
    fun `getActionsByMenuDisplayType returns only the selected display types`() {
        val one = repository.getActionsByMenuDisplayTypes(MenuDisplayType.DISABLED, MenuDisplayType.MENU_ONLY)
        one.getValue(MenuDisplayType.DISABLED)
        one.getValue(MenuDisplayType.MENU_ONLY)
        assertThrows<NoSuchElementException> { one.getValue(MenuDisplayType.ALWAYS) }

        val two = repository.getActionsByMenuDisplayTypes(MenuDisplayType.ALWAYS, MenuDisplayType.DISABLED, MenuDisplayType.MENU_ONLY)
        two.getValue(MenuDisplayType.DISABLED)
        two.getValue(MenuDisplayType.ALWAYS)
        two.getValue(MenuDisplayType.MENU_ONLY)

        val three = repository.getActionsByMenuDisplayTypes(MenuDisplayType.ALWAYS)
        three.getValue(MenuDisplayType.ALWAYS)
        assertThrows<NoSuchElementException> { three.getValue(MenuDisplayType.DISABLED) }
        assertThrows<NoSuchElementException> { three.getValue(MenuDisplayType.MENU_ONLY) }
    }
}
