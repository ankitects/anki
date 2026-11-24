// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import anki.notetypes.StockNotetype
import anki.notetypes.StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_UNKNOWN_VALUE
import anki.notetypes.notetypeId
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.android.animationDisabled
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.common.utils.ext.contentEquals
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.ActivityCardTemplateEditorBinding
import com.ichi2.anki.databinding.FragmentCardTemplateEditorTemplateBinding
import com.ichi2.anki.databinding.IncludeCardTemplateEditorMainBinding
import com.ichi2.anki.databinding.IncludeCardTemplateEditorTopBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldMetadata
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.CardTemplates
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.Notetypes.Companion.NOT_FOUND_NOTE_TYPE
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.libanki.getStockNotetypeKinds
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.notetype.CardTypeName
import com.ichi2.anki.notetype.RenameCardTypeDialog
import com.ichi2.anki.notetype.RepositionCardTemplateDialog
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.settings.uiConfigPrefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.doOnApplyWindowInsets
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.doOnTabSelected
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.postDelayed
import com.ichi2.utils.TruncatedString
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.listItems
import com.ichi2.utils.show
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Translations
import net.ankiweb.rsdroid.exceptions.BackendTemplateException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private typealias BackendCardTemplate = com.ichi2.anki.libanki.CardTemplate

/**
 * Allows the user to view the template for the current note type
 */
@KotlinCleanup("lateinit wherever possible")
open class CardTemplateEditor : AnkiActivity(R.layout.activity_card_template_editor) {
    private val binding by viewBinding(ActivityCardTemplateEditorBinding::bind)
    private val viewModel by viewModels<CardTemplateEditorViewModel>()

    @VisibleForTesting
    val topBinding: IncludeCardTemplateEditorTopBinding
        get() = binding.templateEditorTop

    @VisibleForTesting
    internal val mainBinding: IncludeCardTemplateEditorMainBinding
        get() = binding.templateEditor

    // TODO: see if it is feasible to use mockk to cause a crash
    @VisibleForTesting
    var tempNoteType: CardTemplateNotetype? = null
    private var fieldNames: List<String>? = null
    private var noteTypeId: NoteTypeId = 0
    private var noteId: NoteId = 0

    /**
     * Stores the cursor position for each editor window (front, style, back) within each card template.
     * The outer HashMap's key is the card template's ordinal (position).
     * The inner HashMap's key is the editor window ID (e.g., R.id.front_edit).
     * The value is the cursor position within that editor window.
     */
    private var tabToCursorPositions: HashMap<CardOrdinal, HashMap<Int, Int>> = HashMap()

    // the current editor view among front/style/back
    private var tabToViewId: HashMap<Int, Int?> = HashMap()
    private var startingOrdId: CardOrdinal = 0

    /**
     * The ordinal of the current template being edited
     *
     * Valid for use in [tempNoteType]
     */
    private val ord: Int
        get() = mainBinding.cardTemplateEditorPager.currentItem

