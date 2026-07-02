// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.ItemGradeNowBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.undoAndShowSnackbar
import com.ichi2.anki.utils.ext.setCompoundDrawablesRelativeWithIntrinsicBoundsKt
import com.ichi2.anki.withProgress
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Allows a user to grade a card without the use of the study screen
 *
 * For example, a forgotten card can be marked as 'again' before it's due
 *
 * Discussion: https://github.com/ankitects/anki/pull/3840
 *
 * @see net.ankiweb.rsdroid.Backend.gradeNow
 */
// TODO: handle rotation, via a DialogFragment with IdsFile handling or Fragment Result API
@NeedsTest("Suspended card handling")
object GradeNowDialog {
    fun showDialog(
        context: AppCompatActivity,
        cardIds: List<CardId>,
    ) {
        if (!cardIds.any()) {
            Timber.w("no selected cards")
            return
        }

        Timber.i("Opening 'Grade Now'")

        val adapter = GradeNowListAdapter(context, Grade.entries)

        MaterialAlertDialogBuilder(context).show {
            title(text = with(context) { TR.sentenceCase.gradeNow })
            negativeButton(R.string.dialog_cancel)
            setAdapter(adapter) { dialog, which ->
                val selectedGrade = adapter.getItem(which)!!
                Timber.i("selected '%s'", selectedGrade.name)
                // dismiss the dialog before the operation completes to stop duplicate clicks
                context.gradeNow(cardIds, selectedGrade)
                dialog.dismiss()
            }
        }
    }

    private fun AppCompatActivity.gradeNow(
        ids: List<CardId>,
        grade: Grade,
    ) = launchCatchingTask {
        Timber.d("Grading %d cards as %s", ids.size, grade.name)
        withProgress {
            undoableOp { this.backend.gradeNow(ids, grade.rating) }
        }
        showSnackbar(TR.schedulingGradedCardsDone(ids.size)) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
        }
    }
}

private class GradeNowListAdapter(
    context: Context,
    grades: List<Grade>,
) : ArrayAdapter<Grade>(context, R.layout.item_grade_now, grades) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val binding =
            if (convertView != null) {
                ItemGradeNowBinding.bind(convertView)
            } else {
                ItemGradeNowBinding.inflate(LayoutInflater.from(context), parent, false)
            }

        val grade = getItem(position)!!
        binding.gradeTextView.apply {
            text = grade.getLabel()
            setCompoundDrawablesRelativeWithIntrinsicBoundsKt(start = grade.iconRes)
        }
        return binding.root
    }
}

private enum class Grade(
    val rating: Rating,
    @DrawableRes val iconRes: Int,
    val getLabel: () -> String,
) {
    Again(Rating.AGAIN, R.drawable.ic_ease_again, { TR.studyingAgain() }),
    Hard(Rating.HARD, R.drawable.ic_ease_hard, { TR.studyingHard() }),
    Good(Rating.GOOD, R.drawable.ic_ease_good, { TR.studyingGood() }),
    Easy(Rating.EASY, R.drawable.ic_ease_easy, { TR.studyingEasy() }),
}
