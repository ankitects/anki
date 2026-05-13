/*
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
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.backend.stripHTMLScriptAndStyleTags
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleReminders
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.ui.CollectionMediaImageGetter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var currentContentView = CONTENT_STUDY_OPTIONS
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

    private var retryMenuRefreshJob: Job? = null

    private var fragmented = false

    private val buttonClickListener =
        View.OnClickListener { v: View ->
            if (v.id == R.id.studyoptions_start) {
                Timber.i("StudyOptionsFragment:: start study button pressed")
                if (currentContentView != CONTENT_CONGRATS) {
                    parentFragmentManager.setFragmentResult(
                        REQUEST_STUDY_OPTIONS_STUDY,
                        bundleOf(),
                    )
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
        refreshInterface()
        ChangeManager.subscribe(this)
        return studyOptionsView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
        val dialog = CustomStudyDialog.createInstance(deckId = col!!.decks.selected())
        requireActivity().showDialogFragment(dialog)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_deck_or_study_options -> {
                Timber.i("StudyOptionsFragment:: Deck or study options button pressed")
                if (col!!.decks.isFiltered(col!!.decks.selected())) {
                    val i = FilteredDeckOptionsFragment.getIntent(requireActivity(), did = col!!.decks.current().id)
                    Timber.i("Opening filtered deck options")
                    onDeckOptionsActivityResult.launch(i)
                } else {
                    val i =
                        com.ichi2.anki.pages.DeckOptions
                            .getIntent(requireContext(), col!!.decks.current().id)
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
                val intent =
                    ScheduleReminders.getIntent(
                        requireContext(),
                        ReviewReminderScope.DeckSpecific(col!!.decks.current().id),
                    )
                startActivity(intent)
                return true
            }
            R.id.action_unbury -> {
                Timber.i("StudyOptionsFragment:: unbury button pressed")
                launchCatchingTask {
                    undoableOp<OpChanges> { sched.unburyDeck(decks.getCurrentId()) }
                }
                item.isVisible = false
                return true
            }
            R.id.action_rebuild -> {
                Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed")
                launchCatchingTask { rebuildCram() }
                return true
            }
            R.id.action_empty -> {
                Timber.i("StudyOptionsFragment:: empty cram deck button pressed")
                launchCatchingTask { emptyCram() }
                return true
            }
            else -> return false
        }
    }

    private suspend fun rebuildCram() {
        val result =
            requireActivity().withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
                undoableOp {
                    Timber.d("doInBackground - RebuildCram")
                    sched.rebuildFilteredDeck(decks.selected())
                }
                withCol { fetchStudyOptionsData() }
            }
        rebuildUi(result)
    }

    @VisibleForTesting
    suspend fun emptyCram() {
        val result =
            requireActivity().withProgress(resources.getString(R.string.empty_filtered_deck)) {
                undoableOp {
                    Timber.d("doInBackgroundEmptyCram")
                    sched.emptyFilteredDeck(decks.selected())
                }
                withCol { fetchStudyOptionsData() }
            }
        rebuildUi(result)
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        Timber.i("configureToolbarInternal()")
        try {
            // Switch on or off rebuild/empty/custom study depending on whether or not filtered deck
            if (col != null && col!!.decks.isFiltered(col!!.decks.selected())) {
                menu.findItem(R.id.action_rebuild)?.isVisible = true
                menu.findItem(R.id.action_empty)?.isVisible = true
                menu.findItem(R.id.action_custom_study)?.isVisible = false
                menu.findItem(R.id.action_deck_or_study_options)?.setTitle(R.string.menu__study_options)
            } else {
                menu.findItem(R.id.action_rebuild)?.isVisible = false
                menu.findItem(R.id.action_empty)?.isVisible = false
                menu.findItem(R.id.action_custom_study)?.isVisible = true
                menu.findItem(R.id.action_deck_or_study_options)?.setTitle(R.string.menu__deck_options)
            }
            // Don't show custom study icon if congrats shown
            if (currentContentView == CONTENT_CONGRATS) {
                menu.findItem(R.id.action_custom_study)?.isVisible = false
            }
            // Use new review reminders system if enabled
            menu.findItem(R.id.action_schedule_reminders)?.isVisible = Prefs.newReviewRemindersEnabled
            // Switch on or off unbury depending on if there are cards to unbury
            menu.findItem(R.id.action_unbury)?.isVisible = col != null && col!!.sched.haveBuried()
        } catch (e: IllegalStateException) {
            if (!CollectionManager.isOpenUnsafe()) {
                // This will allow a maximum of one invalidate menu attempt in order to workaround
                // database closes caused by sync on startup where this might be running then have
                // the collection close
                Timber.i(e, "Database closed while working. Probably auto-sync. Will re-try after sleep.")
                if (retryMenuRefreshJob != null) {
                    return // we already are doing a refresh, so abort to avoid entering an endless loop
                }
                retryMenuRefreshJob =
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1000)
                        retryMenuRefreshJob = null
                        activity?.invalidateMenu()
                    }
            }
        }
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
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR || result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                closeStudyOptions(result.resultCode)
                return@registerForActivityResult
            }
        }

    private var updateValuesFromDeckJob: Job? = null

    fun refreshInterface() {
        Timber.d("Refreshing StudyOptionsFragment")
        updateValuesFromDeckJob?.cancel()
        // Load the deck counts for the deck from Collection asynchronously
        updateValuesFromDeckJob =
            launchCatchingTask {
                if (CollectionManager.isOpenUnsafe()) {
                    val result = withCol { fetchStudyOptionsData() }
                    rebuildUi(result)
                }
            }
    }

    class DeckStudyData(
        /**
         * The number of new card to see today in a deck, including subdecks.
         */
        val newCardsToday: Int,
        /**
         * The number of (repetition of) card in learning to see today in a deck, including subdecks. The exact way cards with multiple steps are counted depends on the scheduler
         */
        val lrnCardsToday: Int,
        /**
         * The number of review card to see today in a deck, including subdecks.
         */
        val revCardsToday: Int,
        val buriedNew: Int,
        val buriedLearning: Int,
        val buriedReview: Int,
        val totalNewCards: Int,
        /**
         * Number of cards in this decks and its subdecks.
         */
        val numberOfCardsInDeck: Int,
    )

    private val col: Collection?
        get() {
            try {
                return CollectionManager.getColUnsafe()
            } catch (_: Exception) {
                // This may happen if the backend is locked or similar.
            }
            return null
        }

    override fun onPause() {
        super.onPause()
        updateValuesFromDeckJob?.cancel()
    }

    private fun rebuildUi(result: DeckStudyData) {
        view?.findViewById<View?>(R.id.progress_bar)?.visibility = View.GONE
        // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
        if (activity == null) {
            Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh")
            return
        }
        // #5506 If we have no view, short circuit all UI logic
        val studyOptionsView = view ?: return

        val col =
            col
                ?: throw NullPointerException("StudyOptionsFragment:: Collection is null while rebuilding Ui")

        // Reinitialize controls in case changed to filtered deck
        initAllContentViews(studyOptionsView)
        // Set the deck name
        val deck = col.decks.current()
        // Main deck name
        val fullName = deck.getString("name")
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

        // Switch between the empty view, the ordinary view, and the "congratulations" view
        val isDynamic = deck.isFiltered
        if (result.numberOfCardsInDeck == 0 && !isDynamic) {
            currentContentView = CONTENT_EMPTY
            deckInfoLayout.visibility = View.VISIBLE
            buttonStart.visibility = View.GONE
        } else if (result.newCardsToday + result.lrnCardsToday + result.revCardsToday == 0) {
            currentContentView = CONTENT_CONGRATS
            if (!isDynamic) {
                deckInfoLayout.visibility = View.GONE
                buttonStart.visibility = View.VISIBLE
                buttonStart.text = TR.sentenceCase.customStudy
            } else {
                buttonStart.visibility = View.GONE
            }
        } else {
            currentContentView = CONTENT_STUDY_OPTIONS
            deckInfoLayout.visibility = View.VISIBLE
            buttonStart.visibility = View.VISIBLE
            buttonStart.setText(R.string.studyoptions_start)
        }

        // Set deck description
        @Language("HTML")
        val desc: String =
            if (isDynamic) {
                resources.getString(R.string.dyn_deck_desc)
            } else {
                val deck = col.decks.current()
                if (deck.descriptionAsMarkdown) {
                    @Suppress("DEPRECATION") // renderMarkdown is fine here.
                    col.renderMarkdown(deck.description, sanitize = true)
                } else {
                    deck.description
                }
            }
        if (desc.isNotEmpty()) {
            val mediaDir = col.media.dir
            val imageGetter =
                CollectionMediaImageGetter(
                    requireContext(),
                    textDeckDescription,
                    mediaDir,
                    viewLifecycleOwner.lifecycleScope,
                )

            textDeckDescription.text = formatDescription(desc, imageGetter)
            textDeckDescription.visibility = View.VISIBLE
        } else {
            textDeckDescription.visibility = View.GONE
        }

        // Set new/learn/review card counts
        newCountText.text = result.newCardsToday.toString()
        learningCountText.text = result.lrnCardsToday.toString()
        reviewCountText.text = result.revCardsToday.toString()

        // set bury numbers
        buryInfoLabel.isVisible = result.buriedNew > 0 || result.buriedLearning > 0 || result.buriedReview > 0

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
        newBuryText.updateBuryText(result.buriedNew)
        learningBuryText.updateBuryText(result.buriedLearning)
        reviewBuryText.updateBuryText(result.buriedReview)
        totalNewCardsCount.text = result.totalNewCards.toString()
        totalCardsCount.text = result.numberOfCardsInDeck.toString()
        // Rebuild the options menu
        activity?.invalidateMenu()
    }

    /**
     * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
     */
    private fun Collection.fetchStudyOptionsData(): DeckStudyData {
        val deckId = decks.current().id
        val counts = sched.counts()
        var buriedNew = 0
        var buriedLearning = 0
        var buriedReview = 0
        val tree = sched.deckDueTree(deckId)
        if (tree != null) {
            buriedNew = tree.newCount - counts.new
            buriedLearning = tree.learnCount - counts.lrn
            buriedReview = tree.reviewCount - counts.rev
        }
        return DeckStudyData(
            newCardsToday = counts.new,
            lrnCardsToday = counts.lrn,
            revCardsToday = counts.rev,
            buriedNew = buriedNew,
            buriedLearning = buriedLearning,
            buriedReview = buriedReview,
            totalNewCards = sched.totalNewForCurrentDeck(),
            numberOfCardsInDeck = decks.cardCount(deckId, includeSubdecks = true),
        )
    }

    companion object {
        /**
         * Identifier for a fragment result request to study(open the reviewer). Activities using
         * this fragment need to handle this request and initialize the study screen as they see fit.
         */
        const val REQUEST_STUDY_OPTIONS_STUDY = "request_study_option_study"

        /**
         * Constants for selecting which content view to display
         */
        private const val CONTENT_STUDY_OPTIONS = 0
        private const val CONTENT_CONGRATS = 1
        private const val CONTENT_EMPTY = 2

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