    /**
     * If true, the view is split in two. The template editor appears on the leading side and the previewer on the trailing side.
     * This occurs when the screen size is large
     */
    private var fragmented = false
    val displayDiscardChangesCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    /**
     * Triggered when a card template ('Card 1') is selected in the top tab view
     */

    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------
    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) {
            return
        }
        setupEdgeToEdge()
        // Load the args either from the intent or savedInstanceState bundle
        if (savedInstanceState == null) {
            // get note type id
            noteTypeId = intent.getLongExtra(EXTRA_NOTE_TYPE_ID, NOT_FOUND_NOTE_TYPE)
            if (noteTypeId == NOT_FOUND_NOTE_TYPE) {
                Timber.e("CardTemplateEditor :: no note type ID was provided")
                finish()
                return
            }
            // get id for currently edited note (optional)
            noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            // get id for currently edited template (optional)
            startingOrdId = intent.getIntExtra(EXTRA_START_ORD_ID, -1)
            tabToCursorPositions[0] = hashMapOf()
            tabToViewId[0] = R.id.front_edit
        } else {
            noteTypeId = savedInstanceState.getLong(STATE_NOTE_TYPE_ID)
            noteId = savedInstanceState.getLong(STATE_NOTE_ID)
            startingOrdId = savedInstanceState.getInt(STATE_STARTING_TEMPLATE_ORDINAL)
            tabToCursorPositions = savedInstanceState.getSerializableCompat<HashMap<Int, HashMap<Int, Int>>>(STATE_TAB_TO_CURSOR_POSITION)!!
            tabToViewId = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(STATE_TAB_TO_VIEW_ID)!!
            tempNoteType = CardTemplateNotetype.fromBundle(savedInstanceState)
        }

        fragmented = binding.fragmentContainer?.isVisible == true

        setNavigationBarColor(com.google.android.material.R.attr.colorSurfaceDim)

        // Disable the home icon
        enableToolbar()
        startLoadingCollection()

        if (fragmented) {
            ResizablePaneManager(
                parentLayout = requireNotNull(binding.cardTemplateEditorXlView) { "cardTemplateEditorXlView" },
                divider = requireNotNull(binding.cardTemplateEditorResizingDivider) { "cardTemplateEditorResizingDivider" },
                startPane = requireNotNull(binding.templateEditor.root) { "templateEditor.root" },
                endPane = requireNotNull(binding.fragmentContainer) { "fragmentContainer" },
                sharedPrefs = uiConfigPrefs(),
                startPaneWeightKey = PREF_TEMPLATE_EDITOR_PANE_WEIGHT,
                endPaneWeightKey = PREF_TEMPLATE_PREVIEWER_PANE_WEIGHT,
            )
        }

        // Open TemplatePreviewerFragment if in fragmented mode
        loadTemplatePreviewerFragmentIfFragmented()
        onBackPressedDispatcher.addCallback(this, displayDiscardChangesCallback)

        setupStateCollection()

        topBinding.slidingTabs.doOnTabSelected { tab ->
            Timber.i("selected card index: %s", tab.position)
            viewModel.setCurrentTemplateOrd(tab.position)
            loadTemplatePreviewerFragmentIfFragmented(tab.position)
        }

        // ViewPager2 clears focus when a page is selected, so focus can't stay on an offscreen page.
        // Move it to the selected page instead: see CardTemplateFragment.isCurrentPage
        mainBinding.cardTemplateEditorPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    templateFragment(position)?.binding?.editText?.requestFocus()
                }
            },
        )

        registerDeckSelectedHandler(action = ::onDeckSelected)
    }

    private fun setupEdgeToEdge() {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        ViewCompat.setOnApplyWindowInsetsListener(binding.templateEditorTop.root) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout())
            findViewById<LinearLayout>(
                R.id.template_editor_top,
            )?.updatePadding(left = constraints.left, top = constraints.top, right = constraints.right)
            insets
        }
        // When not fragmented, each CardTemplateFragment insets its own template: the fragments
        // are created after the first insets are dispatched, so they can't be reached from here
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout() or ime())
            if (fragmented) {
                findViewById<LinearLayout>(R.id.template_editor)?.updatePadding(left = constraints.left)
                findViewById<FragmentContainerView>(R.id.fragment_container)?.updatePadding(right = constraints.right)
            }
            insets
        }
    }

    private fun setupStateCollection() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is CardTemplateEditorState.Initializing -> {
                            val exception = state.exception
                            if (exception != null) {
                                AlertDialog.Builder(this@CardTemplateEditor).show {
                                    setTitle(CommonString.vague_error)
                                    setMessage(exception.source.localizedMessage ?: getString(CommonString.something_wrong))
                                    setPositiveButton(CommonString.dialog_ok) { _, _ -> finish() }
                                    setCancelable(false)
                                }
                            }
                        }
                        is CardTemplateEditorState.Loaded -> {
                            if (state.message != null) {
                                val messageText =
                                    when (state.message) {
                                        CardTemplateEditorState.UserMessage.CantDeleteLastTemplate ->
                                            getString(CommonString.card_template_editor_cant_delete)
                                        CardTemplateEditorState.UserMessage.CantAddTemplateToDynamic ->
                                            getString(CommonString.multimedia_editor_something_wrong)
                                        CardTemplateEditorState.UserMessage.SaveSuccess ->
                                            getString(CommonString.card_template_editor_changes_saved)
                                        CardTemplateEditorState.UserMessage.DeletionWouldOrphanNote ->
                                            getString(CommonString.orphan_note_message)
                                    }
                                showSnackbar(messageText)
                                viewModel.clearMessage()
                            }
                            if (state.error != null) {
                                showSnackbar(state.error.source.localizedMessage ?: getString(CommonString.something_wrong))
                                viewModel.clearError()
                            }
                        }
                        is CardTemplateEditorState.Finished -> {
                            finish()
                        }
                    }
                }
            }
        }
    }

    /**
     *  Loads or reloads [tempNoteType] in [R.id.fragment_container] if the view is fragmented. Do nothing otherwise.
     */
    private fun loadTemplatePreviewerFragmentIfFragmented(ord: CardOrdinal = mainBinding.cardTemplateEditorPager.currentItem) {
        if (!fragmented) {
            return
        }
        launchCatchingTask {
            val notetype = tempNoteType!!.notetype
            val notetypeFile = NotetypeFile(this@CardTemplateEditor, notetype)
            val note = withCol { currentFragment?.getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
            val args =
                TemplatePreviewerArguments(
                    notetypeFile = notetypeFile,
                    id = note.id,
                    ord = ord,
                    fields = note.fields,
                    tags = note.tags,
                    fillEmpty = true,
                )
            val fragment = TemplatePreviewerFragment.newInstance(args)
            withStarted {
                supportFragmentManager.commitNow {
                    replace(R.id.fragment_container, fragment)
                }
            }

            // Modify the "Show Answer" button height to 80dp to maintain visual consistency with the BottomNavigationView,
            // which has a default height of 80dp.
            fragment.view?.post {
                fragment.binding.showAnswer.let { button ->
                    button.layoutParams.height = 80.dp.toPx(button.context)
                    button.requestLayout()
                }

                // Adjust the top margin of the webview container to match template editor top margin
                fragment.binding.webViewContainer.let { container ->
                    val params = container.layoutParams as ViewGroup.MarginLayoutParams
                    val topMargin = resources.getDimensionPixelSize(R.dimen.reviewer_side_margin)
                    params.topMargin = topMargin
                    container.layoutParams = params
                }
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            tempNoteType?.let { putAll(it.toBundle()) }
            putLong(STATE_NOTE_TYPE_ID, noteTypeId)
            putLong(STATE_NOTE_ID, noteId)
            putInt(STATE_STARTING_TEMPLATE_ORDINAL, ord)
            putSerializable(STATE_TAB_TO_VIEW_ID, tabToViewId)
            putSerializable(STATE_TAB_TO_CURSOR_POSITION, tabToCursorPositions)
            super.onSaveInstanceState(this)
        }
    }

    override fun onActionBarBackPressed(): Boolean {
        // not the back dispatcher: its callback is disabled while the keyboard is open
        if (noteTypeHasChanged()) {
            showDiscardChangesDialog()
        } else {
            finish()
        }
        return true
    }

    /**
     * Callback used to finish initializing the activity after the collection has been correctly loaded
     * @param col Collection which has been loaded
     */
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        // without this call the editor doesn't see the latest changes to notetypes, see #16630
        @NeedsTest("Add test to check that renaming notetypes in ManageNotetypes is seen in CardTemplateEditor(#16630)")
        col.notetypes.clearCache()
        // The first time the activity loads it has a model id but no edits yet, so no edited model
        // take the passed model id load it up for editing
        if (tempNoteType == null) {
            tempNoteType = CardTemplateNotetype(col.notetypes.get(noteTypeId)!!.deepClone())
            // Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
        fieldNames = tempNoteType!!.notetype.fieldsNames
        // Set up the ViewPager with the sections adapter.
        mainBinding.cardTemplateEditorPager.adapter = TemplatePagerAdapter(this@CardTemplateEditor)

        // Keep more fragments in memory to reduce menu flickering during tab switches (issue #18555).
        // When switching between non-adjacent tabs, ViewPager2's default behavior destroys fragments,
        // causing their MenuProviders to fire and create visual flicker.
        // Capped at 7 (keeping up to 15 fragments total) to balance flicker reduction with memory usage,
        // as templates can contain large content (JS bundles, CSS frameworks, etc.).
        mainBinding.cardTemplateEditorPager.offscreenPageLimit = 7

        TabLayoutMediator(
            topBinding.slidingTabs,
            mainBinding.cardTemplateEditorPager,
        ) { tab: TabLayout.Tab, position: Int ->
            tab.text = tempNoteType!!.getTemplate(position).name
        }.apply { attach() }

        // Set activity title
        supportActionBar?.let {
            it.setTitle(CommonString.title_activity_template_editor)
            it.subtitle = tempNoteType!!.notetype.name
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for note type id %d", noteTypeId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", startingOrdId)
        if (startingOrdId != -1) {
            mainBinding.cardTemplateEditorPager.setCurrentItem(startingOrdId, animationDisabled())
        }
        updateDiscardChangesCallback()
    }

    fun noteTypeHasChanged(): Boolean {
        // insets and 'Up' can both arrive before the note type is loaded: answer them without
        // opening the collection
        val tempNoteType = tempNoteType ?: return false
        val oldNoteType = getColUnsafe.notetypes.get(noteTypeId) ?: return true
        return !tempNoteType.notetype.jsonObject.contentEquals(oldNoteType.jsonObject)
    }

    /**
     * Whether a software keyboard is open, which 'back' dismisses.
     *
     * The template tabs are hidden when the keyboard is open, so 'back' should reveal them.
     */
    private val isKeyboardOpen: Boolean
        get() =
            currentFragment
                ?.takeIf { it.view != null }
                ?.binding
                ?.bottomNavigation
                ?.isVisible == false

    /** Updates [displayDiscardChangesCallback]. Call when the edits or the keyboard change. */
    private fun updateDiscardChangesCallback() {
        // 'back' dismisses the keyboard, revealing the tabs again
        displayDiscardChangesCallback.isEnabled = !isKeyboardOpen && noteTypeHasChanged()
    }

    private fun showDiscardChangesDialog() =
        DiscardChangesDialog.showDialog(this) {
            Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
            // Clear the edited note type from any cache files, and clear it from this objects memory to discard changes
            CardTemplateNotetype.clearTempNoteTypeFiles()
            tempNoteType = null
            finish()
        }

    /** When a deck is selected via Deck Override  */
    fun onDeckSelected(deck: SelectableDeck?) {
        require(deck is SelectableDeck.Deck?)
        if (tempNoteType!!.notetype.isCloze) {
            Timber.w("Attempted to set deck for cloze note type")
            showSnackbar(getString(CommonString.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val template = tempNoteType!!.getTemplate(ord)
        val templateName = template.name

        if (deck != null && getColUnsafe.decks.isFiltered(deck.deckId)) {
            Timber.w("Attempted to set default deck of %s to dynamic deck %s", templateName, deck.name)
            showSnackbar(getString(CommonString.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val message: String =
            if (deck == null) {
                Timber.i("Removing default template from template '%s'", templateName)
                template.jsonObject.put("did", JSONObject.NULL)
                getString(CommonString.model_manager_deck_override_removed_message, templateName)
            } else {
                Timber.i("Setting template '%s' to '%s'", templateName, deck.name)
                template.jsonObject.put("did", deck.deckId)
                getString(CommonString.model_manager_deck_override_added_message, templateName, deck.name)
            }

        showSnackbar(message, Snackbar.LENGTH_SHORT)

        // Deck Override can change from "on" <-> "off"
        invalidateOptionsMenu()
        updateDiscardChangesCallback()
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val currentFragment = currentFragment ?: return super.onKeyUp(keyCode, event)
        if (!event.isCtrlPressed) {
            return super.onKeyUp(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_P -> {
                Timber.i("Ctrl+P: Perform preview from keypress")
                currentFragment.performPreview()
            }
            KeyEvent.KEYCODE_1 -> {
                Timber.i("Ctrl+1: Edit front template from keypress")
                currentFragment.binding.bottomNavigation.selectedItemId = R.id.front_edit
            }
            KeyEvent.KEYCODE_2 -> {
                Timber.i("Ctrl+2: Edit back template from keypress")
                currentFragment.binding.bottomNavigation.selectedItemId = R.id.back_edit
            }
            KeyEvent.KEYCODE_3 -> {
                Timber.i("Ctrl+3: Edit styling from keypress")
                currentFragment.binding.bottomNavigation.selectedItemId = R.id.styling_edit
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Ctrl+S: Save note from keypress")
                currentFragment.saveNoteType()
            }
            KeyEvent.KEYCODE_I -> {
                Timber.i("Ctrl+I: Insert field from keypress")
                currentFragment.showInsertFieldDialog()
            }
            KeyEvent.KEYCODE_N -> {
                Timber.i("Ctrl+N: Add card template from keypress")
                currentFragment.addCardTemplate()
            }
            KeyEvent.KEYCODE_R -> {
                Timber.i("Ctrl+R: Rename card from keypress")
                currentFragment.showRenameDialog()
            }
            KeyEvent.KEYCODE_B -> {
                Timber.i("Ctrl+B: Open browser appearance from keypress")
                currentFragment.openBrowserAppearance()
            }
            KeyEvent.KEYCODE_D -> {
                Timber.i("Ctrl+D: Delete card from keypress")
                currentFragment.deleteCardTemplate()
            }
            KeyEvent.KEYCODE_O -> {
                Timber.i("Ctrl+O: Display deck override dialog from keypress")
                currentFragment.displayDeckOverrideDialog(currentFragment.tempModel)
            }
            KeyEvent.KEYCODE_M -> {
                Timber.i("Ctrl+M: Copy markdown from keypress")
                currentFragment.copyMarkdownTemplateToClipboard()
            }
            else -> {
                return super.onKeyUp(keyCode, event)
            }
        }
        // We reach this only if we didn't reach the `else` case.
        return true
    }

    /** The page for a card, if the pager has created it */
    private fun templateFragment(position: Int): CardTemplateFragment? {
        val adapter = mainBinding.cardTemplateEditorPager.adapter ?: return null
        return supportFragmentManager.findFragmentByTag("f${adapter.getItemId(position)}") as? CardTemplateFragment
    }

    private fun repositionTemplate(
        from: CardOrdinal,
        to: CardOrdinal,
    ) {
        if (from == to) return
        val positions = (0 until tempNoteType!!.templateCount).toMutableList()
        // Save the live cursor positions before the fragments are recreated at their new positions.
        for (position in positions) {
            templateFragment(position)?.takeIf { it.view != null }?.let { fragment ->
                tabToCursorPositions[position]?.set(fragment.currentEditorViewId, fragment.binding.editText.selectionStart)
            }
        }
        positions.add(to, positions.removeAt(from))
        tabToCursorPositions = reorderTabState(tabToCursorPositions, positions)
        tabToViewId = reorderTabState(tabToViewId, positions)
        tempNoteType!!.repositionTemplate(from, to)

        val pager = mainBinding.cardTemplateEditorPager
        (pager.adapter as TemplatePagerAdapter).ordinalShift()
        pager.adapter!!.notifyDataSetChanged()
        pager.setCurrentItem(to, false)
        loadTemplatePreviewerFragmentIfFragmented(to)
        updateDiscardChangesCallback()
    }

    private fun <T> reorderTabState(
        state: HashMap<Int, T>,
        positions: List<Int>,
    ): HashMap<Int, T> =
        positions
            .mapIndexedNotNull { newPosition, oldPosition -> state[oldPosition]?.let { newPosition to it } }
            .toMap(HashMap())

    @get:VisibleForTesting
    val currentFragment: CardTemplateFragment?
        get() = templateFragment(ord)

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * A [androidx.viewpager2.adapter.FragmentStateAdapter] that returns a fragment corresponding to
     * one of the tabs.
     */
    inner class TemplatePagerAdapter(
        fragmentActivity: FragmentActivity,
    ) : FragmentStateAdapter(fragmentActivity) {
        private var baseId: Long = 0

        override fun createFragment(position: Int): Fragment {
            val editorViewId = tabToViewId[position] ?: R.id.front_edit
            return CardTemplateFragment.newInstance(position, noteId, editorViewId)
        }

        override fun getItemCount(): Int = tempNoteType?.templateCount ?: 0

        override fun getItemId(position: Int): Long = baseId + position

        override fun containsItem(id: Long): Boolean {
            @Suppress("ConvertTwoComparisonsToRangeCheck") // more readable without the range check
            return (id - baseId < itemCount) && (id - baseId >= 0)
        }

        /** Force fragments to reinitialize contents by invalidating previous set of ordinal-based ids  */
        fun ordinalShift() {
            baseId += (itemCount + 1).toLong()
        }
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+P", CommonString.card_editor_preview_card),
                    shortcut("Ctrl+1", CommonString.edit_front_template),
                    shortcut("Ctrl+2", CommonString.edit_back_template),
                    shortcut("Ctrl+3", CommonString.edit_styling),
                    shortcut("Ctrl+S", CommonString.save),
                    shortcut("Ctrl+I", CommonString.card_template_editor_insert_field),
                    shortcut("Ctrl+A", Translations::cardTemplatesAddCardType),
                    shortcut("Ctrl+R", Translations::cardTemplatesRenameCardType),
                    shortcut("Ctrl+B", CommonString.edit_browser_appearance),
                    shortcut("Ctrl+D", Translations::cardTemplatesRemoveCardType),
                    shortcut("Ctrl+O", Translations::cardTemplatesDeckOverride),
                    shortcut("Ctrl+M", CommonString.copy_the_template),
                ),
                CommonString.card_template_editor_group,
            )

    class CardTemplateFragment : Fragment(R.layout.fragment_card_template_editor_template) {
        @VisibleForTesting
        internal val binding by viewBinding(FragmentCardTemplateEditorTemplateBinding::bind)

        private val refreshFragmentHandler = Handler(Looper.getMainLooper())

        // Index of this card template fragment in ViewPager
        private val cardIndex
            get() = requireArguments().getInt(ARG_CARD_INDEX)

        /** Whether this is the page shown by the pager. */
        private val isCurrentPage: Boolean
            get() = templateEditor.mainBinding.cardTemplateEditorPager.currentItem == cardIndex

        private val templateName
            get() = tempModel.notetype.templates[cardIndex].name

        val insertFieldRequestKey
            get() = "request_field_insert_$cardIndex"

        var currentEditorViewId = 0

        private val currentEditTab: EditTab?
            get() =
                when (currentEditorViewId) {
                    R.id.front_edit -> EditTab.FRONT
                    R.id.back_edit -> EditTab.BACK
                    R.id.styling_edit -> EditTab.STYLING
                    else -> null
                }

        private lateinit var templateEditor: CardTemplateEditor
        lateinit var tempModel: CardTemplateNotetype

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            // Storing a reference to the templateEditor allows us to use member variables
            templateEditor = activity as CardTemplateEditor
            tempModel = templateEditor.tempNoteType!!
            // Load template
            val template: BackendCardTemplate =
                try {
                    tempModel.getTemplate(cardIndex)
                } catch (e: JSONException) {
                    Timber.d(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                    return
                }
            // initializing the hash map which stores the cursor position for each editor window
            if (templateEditor.tabToCursorPositions[cardIndex] == null) {
                templateEditor.tabToCursorPositions[cardIndex] = hashMapOf()
            }

            binding.editText.customInsertionActionModeCallback = ActionModeCallback()

            // If in fragmented mode, wrap the edit area in a MaterialCardView
            if (templateEditor.fragmented) {
                // Set the background color of the main layout to match the previewer
                binding.mainLayout.setBackgroundColor(
                    getColorFromAttr(
                        requireContext(),
                        com.google.android.material.R.attr.colorSurfaceDim,
                    ),
                )

                // Create a MaterialCardView to wrap the editText
                val cardView =
                    MaterialCardView(requireContext()).apply {
                        layoutParams =
                            LinearLayout
                                .LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    0,
                                    1f,
                                ).apply {
                                    val sideMargin = resources.getDimensionPixelSize(R.dimen.reviewer_side_margin)
                                    setMargins(sideMargin, 0, sideMargin, 0)
                                }
                    }

                // Remove the ScrollView from the main layout and add it to the cardView
                binding.mainLayout.removeViewInLayout(binding.scrollView)

                cardView.addView(
                    binding.scrollView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                binding.mainLayout.addView(cardView, 0)
            }

            binding.bottomNavigation.menu
                .findItem(R.id.front_edit)
                .title = TR.notetypesFrontField()
            binding.bottomNavigation.menu
                .findItem(R.id.styling_edit)
                .title = TR.cardTemplatesTemplateStyling()

            binding.bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
                val currentSelectedId = item.itemId
                templateEditor.tabToViewId[cardIndex] = currentSelectedId
                Timber.i("selected editor view: %s", item.title)
                when (currentSelectedId) {
                    R.id.styling_edit ->
                        setCurrentEditorView(
                            currentSelectedId,
                            cardIndex,
                            tempModel.css,
                        )

                    R.id.back_edit ->
                        setCurrentEditorView(
                            currentSelectedId,
                            cardIndex,
                            template.afmt,
                        )

                    else -> setCurrentEditorView(currentSelectedId, cardIndex, template.qfmt)
                }
                // contents of menu have changed and menu should be redrawn
                templateEditor.invalidateOptionsMenu()
                true
            }
            // set saved or default view
            binding.bottomNavigation.selectedItemId =
                templateEditor.tabToViewId[cardIndex] ?: requireArguments().getInt(ARG_EDITOR_VIEW_ID)

            // Set text change listeners
            val templateEditorWatcher: TextWatcher =
                object : TextWatcher {
                    /**
                     * Declare a nullable variable refreshFragmentRunnable of type Runnable.
                     * This will hold a reference to the Runnable that refreshes the previewer fragment.
                     * It is used to manage delayed fragment updates and can be null if no updates in card.
                     */
                    private var refreshFragmentRunnable: Runnable? = null

                    override fun afterTextChanged(arg0: Editable) {
                        refreshFragmentRunnable?.let { refreshFragmentHandler.removeCallbacks(it) }

                        when (currentEditTab) {
                            EditTab.STYLING -> tempModel.css = binding.editText.text.toString()
                            EditTab.BACK -> template.afmt = binding.editText.text.toString()
                            EditTab.FRONT -> template.qfmt = binding.editText.text.toString()
                            else -> template.qfmt = binding.editText.text.toString()
                        }
                        templateEditor.tempNoteType!!.updateTemplate(cardIndex, template)
                        val updateRunnable =
                            Runnable {
                                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
                            }
                        refreshFragmentRunnable = updateRunnable
                        refreshFragmentHandler.postDelayed(updateRunnable, REFRESH_PREVIEW_DELAY)
                        templateEditor.updateDiscardChangesCallback()
                    }

                    override fun beforeTextChanged(
                        arg0: CharSequence,
                        arg1: Int,
                        arg2: Int,
                        arg3: Int,
                    ) {
                        // do nothing
                    }

                    override fun onTextChanged(
                        arg0: CharSequence,
                        arg1: Int,
                        arg2: Int,
                        arg3: Int,
                    ) {
                        // do nothing
                    }
                }
            binding.editText.addTextChangedListener(templateEditorWatcher)

            binding.root.doOnApplyWindowInsets { view, insets, initial ->
                // Hide the template tabs to make room for a full software keyboard. A physical
                // keyboard can report a visible IME with only a navigation strip (or zero height).
                val softwareKeyboardVisible = insets.getInsets(ime()).bottom > binding.bottomNavigation.minimumHeight
                binding.bottomNavigation.isVisible = !softwareKeyboardVisible
                val bottomInset = insets.getInsets(systemBars() or displayCutout() or ime()).bottom
                view.updatePadding(
                    bottom = if (softwareKeyboardVisible) bottomInset else 0,
                )
                // only the selected page; a page reapplies insets on attach, so none is missed
                if (isCurrentPage) templateEditor.updateDiscardChangesCallback()
                // When fragmented, the activity insets the editor pane instead.
                if (!templateEditor.fragmented) {
                    val bars = insets.getInsets(systemBars() or displayCutout())
                    binding.scrollView.updatePadding(left = bars.left, right = bars.right)
                }
            }
            // the view is added to the pager after the insets were dispatched, so request them again
            binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }

            /*
             * We focus on the editText to indicate it's editable, but we don't automatically
             * show the keyboard. This is intentional - the keyboard should only appear
             * when the user taps on the edit field, not every time the fragment loads.
             */
            if (isCurrentPage) {
                binding.editText.post {
                    binding.editText.requestFocus()
                }
            }

            parentFragmentManager.setFragmentResultListener(insertFieldRequestKey, viewLifecycleOwner) { key, bundle ->
                // this is guaranteed to be non null, as we put a non null value on the other side
                insertField(bundle.getString(InsertFieldDialog.RESULT_INSERTED_FIELD)!!)
            }
            setupMenu()
        }

        /*
         * Custom ActionMode.Callback implementation for adding new field action
         * button in the text selection menu.
         */
        private inner class ActionModeCallback : ActionMode.Callback {
            private val insertFieldId = 1

            override fun onCreateActionMode(
                mode: ActionMode,
                menu: Menu,
            ): Boolean = true

            override fun onPrepareActionMode(
                mode: ActionMode,
                menu: Menu,
            ): Boolean {
                if (menu.findItem(insertFieldId) != null) {
                    return false
                }
                val initialSize = menu.size

                if (currentEditorViewId != R.id.styling_edit) {
                    // 10644: Do not pass in a R.string as the final parameter as MIUI on Android 12 crashes.
                    menu.add(Menu.FIRST, insertFieldId, 0, getString(CommonString.card_template_editor_insert_field))
                }

                return initialSize != menu.size
            }

            override fun onActionItemClicked(
                mode: ActionMode,
                item: MenuItem,
            ): Boolean {
                val itemId = item.itemId
                return if (itemId == insertFieldId) {
                    showInsertFieldDialog()
                    mode.finish()
                    true
                } else {
                    false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Left empty on purpose
            }
        }

        @NeedsTest(
            "the kotlin migration made this method crash due to a recursive call when the dialog would return its data",
        )
        fun showInsertFieldDialog() {
            launchCatchingTask {
                val fieldNames = templateEditor.fieldNames ?: return@launchCatchingTask

                val side =
                    when (currentEditTab) {
                        EditTab.FRONT -> SingleCardSide.FRONT
                        EditTab.BACK -> SingleCardSide.BACK
                        else -> SingleCardSide.FRONT
                    }

                val noteId = if (templateEditor.noteId > 0) templateEditor.noteId else null

                // use the ord of the selected template, not the ord of the currently edited card

                val ord = tempModel.getOriginalOrdinal(templateEditor.ord)

                val dialog =
                    InsertFieldDialog.newInstance(
                        fieldItems = fieldNames,
                        metadata =
                            InsertFieldMetadata.query(
                                side = side,
                                noteId = noteId,
                                ord = ord,
                                cardTemplateName = templateName,
                                noteTypeName = tempModel.notetype.name,
                            ),
                        requestKey = insertFieldRequestKey,
                    )
                templateEditor.showDialogFragment(dialog)
            }
        }

        @NeedsTest("Cancellation")
        @NeedsTest("Prefill is correct")
        @NeedsTest("Does not work for Cloze/Occlusion")
        @NeedsTest("UI is updated on success")
        fun showRenameDialog() {
            if (noteTypeCreatesDynamicNumberOfNotes()) {
                Timber.w("attempted to rename a dynamic note type")
                return
            }
            val ordinal = templateEditor.ord
            val template = templateEditor.tempNoteType!!.getTemplate(ordinal)

            // obtain the current names (potentially unsaved)
            val existingNames =
                templateEditor.tempNoteType!!
                    .notetype.templates
                    .map { CardTypeName.fromString(it.name) }

            RenameCardTypeDialog.showInstance(
                requireContext(),
                prefill = template.name,
                currentName = CardTypeName.fromString(template.name),
                existingNames = existingNames,
            ) { newName ->
                template.name = newName.value
                templateEditor.updateDiscardChangesCallback()
                Timber.i("updated card template name")
                Timber.d("updated name of template %d to '%s'", ordinal, newName)

                // update the tab
                templateEditor.mainBinding.cardTemplateEditorPager.adapter!!
                    .notifyDataSetChanged()
                // Update the tab name in previewer
                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
            }
        }

        private fun showRepositionDialog() {
            if (noteTypeCreatesDynamicNumberOfNotes() || tempModel.templateCount < 2) return
            val currentPosition = templateEditor.ord
            RepositionCardTemplateDialog.showInstance(
                requireContext(),
                tempModel.templateCount,
            ) { newPosition ->
                val newOrdinal = newPosition - 1
                if (newOrdinal == currentPosition) return@showInstance
                Timber.i("moving card template %d to %d", currentPosition, newOrdinal)
                executeWithSyncCheck(Runnable { templateEditor.repositionTemplate(currentPosition, newOrdinal) })
            }
        }

        private fun insertField(fieldToInsert: String) {
            val start = max(binding.editText.selectionStart, 0)
            val end = max(binding.editText.selectionEnd, 0)
            // add string to editText
            binding.editText.text!!.replace(min(start, end), max(start, end), fieldToInsert, 0, fieldToInsert.length)
        }

        fun setCurrentEditorView(
            viewId: Int,
            cardId: Int,
            editorContent: String,
        ) {
            // saving the cursor position before changing the editor view
            templateEditor.tabToCursorPositions[cardId]?.set(
                currentEditorViewId,
                binding.editText.selectionStart,
            )
            currentEditorViewId = viewId
            binding.editText.setText(editorContent)
            if (isCurrentPage) {
                binding.editText.requestFocus()
            }
            binding.editText.setSelection(
                templateEditor.tabToCursorPositions[cardId]?.get(
                    currentEditorViewId,
                ) ?: 0,
            )
        }

        /**
         * Cloze and image occlusion note types can generate an arbitrary number of cards from a note
         * Anki only offers:
         * * Restore to Default
         * * Browser Appearance
         */
        @NeedsTest("cannot perform operations on Image Occlusion")
        private fun noteTypeCreatesDynamicNumberOfNotes(): Boolean {
            val noteType = templateEditor.tempNoteType!!.notetype
            return noteType.isCloze || noteType.isImageOcclusion
        }

        private fun setupMenu() {
            (requireActivity() as MenuHost).addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                        menu: Menu,
                        menuInflater: MenuInflater,
                    ) {
                        menuInflater.inflate(R.menu.card_template_editor, menu)
                        setupCommonMenu(menu)
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = handleCommonMenuItemSelected(menuItem)
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED,
            )
        }

        fun deleteCardTemplate() {
            templateEditor.lifecycleScope.launch {
                val tempModel = templateEditor.tempNoteType
                val ordinal = templateEditor.ord
                val template = tempModel!!.getTemplate(ordinal)
                // Don't do anything if only one template
                if (tempModel.templateCount < 2) {
                    templateEditor.showSimpleMessageDialog(resources.getString(CommonString.card_template_editor_cant_delete))
                    return@launch
                }

                if (deletionWouldOrphanNote(tempModel, ordinal)) {
                    showOrphanNoteDialog()
                    return@launch
                }

                // Show confirmation dialog
                val numAffectedCards =
                    if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel, ordinal)) {
                        Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                        withCol { notetypes.tmplUseCount(tempModel.notetype, tempModel.getOriginalOrdinal(ordinal)!!) }
                    } else {
                        0
                    }
                confirmDeleteCards(template, tempModel.notetype, numAffectedCards)
            }
        }

        /* showOrphanNoteDialog shows a AlertDialog if the deletionWouldOrphanNote returns true
         * it displays a warning for the user when they attempt to delete a card type that
            would leave some notes without any cards (orphan notes) */
        private fun showOrphanNoteDialog() {
            val builder =
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(CommonString.orphan_note_title)
                    .setMessage(CommonString.orphan_note_message)
                    .setPositiveButton(android.R.string.ok, null)

            builder.show()
        }

        fun openBrowserAppearance(): Boolean {
            val currentTemplate = getCurrentTemplate()
            currentTemplate?.let { launchCardBrowserAppearance(it) }
            return true
        }

        fun addCardTemplate() {
            if (templateEditor.tempNoteType!!.notetype.isCloze) {
                Timber.w("addCardTemplate attempted on cloze note type")
                return
            }
            val noteType = templateEditor.tempNoteType!!.notetype
            val newTemplate = buildNewTemplate(noteType)
            launchCatchingTask {
                // Show confirmation dialog
                val numAffectedCards = withProgress { withCol { countCardsCreatedBy(noteType, newTemplate) } }
                confirmAddCards(noteType, newTemplate, numAffectedCards)
            }
        }

        fun saveNoteType(): Boolean {
            if (noteTypeHasChanged()) {
                val confirmButton = templateEditor.findViewById<View>(R.id.action_confirm)
                if (confirmButton != null) {
                    if (!confirmButton.isEnabled) {
                        Timber.d("CardTemplateEditor::discarding extra click after button disabled")
                        return true
                    }
                    confirmButton.isEnabled = false
                }
                launchCatchingTask(resources.getString(CommonString.card_template_editor_save_error)) {
                    try {
                        requireActivity().withProgress(resources.getString(CommonString.saving_model)) {
                            templateEditor.tempNoteType!!.saveToDatabase()
                        }
                        onModelSaved()
                    } catch (e: Exception) {
                        Timber.e(e, "CardTemplateEditor:: saveNoteType() failed")

                        confirmButton?.post {
                            confirmButton.isEnabled = true
                        }

                        throw e
                    }
                }
            } else {
                Timber.d("CardTemplateEditor:: note type has not changed, exiting")
                templateEditor.finish()
            }
            return true
        }

        /**
         * Setups the part of the menu that can be used either in template editor or in previewer fragment.
         */
        fun setupCommonMenu(menu: Menu) {
            menu.findItem(R.id.action_restore_to_default).title = TR.sentenceCase.restoreToDefault
            menu.findItem(R.id.action_card_browser_appearance).title = TR.sentenceCase.browserAppearance
            menu.findItem(R.id.action_reposition).isVisible = !noteTypeCreatesDynamicNumberOfNotes() && tempModel.templateCount > 1
            if (noteTypeCreatesDynamicNumberOfNotes()) {
                Timber.d("Editing cloze/occlusion note type, disabling add/delete card template and deck override functionality")
                menu.findItem(R.id.action_add).isVisible = false
                menu.findItem(R.id.action_rename).isVisible = false
                menu.findItem(R.id.action_add_deck_override).isVisible = false
            } else {
                val template = getCurrentTemplate()

                @StringRes val overrideStringRes =
                    if (template != null && template.jsonObject.has("did") && !template.jsonObject.isNull("did")) {
                        CommonString.card_template_editor_deck_override_on
                    } else {
                        CommonString.card_template_editor_deck_override_off
                    }
                menu.findItem(R.id.action_add_deck_override).setTitle(overrideStringRes)
            }

            // It is invalid to delete if there is only one card template, remove the option from UI
            if (templateEditor.tempNoteType!!.templateCount < 2) {
                menu.findItem(R.id.action_delete).isVisible = false
            }

            // Hide preview option if the view is big enough
            if (templateEditor.fragmented) {
                menu.findItem(R.id.action_preview).isVisible = false
            }

            // marked insert field menu item invisible for style view
            val isInsertFieldItemVisible = currentEditorViewId != R.id.styling_edit
            menu.findItem(R.id.action_insert_field).isVisible = isInsertFieldItemVisible
        }

        @NeedsTest("Notetype is restored to stock kind")
        private suspend fun restoreNotetypeToStock(kind: StockNotetype.Kind? = null) {
            val nid = notetypeId { ntid = tempModel.noteTypeId }
            undoableOp { notetypes.restoreNotetypeToStock(nid, kind) }
            onModelSaved()
            showThemedToast(
                requireContext(),
                TR.cardTemplatesRestoredToDefault(),
                shortLength = false,
            )
        }

        /**
         * Handles the part of the menu set by [setupCommonMenu].
         * @returns whether the given item was handled
         * @see [onMenuItemSelected] and [onMenuItemClick]
         */
        @NeedsTest("Restore to default option")
        fun handleCommonMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_add -> {
                    Timber.i("CardTemplateEditor:: Add template button pressed")
                    addCardTemplate()
                    return true
                }
                R.id.action_reposition -> {
                    showRepositionDialog()
                    return true
                }
                R.id.action_rename -> {
                    Timber.i("CardTemplateEditor:: Rename button pressed")
                    showRenameDialog()
                    return true
                }
                R.id.action_copy_as_markdown -> {
                    Timber.i("CardTemplateEditor:: Copy markdown button pressed")
                    copyMarkdownTemplateToClipboard()
                    return true
                }
                R.id.action_insert_field -> {
                    Timber.i("CardTemplateEditor:: Insert field button pressed")
                    showInsertFieldDialog()
                    return true
                }
                R.id.action_delete -> {
                    Timber.i("CardTemplateEditor:: Delete template button pressed")
                    deleteCardTemplate()
                    return true
                }
                R.id.action_add_deck_override -> {
                    Timber.i("CardTemplateEditor:: Deck override button pressed")
                    displayDeckOverrideDialog(tempModel)
                    return true
                }
                R.id.action_preview -> {
                    Timber.i("CardTemplateEditor:: Preview button pressed")
                    performPreview()
                    return true
                }
                R.id.action_confirm -> {
                    Timber.i("CardTemplateEditor:: Save note type button pressed")
                    saveNoteType()
                }
                R.id.action_card_browser_appearance -> {
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                    openBrowserAppearance()
                }
                R.id.action_restore_to_default -> {
                    Timber.i("CardTemplateEditor:: Restore to default button pressed")

                    fun askUser(kind: StockNotetype.Kind? = null) {
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.sentenceCase.restoreToDefault)
                            setMessage(TR.cardTemplatesRestoreToDefaultConfirmation())
                            setPositiveButton(CommonString.restore) { _, _ ->
                                launchCatchingTask {
                                    restoreNotetypeToStock(kind)
                                }
                            }
                            setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                        }
                    }

                    val originalStockKind = tempModel.notetype.originalStockKind
                    if (originalStockKind != ORIGINAL_STOCK_KIND_UNKNOWN_VALUE) {
                        Timber.d("Asking to restore to original stock kind %s", originalStockKind)
                        askUser()
                        return true
                    }

                    launchCatchingTask {
                        Timber.d("Unknown stock kind: asking which kind to restore to")
                        val stockNotetypeKinds = getStockNotetypeKinds()
                        val stockNotetypesNames =
                            withCol {
                                stockNotetypeKinds.map { getStockNotetype(it).name }
                            }
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.sentenceCase.restoreToDefault)
                            setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                            listItems(stockNotetypesNames) { _: DialogInterface, index: Int ->
                                val kind = stockNotetypeKinds[index]
                                askUser(kind)
                            }
                        }
                    }
                    true
                }
                else -> {
                    return false
                }
            }
        }

        private val currentTemplate: CardTemplate?
            get() =
                try {
                    val tempModel = templateEditor.tempNoteType
                    val template: BackendCardTemplate =
                        tempModel!!.getTemplate(templateEditor.ord)
                    CardTemplate(
                        front = template.qfmt,
                        back = template.afmt,
                        style = tempModel.css,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                    null
                }

        /** Copies the template to clipboard in markdown format */
        fun copyMarkdownTemplateToClipboard() {
            // A number of users who post their templates to Reddit/Discord have these badly formatted
            // It makes it much easier for people to understand if these are provided as markdown
            val template = currentTemplate ?: return

            context?.let { ctx ->
                ctx.copyToClipboard(
                    TruncatedString.from(template.toMarkdown(ctx)),
                )
            }
        }

        private fun onModelSaved() {
            Timber.d("saveModelAndExitHandler::postExecute called")
            val button = templateEditor.findViewById<View>(R.id.action_confirm)
            if (button != null) {
                button.isEnabled = true
            }
            templateEditor.tempNoteType = null
            templateEditor.finish()
        }

        fun getNote(col: Collection): Note? {
            val nid = requireArguments().getLong(EXTRA_NOTE_ID)
            return if (nid != -1L) col.getNote(nid) else null
        }

        fun performPreview() {
            launchCatchingTask {
                val notetype = templateEditor.tempNoteType!!.notetype
                val notetypeFile = NotetypeFile(requireContext(), notetype)
                val note = withCol { getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
                val args =
                    TemplatePreviewerArguments(
                        notetypeFile = notetypeFile,
                        id = note.id,
                        ord = templateEditor.ord,
                        fields = note.fields,
                        tags = note.tags,
                        fillEmpty = true,
                    )
                val intent = TemplatePreviewerPage.getIntent(requireContext(), args)
                startActivity(intent)
            }
        }

        fun displayDeckOverrideDialog(tempModel: CardTemplateNotetype) =
            launchCatchingTask {
                if (tempModel.notetype.isCloze) {
                    showSnackbar(getString(CommonString.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
                    return@launchCatchingTask
                }
                val name = getCurrentTemplateName(tempModel)
                val title = getString(CommonString.card_template_editor_deck_override)
                val explanation = getString(CommonString.deck_override_explanation, name)
                // Anki Desktop allows Dynamic decks, have reported this as a bug:
                // https://forums.ankiweb.net/t/minor-bug-deck-override-to-filtered-deck/1493
                startDeckSelection(title = title, templateEditorMessage = explanation, allowAll = false, allowFiltered = false)
            }

        private fun getCurrentTemplateName(tempModel: CardTemplateNotetype): String =
            try {
                val template = tempModel.getTemplate(templateEditor.ord)
                template.name
            } catch (e: Exception) {
                Timber.w(e, "Failed to get name for template")
                ""
            }

        private fun launchCardBrowserAppearance(currentTemplate: BackendCardTemplate) {
            val context = appContext
            val browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate)
            onCardBrowserAppearanceActivityResult.launch(browserAppearanceIntent)
        }

        @CheckResult
        private fun getCurrentTemplate(): BackendCardTemplate? {
            val currentCardTemplateIndex = getCurrentCardTemplateIndex()
            return try {
                templateEditor.tempNoteType!!.notetype.templates[currentCardTemplateIndex]
            } catch (e: JSONException) {
                Timber.w(e, "CardTemplateEditor::getCurrentTemplate - unexpectedly unable to fetch template? %d", currentCardTemplateIndex)
                null
            }
        } // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.

        /**
         * @return The index of the card template which is currently referred to by the fragment
         */
        private fun getCurrentCardTemplateIndex(): Int {
            // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.
            return requireArguments().getInt(ARG_CARD_INDEX)
        }

        private suspend fun deletionWouldOrphanNote(
            tempModel: CardTemplateNotetype?,
            position: Int,
        ): Boolean {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel!!, position)) {
                val currentDeletes = tempModel.getDeleteDbOrds(position)
                val cardIds = withCol { notetypes.getCardIdsForNoteType(tempModel.noteTypeId, currentDeletes) }
                if (cardIds == null) {
                    // It is possible but unlikely that a user has an in-memory template addition that would
                    // generate cards making the deletion safe, but we don't handle that. All users who do
                    // not already have cards generated making it safe will see this error message:
                    return true
                }
            }
            return false
        }

        private var onCardBrowserAppearanceActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) {
                    return@registerForActivityResult
                }
                onCardBrowserAppearanceResult(result.data)
            }
        private var onRequestPreviewResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) {
                    return@registerForActivityResult
                }
                CardTemplateNotetype.clearTempNoteTypeFiles()
                // Make sure the fragments reinitialize, otherwise there is staleness on return
                (templateEditor.mainBinding.cardTemplateEditorPager.adapter as TemplatePagerAdapter).ordinalShift()
                templateEditor.mainBinding.cardTemplateEditorPager.adapter!!
                    .notifyDataSetChanged()
            }

        private fun onCardBrowserAppearanceResult(data: Intent?) {
            val result = CardTemplateBrowserAppearanceEditor.Result.fromIntent(data)
            if (result == null) {
                Timber.w("Error processing Card Template Browser Appearance result")
                return
            }
            Timber.i("Applying Card Template Browser Appearance result")
            val currentTemplate = getCurrentTemplate()
            if (currentTemplate != null) {
                result.applyTo(currentTemplate)
                templateEditor.updateDiscardChangesCallback()
            }
        }

        private fun noteTypeHasChanged(): Boolean = templateEditor.noteTypeHasChanged()

        /**
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param notetype note type to remove template from, modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmDeleteCards(
            tmpl: BackendCardTemplate,
            notetype: NotetypeJson,
            numAffectedCards: Int,
        ) {
            val d = ConfirmationDialog()
            val msg =
                String.format(
                    resources.getQuantityString(
                        CommonPlurals.card_template_editor_confirm_delete,
                        numAffectedCards,
                    ),
                    numAffectedCards,
                    tmpl.jsonObject.optString("name"),
                )
            d.setArgs(
                title = getString(CommonString.delete_card_type),
                message = msg,
                positiveButtonText = getString(CommonString.dialog_positive_delete),
            )

            val deleteCard = Runnable { deleteTemplate(tmpl, notetype) }
            val confirm = Runnable { executeWithSyncCheck(deleteCard) }
            d.setConfirm(confirm)
            templateEditor.showDialogFragment(d)
        }

        /**
         * Confirm if the user wants to add new card template
         * @param notetype note type to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(
            notetype: NotetypeJson,
            newTemplate: BackendCardTemplate,
            numAffectedCards: Int,
        ) {
            val d = ConfirmationDialog()
            val msg =
                String.format(
                    resources.getQuantityString(
                        CommonPlurals.card_template_editor_confirm_add,
                        numAffectedCards,
                    ),
                    numAffectedCards,
                )
            d.setArgs(
                title = getString(CommonString.add_card_type),
                message = msg,
                positiveButtonText = getString(CommonString.menu_add),
            )

            val addCard = Runnable { addNewTemplate(notetype, newTemplate) }
            val confirm = Runnable { executeWithSyncCheck(addCard) }
            d.setConfirm(confirm)
            templateEditor.showDialogFragment(d)
        }

        /**
         * Execute an action on the schema, asking the user to confirm that a full sync is ok
         * If [schemaChangingAction] is successfully executed, then the template is reloaded.
         *
         * This method is always useful because all calls to executeWithSyncCheck may need to refresh the previewer.
         * Due to conditional generation (e.g., {{#c5}}foo{{/c5}} which is non-empty only if it's the 5th card and is
         * empty otherwise), it's important to reload the template. This is particularly useful for cloze types,
         * where a card can move from the 5th to the 6th position due to adding an extra card type, causing content
         * to change or be deleted.
         *
         * @param schemaChangingAction The action to execute (adding / removing card)
         */
        private fun executeWithSyncCheck(schemaChangingAction: Runnable) {
            try {
                templateEditor.getColUnsafe.modSchema(check = true)
                schemaChangingAction.run()
                templateEditor.updateDiscardChangesCallback()
                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(CommonString.full_sync_confirmation))
                val confirm =
                    Runnable {
                        templateEditor.getColUnsafe.modSchema(check = false)
                        schemaChangingAction.run()
                        templateEditor.updateDiscardChangesCallback()
                        templateEditor.dismissAllDialogFragments()
                    }
                val cancel = Runnable { templateEditor.dismissAllDialogFragments() }
                d.setConfirm(confirm)
                d.setCancel(cancel)
                templateEditor.showDialogFragment(d)
            }
        }

        /**
         * @param tmpl template to remove
         * @param notetype note type to remove from, updated in place by reference
         */
        private fun deleteTemplate(
            tmpl: BackendCardTemplate,
            notetype: NotetypeJson,
        ) {
            val oldTemplates = notetype.templates
            val newTemplates = CardTemplates(JSONArray())
            for (possibleMatch in oldTemplates) {
                if (possibleMatch.ord != tmpl.ord) {
                    newTemplates.append(possibleMatch)
                } else {
                    Timber.d("deleteTemplate() found match - removing template with ord %s", possibleMatch.ord)
                    templateEditor.tempNoteType!!.removeTemplate(possibleMatch.ord)
                }
            }
            notetype.templates = newTemplates
            Notetypes._updateTemplOrds(notetype)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            (templateEditor.mainBinding.cardTemplateEditorPager.adapter as TemplatePagerAdapter).ordinalShift()
            templateEditor.mainBinding.cardTemplateEditorPager.adapter!!
                .notifyDataSetChanged()
            templateEditor.mainBinding.cardTemplateEditorPager.setCurrentItem(
                newTemplates.length() - 1,
                templateEditor.animationDisabled(),
            )
        }

        /**
         * Add new template to a given note type
         * @param noteType note type to add new template to
         */
        private fun addNewTemplate(
            noteType: NotetypeJson,
            newTemplate: BackendCardTemplate,
        ) {
            val templates = noteType.templates
            templates.append(newTemplate)
            templateEditor.tempNoteType!!.addNewTemplate()
            templateEditor.mainBinding.cardTemplateEditorPager.adapter!!
                .notifyDataSetChanged()
            templateEditor.mainBinding.cardTemplateEditorPager.setCurrentItem(
                templates.length() - 1,
                templateEditor.animationDisabled(),
            )
        }

        private fun buildNewTemplate(noteType: NotetypeJson): BackendCardTemplate {
            val oldCardIndex = requireArguments().getInt(ARG_CARD_INDEX)
            val templates = noteType.templates
            val oldTemplate = templates[oldCardIndex]
            val newTemplate = Notetypes.newTemplate(newCardName(templates))
            // Set up question & answer formats
            newTemplate.qfmt = oldTemplate.qfmt
            newTemplate.afmt = oldTemplate.afmt
            // Reverse the front and back if only one template
            if (templates.length() == 1) {
                flipQA(newTemplate)
            }
            val lastExistingOrd = templates.last().ord
            Timber.d("buildNewTemplate() lastExistingOrd was %s", lastExistingOrd)
            newTemplate.setOrd(lastExistingOrd + 1)
            return newTemplate
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        @KotlinCleanup("Use Kotlin's Regex methods")
        private fun flipQA(template: BackendCardTemplate) {
            val qfmt = template.qfmt
            val afmt = template.afmt
            val m = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt)
            template.qfmt =
                if (!m.find()) {
                    afmt.replace("{{FrontSide}}", "")
                } else {
                    m.group(2)!!.trim()
                }
            template.afmt = "{{FrontSide}}\n\n<hr id=answer>\n\n$qfmt"
        }

        /**
         * Get name for new template
         * @param templates array of templates which is being added to
         * @return name for new template
         */
        private fun newCardName(templates: CardTemplates): String {
            // Start by trying to set the name to "Card n" where n is the new num of templates
            var n = templates.length() + 1
            // If the starting point for name already exists, iteratively increase n until we find a unique name
            while (true) {
                // Get new name
                val name = TR.cardTemplatesCard(n)
                // Cycle through all templates checking if new name exists
                if (templates.all { name != it.name }) {
                    return name
                }
                n += 1
            }
        }

        data class CardTemplate(
            val front: String,
            val back: String,
            val style: String,
        ) {
            fun toMarkdown(context: Context) =
                // backticks are not supported by old reddit
                with(context) {
                    buildString {
                        appendLine("**${TR.sentenceCase.frontTemplate}**\n")
                        appendLine("```html\n$front\n```\n")
                        appendLine("**${TR.sentenceCase.backTemplate}**\n")
                        appendLine("```html\n$back\n```\n")
                        appendLine("**${TR.cardTemplatesTemplateStyling()}**\n")
                        append("```css\n$style\n```")
                    }
                }
        }

        companion object {
            fun newInstance(
                cardIndex: Int,
                noteId: NoteId,
                viewId: Int,
            ): CardTemplateFragment {
                val f = CardTemplateFragment()
                val args = Bundle()
                args.putInt(ARG_CARD_INDEX, cardIndex)
                args.putLong(EXTRA_NOTE_ID, noteId)
                args.putInt(ARG_EDITOR_VIEW_ID, viewId)
                f.arguments = args
                return f
            }
        }
    }

    enum class EditTab {
        FRONT,
        BACK,
        STYLING,
    }

    companion object {
        private const val STATE_TAB_TO_CURSOR_POSITION = "tabToCursorPosition"
        private const val ARG_EDITOR_VIEW_ID = "editorViewId"
        private const val STATE_TAB_TO_VIEW_ID = "tabToViewId"
        const val EXTRA_NOTE_TYPE_ID = "noteTypeId"
        private const val EXTRA_NOTE_ID = "noteId"
        private const val EXTRA_START_ORD_ID = "ordId"
        private const val STATE_NOTE_TYPE_ID = "noteTypeId"
        private const val STATE_NOTE_ID = "noteId"
        private const val STATE_STARTING_TEMPLATE_ORDINAL = "ordId"
        private const val ARG_CARD_INDEX = "card_ord"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_TEMPLATE_EDITOR_PANE_WEIGHT = "cardTemplateEditorPaneWeight"
        private const val PREF_TEMPLATE_PREVIEWER_PANE_WEIGHT = "cardTemplatePreviewerPaneWeight"

        // Time to wait before refreshing the previewer
        private val REFRESH_PREVIEW_DELAY = 1.seconds

        @Suppress("unused")
        private const val REQUEST_PREVIEWER = 0

        @Suppress("unused")
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1

        @CheckResult
        fun getIntent(
            context: Context,
            noteTypeId: NoteTypeId,
            noteId: NoteId? = null,
            ord: CardOrdinal? = null,
        ) = Intent(context, CardTemplateEditor::class.java)
            .apply {
                putExtra(EXTRA_NOTE_TYPE_ID, noteTypeId)
                noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
                ord?.let { putExtra(EXTRA_START_ORD_ID, it) }

                Timber.d(
                    "Built intent for CardTemplateEditor; ntid: %s; nid: %s; ord: %s",
                    noteTypeId,
                    noteId,
                    ord,
                )
            }
    }
}

@VisibleForTesting
internal fun Collection.countCardsCreatedBy(
    noteType: NotetypeJson,
    template: BackendCardTemplate,
    timeout: Duration = 1.seconds,
): Int {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    val unreferencedOrd = generateSequence(0) { it + 1 }.first { "c${it + 1}" !in template.qfmt }
    val templateBytes = BackendUtils.toJsonBytes(template.deepClone().apply { setOrd(unreferencedOrd) })
    val noteIds = notetypes.nids(noteType)
    return try {
        noteIds.count { nid ->
            if (deadline.hasPassedNow()) {
                Timber.w("counting the cards of a new card type timed out")
                Analytics.sendAnalyticsException(TimeoutException("New card type count timed out with ${noteIds.size} notes"), false)
                return noteIds.size
            }
            !backend
                .renderUncommittedCardLegacy(getNote(nid).toBackendNote(), unreferencedOrd, templateBytes, false, true)
                .isEmpty
        }
    } catch (e: BackendTemplateException) {
        Timber.w(e, "new card type has a template error")
        0
    }
}
