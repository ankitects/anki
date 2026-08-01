// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.Group
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.backend.stripHTMLScriptAndStyleTags
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.ui.CollectionMediaImageGetter
import org.intellij.lang.annotations.Language
import timber.log.Timber

/**
 * Displays an overview of a deck (title, counts, description) and allows studying or modification
 * of the deck (Unbury, Deck Options, Custom Study)
 *
 * Filtered decks may be emptied or rebuilt
 *
 * On a tablet, this is the primary screen to study a deck and appears inside [DeckPicker]
 * On a phone, this is hosted inside [StudyOptionsActivity], opened via the the [DeckPicker] counts
 */
class StudyOptionsFragment :
    Fragment(),
    ChangeManager.Subscriber,
    MenuProvider {
    @VisibleForTesting
    internal val viewModel: StudyOptionsViewModel by viewModels()

    private lateinit var deckInfoLayout: Group
    private lateinit var buttonStart: Button
    private lateinit var textDeckName: TextView
    private lateinit var textDeckDescription: TextView
    private lateinit var buryInfoLabel: TextView
    private lateinit var newCountText: TextView
    private lateinit var newBuryText: TextView
    private lateinit var learningCountText: TextView
    private lateinit var learningBuryText: TextView
    private lateinit var reviewCountText: TextView
    private lateinit var reviewBuryText: TextView
    private lateinit var totalNewCardsCount: TextView
    private lateinit var totalCardsCount: TextView

    private var fragmented = false

    private val buttonClickListener =
        View.OnClickListener { v: View ->
            if (v.id == R.id.studyoptions_start) {
                Timber.i("StudyOptionsFragment:: start study button pressed")
                val state = viewModel.state
                if (state !is StudyOptionsState.Congrats) {
                    parentFragmentManager.setStudyOptionsStudyResult()
                } else {
                    showCustomStudyContextMenu()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        Timber.i("onCreateView()")
        val studyOptionsView = inflater.inflate(R.layout.fragment_study_options, container, false)
        fragmented = requireActivity().javaClass != StudyOptionsActivity::class.java
        initAllContentViews(studyOptionsView)
        ChangeManager.subscribe(this)
        return studyOptionsView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        if (!fragmented) {
            requireAnkiActivity().setToolbarText(title = "")
        }
        viewModel.flowOfState.launchCollectionInLifecycleScope(::rebuildUi)
        refreshInterface()
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menuInflater.inflate(R.menu.study_options_fragment, menu)
        menu.findItem(R.id.action_rebuild)?.title = TR.actionsRebuild()
        menu.findItem(R.id.action_custom_study)?.title = TR.sentenceCase.customStudy
        menu.findItem(R.id.action_unbury)?.title = TR.studyingUnbury()
    }

    override fun onResume() {
        super.onResume()
        refreshInterface()
    }

    private fun closeStudyOptions(result: Int) {
        val a: Activity? = activity
        if (!fragmented && a != null) {
            a.setResult(result)
            a.finish()
        } else if (a == null) {
            // getActivity() can return null if reference to fragment lingers after parent activity has been closed,
            // which is particularly relevant when using AsyncTasks.
            Timber.e("closeStudyOptions() failed due to getActivity() returning null")
        }
    }

    private fun initAllContentViews(studyOptionsView: View) {
        studyOptionsView.findViewById<View>(R.id.studyoptions_gradient).visibility =
            if (fragmented) View.VISIBLE else View.GONE
        deckInfoLayout = studyOptionsView.findViewById(R.id.group_counts)
        textDeckName = studyOptionsView.findViewById(R.id.studyoptions_deck_name)
        textDeckDescription = studyOptionsView.findViewById(R.id.studyoptions_deck_description)
        // make links clickable
        textDeckDescription.movementMethod = LinkMovementMethod.getInstance()
        buryInfoLabel =
            studyOptionsView.findViewById<TextView>(R.id.studyoptions_bury_counts_label).apply {
                // TODO see if we could further improve the display and discoverability of buried cards here
                text = TR.studyingCountsDiffer()
            }
        // Code common to both fragmented and non-fragmented view
        newCountText = studyOptionsView.findViewById(R.id.studyoptions_new_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_new_count_label).text = TR.actionsNew()
        newBuryText = studyOptionsView.findViewById(R.id.studyoptions_new_bury)
        learningCountText = studyOptionsView.findViewById(R.id.studyoptions_learning_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_learning_count_label).text = TR.schedulingLearning()
        learningBuryText = studyOptionsView.findViewById(R.id.studyoptions_learning_bury)
        reviewCountText = studyOptionsView.findViewById(R.id.studyoptions_review_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_review_count_label).text = TR.studyingToReview()
        reviewBuryText = studyOptionsView.findViewById(R.id.studyoptions_review_bury)
        buttonStart =
            studyOptionsView.findViewById<Button>(R.id.studyoptions_start).apply {
                setOnClickListener(buttonClickListener)
            }
        totalNewCardsCount = studyOptionsView.findViewById(R.id.studyoptions_total_new_count)
        totalCardsCount = studyOptionsView.findViewById(R.id.studyoptions_total_count)
    }

    private fun showCustomStudyContextMenu() {
        val dialog = CustomStudyDialog.createInstance(deckId = viewModel.selectedDeckId)
        requireActivity().showDialogFragment(dialog)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_deck_or_study_options -> {
                Timber.i("StudyOptionsFragment:: Deck or study options button pressed")
                val deckId = viewModel.selectedDeckId
                if (viewModel.isFilteredDeck) {
                    val i = FilteredDeckOptionsFragment.getIntent(requireActivity(), did = deckId)
                    Timber.i("Opening filtered deck options")
                    onDeckOptionsActivityResult.launch(i)
                } else {
                    val i =
                        com.ichi2.anki.pages.DeckOptions
                            .getIntent(requireContext(), deckId)
                    Timber.i("Opening deck options for activity result")
                    onDeckOptionsActivityResult.launch(i)
                }
                return true
            }
            R.id.action_custom_study -> {
                Timber.i("StudyOptionsFragment:: custom study button pressed")
                showCustomStudyContextMenu()
                return true
            }
            R.id.action_schedule_reminders -> {
                Timber.i("StudyOptionsFragment:: schedule reminders button pressed")
                parentFragmentManager.setStudyOptionsAddEditReminderResult(viewModel.selectedDeckId)
                return true
            }
            R.id.action_unbury -> {
                Timber.i("StudyOptionsFragment:: unbury button pressed")
                viewModel.unbury()
                item.isVisible = false
                return true
            }
            R.id.action_rebuild -> {
                Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed")
                launchCatchingTask {
                    withProgress(R.string.rebuild_filtered_deck) {
                        viewModel.rebuildCram()
                    }
                }
                return true
            }
            R.id.action_empty -> {
                Timber.i("StudyOptionsFragment:: empty cram deck button pressed")
                launchCatchingTask {
                    withProgress(R.string.empty_filtered_deck) {
                        viewModel.emptyCram()
                    }
                }
                return true
            }
            else -> return false
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        Timber.i("configureToolbarInternal()")
        val state = viewModel.state
        if (state is StudyOptionsState.Loading) return

        if (viewModel.isFilteredDeck) {
            menu.findItem(R.id.action_rebuild)?.isVisible = true
            menu.findItem(R.id.action_empty)?.isVisible = true
            menu.findItem(R.id.action_custom_study)?.isVisible = false
            menu.findItem(R.id.action_deck_or_study_options)?.setTitle(R.string.menu__study_options)
        } else {
            menu.findItem(R.id.action_rebuild)?.isVisible = false
            menu.findItem(R.id.action_empty)?.isVisible = false
            menu.findItem(R.id.action_custom_study)?.isVisible = true
            menu.findItem(R.id.action_deck_or_study_options)?.title = TR.sentenceCase.deckOptions
        }
        if (state is StudyOptionsState.Congrats) {
            menu.findItem(R.id.action_custom_study)?.isVisible = false
        }
        menu.findItem(R.id.action_schedule_reminders)?.isVisible = Prefs.newReviewRemindersEnabled
        menu.findItem(R.id.action_unbury)?.isVisible = viewModel.haveBuried
    }

    private var onDeckOptionsActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Timber.i("StudyOptionsFragment::mOnDeckOptionsActivityResult")

            if (!isAdded) {
                Timber.d("Fragment not added to the activity")
                CrashReportService.sendExceptionReport("Fragment is not added to activity", "StudyOptionsFragment")
                return@registerForActivityResult
            }

            Timber.d(
                "Handling onActivityResult for StudyOptionsFragment (deckOptions/filteredDeckOptions, resultCode = %d)",
                result.resultCode,
            )
            activity?.invalidateMenu()
            if (result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                closeStudyOptions(result.resultCode)
                return@registerForActivityResult
            }
        }

    fun refreshInterface() {
        Timber.d("Refreshing StudyOptionsFragment")
        viewModel.refreshData()
    }

    private fun rebuildUi(state: StudyOptionsState) {
        if (state is StudyOptionsState.Loading) return
        view?.findViewById<View?>(R.id.progress_bar)?.visibility = View.GONE
        // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
        if (activity == null) {
            Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh")
            return
        }
        // #5506 If we have no view, short circuit all UI logic
        val studyOptionsView = view ?: return

        initAllContentViews(studyOptionsView)

        updateDeckNameView(state)

        when (state) {
            is StudyOptionsState.Empty -> {
                deckInfoLayout.visibility = View.VISIBLE
                buttonStart.visibility = View.GONE
            }
            is StudyOptionsState.Congrats -> {
                if (!state.isDynamic) {
                    deckInfoLayout.visibility = View.GONE
                    buttonStart.visibility = View.VISIBLE
                    buttonStart.text = TR.sentenceCase.customStudy
                } else {
                    buttonStart.visibility = View.GONE
                }
            }
            is StudyOptionsState.StudyOptions -> {
                deckInfoLayout.visibility = View.VISIBLE
                buttonStart.visibility = View.VISIBLE
                buttonStart.setText(R.string.studyoptions_start)
            }
            is StudyOptionsState.Loading -> return
        }

        val isDynamic =
            when (state) {
                is StudyOptionsState.StudyOptions -> state.isDynamic
                is StudyOptionsState.Congrats -> state.isDynamic
                else -> false
            }

        val description =
            when (state) {
                is StudyOptionsState.StudyOptions -> state.deckDescription
                else -> null
            }

        @Language("HTML")
        val desc: String =
            if (isDynamic) {
                resources.getString(R.string.dyn_deck_desc)
            } else {
                description ?: ""
            }
        if (desc.isNotEmpty()) {
            val imageGetter =
                viewModel.mediaDir?.let { mediaDir ->
                    CollectionMediaImageGetter(
                        requireContext(),
                        textDeckDescription,
                        mediaDir,
                        viewLifecycleOwner.lifecycleScope,
                    )
                }
            textDeckDescription.text = formatDescription(desc, imageGetter)
            textDeckDescription.visibility = View.VISIBLE
        } else {
            textDeckDescription.visibility = View.GONE
        }

        val data = state.dataOrNull() ?: return

        newCountText.text = data.newCardsToday.toString()
        learningCountText.text = data.lrnCardsToday.toString()
        reviewCountText.text = data.revCardsToday.toString()

        // set bury numbers
        buryInfoLabel.isVisible = data.hasBuriedCards

        fun TextView.updateBuryText(count: Int) {
            this.isVisible = count > 0
            this.text =
                when {
                    count > 0 ->
                        requireContext().resources.getQuantityString(
                            R.plurals.studyoptions_buried_count,
                            count,
                            count,
                        )
                    // #18094 - potential race condition: view may be visible with a count of 0
                    else -> ""
                }
        }
        newBuryText.updateBuryText(data.buriedNew)
        learningBuryText.updateBuryText(data.buriedLearning)
        reviewBuryText.updateBuryText(data.buriedReview)
        totalNewCardsCount.text = data.totalNewCards.toString()
        totalCardsCount.text = data.numberOfCardsInDeck.toString()
        activity?.invalidateMenu()
    }

    /**
     * Sets [textDeckName] from [state]. No-op when [state] does not carry a deck name
     * (i.e. [StudyOptionsState.Loading]) — the rest of [rebuildUi] is unaffected.
     */
    private fun updateDeckNameView(state: StudyOptionsState) {
        val fullName = state.deckNameOrNull() ?: return
        val name = Decks.path(fullName)
        val nameBuilder = StringBuilder()
        if (name.isNotEmpty()) {
            nameBuilder.append(name[0])
        }
        if (name.size > 1) {
            nameBuilder.append("\n").append(name[1])
        }
        if (name.size > 3) {
            nameBuilder.append("...")
        }
        if (name.size > 2) {
            nameBuilder.append("\n").append(name[name.size - 1])
        }
        textDeckName.text = nameBuilder.toString()
    }

    companion object {
        /**
         * Identifier for a fragment result request to study(open the reviewer). Activities using
         * this fragment need to handle this request and initialize the study screen as they see fit.
         */
        private const val REQUEST_STUDY_OPTIONS_STUDY = "request_study_option_study"

        /**
         * Identifier for a fragment result request to open the review reminders screen for the selected deck.
         * Activities using this fragment need to handle this request and open the review reminders screen as they see fit.
         */
        private const val REQUEST_STUDY_OPTIONS_REVIEW_REMINDERS = "request_study_option_review_reminders"

        /**
         * Bundle key for the deck ID whose study options are being displayed, used when the review reminders screen is being opened.
         */
        private const val RESULT_REVIEW_REMINDERS_DECK_ID = "result_review_reminders_deck_id"

        @VisibleForTesting
        fun formatDescription(
            @Language("HTML") desc: String,
            imageGetter: Html.ImageGetter? = null,
        ): Spanned {
            // #5715: In deck description, ignore what is in style and script tag
            // Since we don't currently execute the JS/CSS, it's not worth displaying.
            val withStrippedTags = stripHTMLScriptAndStyleTags(desc)
            // #5188 - compat.fromHtml converts newlines into spaces.
            val withoutWindowsLineEndings = withStrippedTags.replace("\r\n", "<br/>")
            val withoutLinuxLineEndings = withoutWindowsLineEndings.replace("\n", "<br/>")

            return withoutLinuxLineEndings.parseAsHtml(
                flags = HtmlCompat.FROM_HTML_MODE_LEGACY,
                imageGetter = imageGetter,
            )
        }

        fun FragmentActivity.registerStudyOptionsStudyHandler(action: () -> Unit) {
            setFragmentResultListener(REQUEST_STUDY_OPTIONS_STUDY) { _, _ ->
                Timber.i("Received fragment result from study options fragment to start studying")
                action()
            }
        }

        fun FragmentActivity.registerStudyOptionsAddEditReminderHandler(action: (did: DeckId) -> Unit) {
            setFragmentResultListener(REQUEST_STUDY_OPTIONS_REVIEW_REMINDERS) { _, bundle ->
                Timber.i("Received fragment result from study options fragment for adding / editing review reminders")
                val did = bundle.getLong(RESULT_REVIEW_REMINDERS_DECK_ID)
                action(did)
            }
        }

        private fun FragmentManager.setStudyOptionsStudyResult() {
            setFragmentResult(REQUEST_STUDY_OPTIONS_STUDY, Bundle())
        }

        private fun FragmentManager.setStudyOptionsAddEditReminderResult(did: DeckId) {
            setFragmentResult(
                REQUEST_STUDY_OPTIONS_REVIEW_REMINDERS,
                Bundle().apply { putLong(RESULT_REVIEW_REMINDERS_DECK_ID, did) },
            )
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (activity != null) {
            refreshInterface()
        }
    }
}
