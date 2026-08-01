/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.text.Spanned
import android.text.style.UnderlineSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.databinding.ViewStudyCountsBinding
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.themes.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyCountsViewTest {
    private lateinit var view: StudyCountsView
    private lateinit var binding: ViewStudyCountsBinding

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Themes.setTheme(context)
        view = StudyCountsView(context)
        binding = ViewStudyCountsBinding.bind(view)
    }

    @Test
    fun `updateCounts updates text values correctly`() {
        val counts =
            StudyCounts(
                newCount = 10,
                learnCount = 5,
                reviewCount = 20,
                activeQueue = Counts.Queue.NEW,
            )

        view.updateCounts(counts)

        assertEquals("10", binding.newCount.text.toString())
        assertEquals("5", binding.learnCount.text.toString())
        assertEquals("20", binding.reviewCount.text.toString())
    }

    @Test
    fun `NEW underlines only new count`() {
        val counts =
            StudyCounts(
                newCount = 10,
                learnCount = 5,
                reviewCount = 20,
                activeQueue = Counts.Queue.NEW,
            )

        view.updateCounts(counts)

        assertTrue("New count should be underlined", binding.newCount.text.hasUnderlineSpan())
        assertFalse("Learn count should not be underlined", binding.learnCount.text.hasUnderlineSpan())
        assertFalse("Review count should not be underlined", binding.reviewCount.text.hasUnderlineSpan())
    }

    @Test
    fun `LEARN underlines only learn count`() {
        val counts =
            StudyCounts(
                newCount = 10,
                learnCount = 5,
                reviewCount = 20,
                activeQueue = Counts.Queue.LRN,
            )

        view.updateCounts(counts)

        assertFalse("New count should not be underlined", binding.newCount.text.hasUnderlineSpan())
        assertTrue("Learn count should be underlined", binding.learnCount.text.hasUnderlineSpan())
        assertFalse("Review count should not be underlined", binding.reviewCount.text.hasUnderlineSpan())
    }

    @Test
    fun `REVIEW underlines only review count`() {
        val counts =
            StudyCounts(
                newCount = 10,
                learnCount = 5,
                reviewCount = 20,
                activeQueue = Counts.Queue.REV,
            )

        view.updateCounts(counts)

        assertFalse("New count should not be underlined", binding.newCount.text.hasUnderlineSpan())
        assertFalse("Learn count should not be underlined", binding.learnCount.text.hasUnderlineSpan())
        assertTrue("Review count should be underlined", binding.reviewCount.text.hasUnderlineSpan())
    }

    private fun CharSequence.hasUnderlineSpan(): Boolean {
        if (this !is Spanned) return false
        val spans = getSpans(0, length, UnderlineSpan::class.java)
        return spans.isNotEmpty()
    }
}
