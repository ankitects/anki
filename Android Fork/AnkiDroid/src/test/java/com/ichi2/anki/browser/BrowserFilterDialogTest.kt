// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.browser.search.requireChildFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class BrowserFilterDialogTest(
    private val filter: Filter,
    private val inSearch: Boolean,
) : RobolectricTest() {
    @Test
    fun `rapid taps open only one dialog`() =
        withFilterChip { chip ->
            assertTrue(chip.performClick())
            assertTrue(chip.performClick())
            advanceRobolectricLooper()

            assertEquals(1, filterDialogs.size)
            assertTrue(filterDialogs.single().requireDialog().isShowing)
        }

    @Test
    fun `repeated taps preserve the dialog and allow reopening after dismissal`() =
        withFilterChip { chip ->
            assertTrue(chip.performClick())
            advanceRobolectricLooper()
            val original = filterDialogs.single()

            assertTrue(chip.performClick())
            advanceRobolectricLooper()
            assertEquals(1, filterDialogs.size)
            assertSame(original, filterDialogs.single())

            original.dismiss()
            advanceRobolectricLooper()
            assertTrue(filterDialogs.isEmpty())

            assertTrue(chip.performClick())
            advanceRobolectricLooper()
            val reopened = filterDialogs.single()
            assertNotSame(original, reopened)
            assertTrue(reopened.requireDialog().isShowing)
        }

    private fun withFilterChip(block: Fragment.(Chip) -> Unit) =
        withCardBrowserFragment(useSearchView = true) {
            val owner =
                if (inSearch) {
                    assertNotNull(searchView).show()
                    advanceRobolectricLooper()
                    requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
                } else {
                    this
                }
            owner.block(owner.requireView().findViewById(filter.chipId))
        }

    private val Fragment.filterDialogs: List<DialogFragment>
        get() {
            val manager = if (filter == Filter.TAGS) requireActivity().supportFragmentManager else childFragmentManager
            return manager.fragments.filterIsInstance<DialogFragment>()
        }

    enum class Filter(
        @param:IdRes val chipId: Int,
    ) {
        DECKS(R.id.decks_chip),
        TAGS(R.id.tags_chip),
        CARD_STATE(R.id.card_state_chip),
        FLAGS(R.id.flags_chip),
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, inSearch={1}")
        fun parameters(): List<Array<Any>> = Filter.entries.flatMap { filter -> listOf(arrayOf(filter, false), arrayOf(filter, true)) }
    }
}
