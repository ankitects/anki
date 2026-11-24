// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.ichi2.anki.databinding.ViewStudyCountsBinding
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.utils.UiUtil

/**
 * Displays New, Learn, and Review counts.
 *
 * @see [Counts]
 */
class StudyCountsView : LinearLayout {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val binding = ViewStudyCountsBinding.inflate(LayoutInflater.from(context), this)

    fun updateCounts(counts: StudyCounts) {
        fun TextView.setCount(queue: Counts.Queue) {
            val newText =
                when (queue) {
                    Counts.Queue.NEW -> counts.new
                    Counts.Queue.LRN -> counts.learn
                    Counts.Queue.REV -> counts.review
                }
            text =
                if (counts.activeQueue == queue) {
                    UiUtil.underline(newText)
                } else {
                    newText
                }
        }

        binding.newCount.setCount(Counts.Queue.NEW)
        binding.learnCount.setCount(Counts.Queue.LRN)
        binding.reviewCount.setCount(Counts.Queue.REV)
    }
}
