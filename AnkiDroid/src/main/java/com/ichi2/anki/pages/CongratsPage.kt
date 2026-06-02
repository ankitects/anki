/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import anki.scheduler.UnburyDeckRequest
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.listItemsAndMessage
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import kotlin.math.round

class CongratsPage :
    PageFragment(),
    ChangeManager.Subscriber {
    override val pagePath: String = "congrats"

    private val viewModel by viewModels<CongratsViewModel>()

    init {
        ChangeManager.subscribe(this)
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // typically due to 'day rollover'
        if (changes.studyQueues) {
            Timber.i("refreshing: study queues updated")
            webViewLayout.post { webViewLayout.reload() }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }.launchIn(lifecycleScope)

        viewModel.unburyState
            .onEach { state ->
                when (state) {
                    UnburyState.OpenStudy -> openStudyOptionsAndFinish()
                    UnburyState.SelectMode -> {
                        val unburyOptions =
                            mutableListOf(
                                TR.studyingManuallyBuriedCards(),
                                TR.studyingBuriedSiblings(),
                                TR.studyingAllBuriedCards(),
                            )
                        AlertDialog.Builder(requireContext()).show {
                            negativeButton(R.string.dialog_cancel)
                            listItemsAndMessage(
                                TR.studyingWhatWouldYouLikeToUnbury(),
                                unburyOptions,
                            ) { _, position ->
                                val mode =
                                    when (position) {
                                        0 -> UnburyDeckRequest.Mode.USER_ONLY
                                        1 -> UnburyDeckRequest.Mode.SCHED_ONLY
                                        2 -> UnburyDeckRequest.Mode.ALL
                                        else -> error("Unhandled unbury option: ${unburyOptions[position]}")
                                    }
                                viewModel.onUnburyModeSelected(mode)
                            }
                        }
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.deckOptionsDestination
            .flowWithLifecycle(lifecycle)
            .onEach { destination ->
                val intent = destination.toIntent(requireContext())
                startActivity(intent, null)
            }.launchIn(lifecycleScope)

        with(view.findViewById<MaterialToolbar>(R.id.toolbar)) {
            inflateMenu(R.menu.congrats)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_open_deck_options) {
                    viewModel.onDeckOptions()
                }
                true
            }
        }

        setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                -> openStudyOptionsAndFinish()
            }
        }
    }

    override val bridgeCommands =
        mapOf(
            "unbury" to { viewModel.onUnbury() },
            "customStudy" to { onStudyMore() },
        )

    private fun openStudyOptionsAndFinish() {
        val intent = Intent(requireContext(), StudyOptionsActivity::class.java)
        startActivity(intent, null)
        requireActivity().finish()
    }

    private fun onStudyMore() {
        launchCatchingTask {
            val customStudy = CustomStudyDialog.createInstance(deckId = withCol { decks.selected() })
            customStudy.show(childFragmentManager, null)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, fragmentClass = CongratsPage::class)

        private fun displayNewCongratsScreen(context: Context): Boolean = context.sharedPrefs().getBoolean("new_congrats_screen", false)

        fun display(activity: FragmentActivity) {
            if (displayNewCongratsScreen(activity)) {
                activity.startActivity(getIntent(activity))
            } else {
                activity.launchCatchingTask {
                    val message = getDeckFinishedMessage(activity)
                    showThemedToast(activity, message, false)
                }
            }
        }

        fun onReviewsCompleted(
            activity: FragmentActivity,
            cardsInDeck: Boolean,
        ) {
            if (displayNewCongratsScreen(activity)) {
                activity.startActivity(getIntent(activity))
                return
            }

            // Show a message when reviewing has finished
            if (cardsInDeck) {
                activity.launchCatchingTask {
                    val message = getDeckFinishedMessage(activity)
                    activity.showSnackbar(message)
                }
            } else {
                activity.showSnackbar(R.string.studyoptions_no_cards_due)
            }
        }

        // based in https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/routes/congrats/lib.ts#L8C17-L8C34
        private suspend fun getDeckFinishedMessage(activity: FragmentActivity): String {
            val info = withCol { sched.congratulationsInfo() }
            val secsUntilNextLearn = info.secsUntilNextLearn
            if (secsUntilNextLearn >= SECONDS_PER_DAY) {
                return activity.getString(R.string.studyoptions_congrats_finished)
            }
            // https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/lib/tslib/time.ts#L22
            val (unit, amount) =
                if (secsUntilNextLearn < TIME_MINUTE) {
                    "seconds" to secsUntilNextLearn.toDouble()
                } else if (secsUntilNextLearn < TIME_HOUR) {
                    "minutes" to secsUntilNextLearn / TIME_MINUTE
                } else {
                    "hours" to secsUntilNextLearn / TIME_HOUR
                }

            val nextLearnDue = TR.schedulingNextLearnDue(unit, round(amount).toInt())
            return activity.getString(R.string.studyoptions_congrats_next_due_in, nextLearnDue)
        }

        fun DeckPicker.onDeckCompleted() {
            Timber.i("Opening CongratsPage")
            startActivity(getIntent(this))
        }
    }
}

class CongratsViewModel :
    ViewModel(),
    OnErrorListener {
    override val onError = MutableSharedFlow<String>()
    val unburyState = MutableSharedFlow<UnburyState>()
    val deckOptionsDestination = MutableSharedFlow<DeckOptionsDestination>()

    fun onUnbury() {
        launchCatchingIO {
            // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/overview.py#L154
            val congratsInfo = withCol { sched.congratulationsInfo() }
            if (congratsInfo.haveSchedBuried && congratsInfo.haveUserBuried) {
                unburyState.emit(UnburyState.SelectMode)
                return@launchCatchingIO
            }
            unburyAndStudy(UnburyDeckRequest.Mode.ALL)
        }
    }

    fun onUnburyModeSelected(mode: UnburyDeckRequest.Mode) {
        launchCatchingIO {
            unburyAndStudy(mode)
        }
    }

    private suspend fun unburyAndStudy(mode: UnburyDeckRequest.Mode) {
        undoableOp {
            sched.unburyDeck(decks.getCurrentId(), mode)
        }
        unburyState.emit(UnburyState.OpenStudy)
    }

    fun onDeckOptions() {
        launchCatchingIO {
            val deckId = withCol { decks.getCurrentId() }
            val isFiltered = withCol { decks.isFiltered(deckId) }
            deckOptionsDestination.emit(DeckOptionsDestination(deckId, isFiltered))
        }
    }
}

sealed class UnburyState {
    data object OpenStudy : UnburyState()

    data object SelectMode : UnburyState()
}
