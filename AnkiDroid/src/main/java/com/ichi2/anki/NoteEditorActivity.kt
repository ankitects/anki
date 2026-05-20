/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.databinding.ActivityNoteEditorBinding
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.noteeditor.NoteEditorFragmentDelegate
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.ext.doOnTabSelected
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * To find the actual note Editor, @see [NoteEditorFragment]
 * This activity contains the NoteEditorFragment, and, on x-large screens, the previewer fragment.
 * It also ensures that changes in the note are transmitted to the previewer
 */

// TODO: Move intent handling to [NoteEditorActivity] from [NoteEditorFragment]
class NoteEditorActivity :
    AnkiActivity(),
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    ShortcutGroupProvider,
    NoteEditorFragmentDelegate {
    override val baseSnackbarBuilder: SnackbarBuilder = { }

    lateinit var noteEditorFragment: NoteEditorFragment

    private val mainToolbar: androidx.appcompat.widget.Toolbar
        get() = findViewById(R.id.toolbar)

    /**
     * Reference to the previewer container that exists only on larger screens.
     * Non-null if and only if the layout is x-large and includes the previewer frame
     *
     * Unlike lateinit variables, this will remain null throughout the activity
     * lifecycle on smaller screens that don't include the previewer frame.
     *
     * Fragmentation is determined by this view's visibility after inflation.
     */
    private var previewerFrame: FragmentContainerView? = null

    /**
     * Job for managing delayed previewer refresh operations.
     * Automatically cancelled when the lifecycle scope is destroyed, preventing memory leaks.
     */
    private var refreshPreviewerJob: Job? = null

    val fragmented: Boolean
        get() = previewerFrame?.isVisible == true

    private lateinit var binding: ActivityNoteEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewerFrame = binding.previewerFrame
        Timber.i("Note Editor is in %s mode", if (fragmented) "split" else "single-pane")

        // TODO: specify how non-null but invalid extras are handled
        val fragmentArgs = intent.extras ?: NoteEditorFragment.addNoteArgs()

        val existingFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)

        if (existingFragment == null) {
            supportFragmentManager.commit {
                replace(R.id.note_editor_fragment_frame, NoteEditorFragment.newInstance(fragmentArgs), FRAGMENT_TAG)
                setReorderingAllowed(true)
                /*
                 * Initializes the noteEditorFragment reference only after the transaction is committed.
                 * This ensures the fragment is fully created and available in the activity before
                 * any code attempts to interact with it, preventing potential null reference issues.
                 */
                runOnCommit {
                    noteEditorFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as NoteEditorFragment
                    noteEditorFragment.setDelegate(this@NoteEditorActivity)
                }
            }
        } else {
            noteEditorFragment = existingFragment as NoteEditorFragment
            noteEditorFragment.setDelegate(this)
        }

        enableToolbar()

        // R.id.home is handled in setNavigationOnClickListener
        // Set a listener for back button clicks in the toolbar
        mainToolbar.setNavigationOnClickListener {
            Timber.i("NoteEditor:: Back button on the menu was pressed")
            onBackPressedDispatcher.onBackPressed()
        }

        if (fragmented) {
            // Defer previewer loading to avoid blocking onCreate
            binding.previewerFrame!!.post {
                loadNoteEditorPreviewer(true)
            }
            val parentLayout = binding.noteEditorXlView!!
            val divider = binding.noteEditorResizingDivider!!
            val noteEditorPane = binding.noteEditorFragmentFrame
            val previewerPane = binding.previewerFrameLayout!!
            ResizablePaneManager(
                parentLayout = parentLayout,
                divider = divider,
                leftPane = noteEditorPane,
                rightPane = previewerPane,
                sharedPrefs = Prefs.getUiConfig(this),
                leftPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
                rightPaneWeightKey = PREF_PREVIEWER_PANE_WEIGHT,
            )
        }

        startLoadingCollection()
    }

    /**
     * Loads and configures the note editor previewer.
     *
     * This method orchestrates the entire preview process including:
     * - Processing the current note fields and tags
     * - Setting up the previewer fragment with the appropriate configuration
     * - Configuring the tab layout for card template navigation
     *
     * The preview will reflect the current state of the note being edited,
     * allowing users to see how their cards will appear during review.
     *
     * BUG: Fragment replacement loses user state
     * - Scroll position is reset when user has scrolled through long card content
     *
     * Ideally, we should:
     * Preserve scroll position when possible
     *
     * State preservation behavior:
     * - Regular templates: Content-only updates preserve tab selection and front/back state
     * - Cloze notes: Fragment recreation with preserved tab selection to handle dynamic cloze changes
     */
    fun loadNoteEditorPreviewer(forceReplace: Boolean) {
        if (!fragmented) {
            return
        }

        // Check if noteEditorFragment is initialized before proceeding
        if (!::noteEditorFragment.isInitialized) {
            Timber.w("loadNoteEditorPreviewer called before noteEditorFragment was initialized")
            return
        }

        // Check if editorNote is available before proceeding
        val note =
            noteEditorFragment.editorNote ?: run {
                Timber.w("loadNoteEditorPreviewer called before editorNote was available")
                return
            }

        launchCatchingTask {
            try {
                val fields = noteEditorFragment.prepareNoteFields()
                val tags = noteEditorFragment.selectedTags ?: mutableListOf()

                fun updatePreviewerFragment(ord: Int) {
                    val previewerFragment = createPreviewerFragment(fields, tags, ord)
                    supportFragmentManager.commit {
                        replace(R.id.previewer_frame, previewerFragment)
                        runOnCommit {
                            configurePreviewerTabs(previewerFragment)
                        }
                    }
                }

                val existingPreviewer = supportFragmentManager.findFragmentById(R.id.previewer_frame)

                when {
                    forceReplace -> {
                        updatePreviewerFragment(ord = noteEditorFragment.determineCardOrdinal(fields))
                    }
                    existingPreviewer is TemplatePreviewerFragment -> {
                        if (note.notetype.isCloze) {
                            // For cloze notes, force recreation to handle dynamic cloze changes
                            // but preserve the user's selected tab
                            updatePreviewerFragment(ord = existingPreviewer.getSafeClozeOrd())
                        } else {
                            // For regular templates, just update content
                            existingPreviewer.updateContent(fields, tags)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load note editor previewer")
            }
        }
    }

    /**
     * Creates and configures the template previewer fragment.
     *
     * @param fields The processed note fields
     * @param tags The selected tags for the note
     * @param ord The ordinal (position) of the card template to display
     * @return The configured previewer fragment
     */
    private fun createPreviewerFragment(
        fields: List<String>,
        tags: List<String>,
        ord: CardOrdinal,
    ): TemplatePreviewerFragment {
        val args =
            TemplatePreviewerArguments(
                notetypeFile =
                    NotetypeFile(
                        this@NoteEditorActivity,
                        noteEditorFragment.editorNote!!.notetype,
                    ),
                fields = fields,
                tags = tags,
                id = noteEditorFragment.editorNote!!.id,
                ord = ord,
                fillEmpty = false,
            )

        val previewerFragment = TemplatePreviewerFragment.newInstance(args)
        return previewerFragment
    }

    /**
     * Configures the tab layout for the previewer with appropriate tabs for each card template.
     * Delegates the tab setup responsibility to the TemplatePreviewerFragment
     *
     * @param previewerFragment The previewer fragment that will manage the tabs
     */
    private fun configurePreviewerTabs(previewerFragment: TemplatePreviewerFragment) {
        // Post to ensure the fragment is attached before accessing its viewModel
        binding.previewerFrame?.post {
            if (!previewerFragment.isAdded) return@post
            val previewerTabLayout = binding.previewerTabLayout!!
            launchCatchingTask {
                previewerFragment.setupTabs(previewerTabLayout)
            }
        }
    }

    /**
     * Sets up the previewer tabs with appropriate titles and selection handling.
     *
     * @param tabLayout The tab layout to configure
     */
    private suspend fun TemplatePreviewerFragment.setupTabs(tabLayout: TabLayout) {
        tabLayout.removeAllTabs()

        val cardsWithEmptyFronts = viewModel.cardsWithEmptyFronts?.await()
        val templateNames = viewModel.getTemplateNames()

        for ((index, templateName) in templateNames.withIndex()) {
            val tabTitle =
                if (cardsWithEmptyFronts?.get(index) == true) {
                    getString(R.string.card_previewer_empty_front_indicator, templateName)
                } else {
                    templateName
                }
            val newTab = tabLayout.newTab().setText(tabTitle)
            tabLayout.addTab(newTab)
        }

        // Ensure tab index is within valid range
        val currentTabIndex = viewModel.getCurrentTabIndex()
        val safeTabIndex = currentTabIndex.coerceIn(0, maxOf(0, templateNames.size - 1))

        if (templateNames.isNotEmpty()) {
            tabLayout.selectTab(tabLayout.getTabAt(safeTabIndex))
            // Update ViewModel if index was adjusted
            if (safeTabIndex != currentTabIndex) {
                viewModel.onTabSelected(safeTabIndex)
            }
        }

        // Remove any existing listeners to avoid duplicates
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.doOnTabSelected { tab ->
            Timber.v("Selected tab %d", tab.position)
            viewModel.onTabSelected(tab.position)
        }
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        noteEditorFragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override val shortcuts: ShortcutGroup
        get() = noteEditorFragment.shortcuts

    override fun onResume() {
        super.onResume()
        // Refresh the previewer when activity resumes, if needed
        if (fragmented) {
            loadNoteEditorPreviewer(false)
        }
    }

    //region NoteEditorFragmentDelegate Protocol Methods

    override fun onNoteEditorReady() {
        // Load the if fragmented, else does nothing
        if (!fragmented) return

        loadNoteEditorPreviewer(false)
    }

    override fun onNoteTextChanged() {
        if (!fragmented) return

        refreshPreviewerJob?.cancel()
        refreshPreviewerJob =
            launchCatchingTask {
                delay(REFRESH_NOTE_EDITOR_PREVIEW_DELAY)
                loadNoteEditorPreviewer(false)
            }
    }

    override fun onNoteSaved() {
        if (!fragmented) return

        loadNoteEditorPreviewer(true)
    }

    override fun onNoteTypeChanged() {
        if (!fragmented) return

        loadNoteEditorPreviewer(true)
    }
    //endregion

    companion object {
        const val FRAGMENT_TAG = "NoteEditorFragmentTag"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"
        private const val PREF_PREVIEWER_PANE_WEIGHT = "previewerPaneWeight"

        private val REFRESH_NOTE_EDITOR_PREVIEW_DELAY = 100.milliseconds
    }
}
