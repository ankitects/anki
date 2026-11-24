// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import com.ichi2.anki.reviewer.ReviewerUi

class MockReviewerUi : ReviewerUi {
    override var isDisplayingAnswer = false
        private set

    companion object {
        fun displayingAnswer(): ReviewerUi {
            val mockReviewerUi = MockReviewerUi()
            mockReviewerUi.isDisplayingAnswer = true
            return mockReviewerUi
        }
    }
}
