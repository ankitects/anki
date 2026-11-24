// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.snackbar.showSnackbar
import kotlin.math.round

/** Presents the end of a review session in the activity that owns its result. */
object StudyCompletionUi {
    fun openCongratsPage(activity: FragmentActivity) {
        activity.startActivity(CongratsPage.getIntent(activity))
    }

    fun displayFromShortcut(activity: FragmentActivity) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
        } else {
            activity.launchCatchingTask {
                val state = withCol { currentDeckStudyState() }
                showThemedToast(activity, finishedMessage(activity, state), false)
            }
        }
    }

    fun onReviewsCompleted(
        activity: FragmentActivity,
        state: DeckStudyState,
    ) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
            return
        }
        when (state.availability) {
            StudyAvailability.NoCardsAvailable -> activity.showSnackbar(finishedMessage(activity, state))
            StudyAvailability.CardsAvailable -> activity.showSnackbar(CommonString.studyoptions_no_cards_due)
        }
    }

    private fun displayNewCongratsScreen(context: Context) = context.sharedPrefs().getBoolean("new_congrats_screen", false)

    private fun finishedMessage(
        context: Context,
        state: DeckStudyState,
    ): String {
        val seconds = state.nextLearningIn?.inWholeSeconds ?: return context.getString(CommonString.studyoptions_congrats_finished)
        val (unit, amount) =
            if (seconds < TIME_MINUTE) {
                "seconds" to seconds.toDouble()
            } else if (seconds < TIME_HOUR) {
                "minutes" to seconds / TIME_MINUTE
            } else {
                "hours" to seconds / TIME_HOUR
            }
        val nextLearnDue = TR.schedulingNextLearnDue(unit, round(amount).toInt())
        return context.getString(CommonString.studyoptions_congrats_next_due_in, nextLearnDue)
    }
}
