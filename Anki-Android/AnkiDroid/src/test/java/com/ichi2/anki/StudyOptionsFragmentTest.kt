// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.appcompat.view.menu.MenuBuilder
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.launchFragmentInContainer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Fragment-level coverage for the closed-collection scenarios on tablets, where
 * [StudyOptionsFragment] is embedded inside [DeckPicker] and the collection can be
 * unusable during sync-on-startup.
 */
@RunWith(AndroidJUnit4::class)
class StudyOptionsFragmentTest : RobolectricTest() {
    @Test
    fun `fragment reaches RESUMED with a closed collection`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).onFragment { fragment ->
                assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
            }
        }
    }

    @Test
    fun `onResume after STARTED with a closed collection does not crash`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.STARTED,
            ).run {
                moveToState(Lifecycle.State.RESUMED)
                onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `recreate with a closed collection survives lifecycle restart`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).run {
                recreate()
                onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash with a closed collection from start`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).onFragment { fragment ->
                val menu = MenuBuilder(fragment.requireContext())
                fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                fragment.onPrepareMenu(menu)
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash when collection closes after population`() {
        col

        val scenario =
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            )
        scenario.onFragment { fragment ->

            runBlocking { fragment.viewModel.refreshData().join() }
            assertIs<StudyOptionsState.Empty>(fragment.viewModel.state)
        }
        withNullCollection {
            scenario.onFragment { fragment ->
                @Suppress("RestrictedApi")
                val menu = MenuBuilder(fragment.requireContext())
                fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                fragment.onPrepareMenu(menu)
            }
        }
    }
}
