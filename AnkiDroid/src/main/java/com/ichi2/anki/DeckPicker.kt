/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

// usage of 'this' in constructors when class is non-final - weak warning
// should be OK as this is only non-final for tests
@file:Suppress("LeakingThis")

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.SQLException
import android.graphics.PixelFormat
import android.os.Bundle
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.MenuItemCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.draganddrop.DropHelper
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import anki.decks.deckId
import anki.sync.SyncStatusResponse
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPickerFloatingActionMenu.FloatingActionBarToggleListener
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.DBError
import com.ichi2.anki.InitialActivity.StartupFailure.DatabaseLocked
import com.ichi2.anki.InitialActivity.StartupFailure.DirectoryNotAccessible
import com.ichi2.anki.InitialActivity.StartupFailure.DiskFull
import com.ichi2.anki.InitialActivity.StartupFailure.FutureAnkidroidVersion
import com.ichi2.anki.InitialActivity.StartupFailure.SDCardNotMounted
import com.ichi2.anki.IntentHandler.Companion.intentToReviewDeckFromShortcuts
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeDeckPickerBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.DeckDeletionResult
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.deckpicker.DeckPickerViewModel.FlattenedDeckList
import com.ichi2.anki.deckpicker.DeckPickerViewModel.StartupResponse
import com.ichi2.anki.deckpicker.EmptyCardsResult
import com.ichi2.anki.deckpicker.OptionsMenuState
import com.ichi2.anki.deckpicker.ShortcutData
import com.ichi2.anki.deckpicker.SyncIconState
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerAnalyticsOptInDialog
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.EditDeckDescriptionDialog
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.dialogs.FatalErrorDialog
import com.ichi2.anki.dialogs.ImportDialog.ImportDialogListener
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.SchedulerUpgradeDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResultListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.pages.CongratsPage.Companion.onDeckCompleted
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySyncDiscardUndo
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.animations.fadeIn
import com.ichi2.anki.ui.animations.fadeOut
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ShortcutUtils
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.positionIsVisible
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.setImageDrawableSafe
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.ClipboardUtil.IMPORT_MIME_TYPES
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils
import com.ichi2.utils.configureView
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.showDialogIfWebViewOutdated
import com.ichi2.utils.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.io.File

/**
 * The current entry point for AnkiDroid. Displays decks, allowing users to study. Many other functions.
 *
 * On a tablet, this is a fragmented view, with [StudyOptionsFragment] to the right: [tryShowStudyOptionsPanel]
 *
 * Often used as navigation to: [Reviewer], [NoteEditorFragment] (adding notes), [StudyOptionsFragment] [SharedDecksDownloadFragment]
 *
 * Responsibilities:
 * * Setup/upgrades of the application: [handleStartup]
 * * Error handling [handleDbLocked]
 * * Displaying a tree of decks, some of which may be collapsible: [deckListAdapter]
 *   * Allows users to study the decks
 *   * Displays deck progress
 *   * A long press opens a menu allowing modification of the deck
 *   * Filtering decks (if more than 10) [toolbarSearchView]
 * * Controlling syncs
 *   * A user may [pull down][pullToSyncWrapper] on the 'tree view' to sync
 *   * A [button][updateSyncIconFromState] which relies on [SyncIconState] to display whether a sync is needed
 *   * Blocks the UI and displays sync progress when syncing
 * * Displaying 'General' AnkiDroid options: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportDialogListener]
 * * A Floating Action Button [floatingActionMenu] allowing the user to quickly add notes/cards.
 * * A custom image as a background can be added: [applyDeckPickerBackground]
 */
@KotlinCleanup("lots to do")
@NeedsTest("If the collection has been created, the app intro is not displayed")
@NeedsTest("If the user selects 'Sync Profile' in the app intro, a sync starts immediately")
@NeedsTest("Regression test of #19555 or remove 'android:configChanges' for the screen")
open class DeckPicker :
    NavigationDrawerActivity(),
    SyncErrorDialogListener,
    ImportDialogListener,
    OnRequestPermissionsResultCallback,
    ChangeManager.Subscriber,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider,
    CollectionPermissionScreenLauncher {
    val viewModel: DeckPickerViewModel by viewModels()

    private lateinit var binding: ActivityHomescreenBinding

    @VisibleForTesting
    internal val deckPickerBinding: IncludeDeckPickerBinding
        get() = binding.deckPickerPane
    private val floatingActionButtonBinding: IncludeFloatingAddButtonBinding
        get() = deckPickerBinding.floatingActionButton

    override var fragmented: Boolean
        get() =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK ==
                Configuration.SCREENLAYOUT_SIZE_XLARGE
        set(_) = throw UnsupportedOperationException()

    // Short animation duration from system
    private var shortAnimDuration = 0

    private lateinit var decksLayoutManager: LinearLayoutManager
    private lateinit var deckListAdapter: DeckAdapter
    private lateinit var pullToSyncWrapper: SwipeRefreshLayout

    @VisibleForTesting
    lateinit var floatingActionMenu: DeckPickerFloatingActionMenu

    var activeSnackBar: Snackbar? = null
    private val activeSnackbarCallback =
        object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onShown(transientBottomBar: Snackbar?) {
                activeSnackBar = transientBottomBar
            }

            override fun onDismissed(
                transientBottomBar: Snackbar?,
                event: Int,
            ) {
                activeSnackBar = null
            }
        }
    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = floatingActionButtonBinding.fabMain.takeIf { it.isVisible }
        addCallback(activeSnackbarCallback)
    }

    private var syncMediaProgressJob: Job? = null

    // flag keeping track of when the app has been paused
    var activityPaused = false
        private set

    @VisibleForTesting
    val dueTree: DeckNode?
        get() = viewModel.dueTree

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var searchDecksIcon: MenuItem? = null

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var syncOnResume = false

    private var toolbarSearchItem: MenuItem? = null
    private var toolbarSearchView: AccessibleSearchView? = null

    override val permissionScreenLauncher = recreateActivityResultLauncher()

    private val reviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                processReviewResults(it.resultCode)
            },
        )

    private val showNewVersionInfoLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                showStartupScreensAndDialogs(baseContext.sharedPrefs(), 3)
            },
        )

    private val loginForSyncLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    syncOnResume = true
                }
            },
        )

    private val requestPathUpdateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                // The collection path was inaccessible on startup so just close the activity and let user restart
                finish()
            },
        )

    private val apkgFileImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    lifecycleScope.launch {
                        withProgress(message = getString(R.string.import_preparing_file)) {
                            withContext(Dispatchers.IO) {
                                onSelectedPackageToImport(it.data!!)
                            }
                        }
                    }
                }
            },
        )

    private val csvImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    onSelectedCsvForImport(it.data!!)
                }
            },
        )

    private val exitAndSyncBackCallback =
        object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                // TODO: Room for improvement now we use back callbacks
                // can't use launchCatchingTask because any errors
                // would need to be shown in the UI
                lifecycleScope
                    .launch {
                        automaticSync(runInBackground = true)
                    }.invokeOnCompletion {
                        finish()
                    }
            }
        }

    private val closeFloatingActionBarBackPressCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                floatingActionMenu.closeFloatingActionMenu(applyRiseAndShrinkAnimation = true)
            }
        }

    private inner class DeckPickerActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == RESULT_MEDIA_EJECTED) {
                onSdCardNotMounted()
                return
            }
            callback(result)
        }
    }

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private fun onDeckClick(
        deckId: DeckId,
        selectionType: DeckSelectionType,
    ) {
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        launchCatchingTask {
            handleDeckSelection(deckId, selectionType)
            if (fragmented) {
                // Calling notifyDataSetChanged() will update the color of the selected deck.
                // This interferes with the ripple effect, so we don't do it if lollipop and not tablet view
                deckListAdapter.notifyDataSetChanged()
                updateDeckList()
            }
        }
    }

    private suspend fun showDeckPickerContextMenu(deckId: DeckId) {
        val menu = DeckPickerContextMenu.newInstance(deckId)
        CardBrowser.clearLastDeckId()
        showDialogFragment(menu)
    }

    private suspend fun showDeckPickerRightClickContextMenu(request: DeckPickerViewModel.RightClickMenuRequest) {
        DeckPickerMenuContentProvider.show(
            deckPicker = this@DeckPicker,
            deckId = request.deckId,
            x = request.x,
            y = request.y,
        )
    }

    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------

    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        // Then set theme and content view
        super.onCreate(savedInstanceState)

        binding = ActivityHomescreenBinding.inflate(layoutInflater)

        // handle the first load: display the app introduction
        // This screen is currently better equipped to handle errors than IntroductionActivity
        if (!hasShownAppIntro() && AnkiDroidApp.fatalError == null) {
            Timber.i("Displaying app intro")
            val appIntro = Intent(this, IntroductionActivity::class.java)
            appIntro.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(appIntro)
            finish() // calls onDestroy() immediately
            return
        }
        Timber.d("Not displaying app intro")
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.d("launched from introduction activity login: syncing")
            syncOnResume = true
        }

        setViewBinding(binding)
        enableToolbar()
        // TODO This method is run on every activity recreation, which can happen often.
        //  It seems that the original idea was for for this to only run once, on app start.
        //  This method triggers backups, sync, and may re-show dialogs
        //  that may have been dismissed. Make this run only once?
        handleStartup()

        registerReceiver()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer()
        title = resources.getString(R.string.app_name)

        deckPickerBinding.deckPickerContent.visibility = View.GONE
        deckPickerBinding.noDecksPlaceholder.visibility = View.GONE

        // specify a LinearLayoutManager for the RecyclerView
        decksLayoutManager = LinearLayoutManager(this)
        deckPickerBinding.decks.layoutManager = decksLayoutManager

        deckListAdapter =
            DeckAdapter(
                this,
                onDeckSelected = { onDeckClick(it, DeckSelectionType.DEFAULT) },
                onDeckCountsSelected = { onDeckClick(it, DeckSelectionType.SHOW_STUDY_OPTIONS) },
                onDeckChildrenToggled = { deckId ->
                    viewModel.toggleDeckExpand(deckId)
                    dismissAllDialogFragments()
                },
                onDeckContextRequested = { deckId -> viewModel.requestContextMenu(deckId) },
                onDeckRightClick = { deckId, x, y ->
                    viewModel.requestRightClickContextMenu(deckId, x, y)
                    Timber.d("Right Click on deck recorded!! %d, %f %f", deckId, x, y)
                },
            )
        deckPickerBinding.decks.adapter = deckListAdapter

        lifecycleScope.launch { applyDeckPickerBackground() }

        pullToSyncWrapper =
            deckPickerBinding.pullToSyncWrapper.apply {
                setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
                setOnRefreshListener {
                    Timber.i("Pull to Sync: Syncing")
                    pullToSyncWrapper.isRefreshing = false
                    sync()
                }
                viewTreeObserver.addOnScrollChangedListener {
                    pullToSyncWrapper.isEnabled = decksLayoutManager.findFirstCompletelyVisibleItemPosition() == 0
                }
            }
        // Setup the FloatingActionButtons
        floatingActionMenu =
            DeckPickerFloatingActionMenu(this, binding, this).apply {
                toggleListener =
                    FloatingActionBarToggleListener { isOpening ->
                        closeFloatingActionBarBackPressCallback.isEnabled = isOpening
                    }
            }

        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        with(this) { showDialogIfWebViewOutdated() }

        // If a review reminder deserialization error has recently occurred
        // (ex. on app boot, when the app opened, etc.), inform the user via a dialog
        ReviewRemindersDatabase.checkDeserializationErrors(this)

        setFragmentResultListener(REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION -> {
                    Timber.d("Custom study created")
                    updateDeckList()
                    openStudyOptions()
                }
                CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                    Timber.d("Study limits updated")
                    fragment?.refreshInterface()
                    updateDeckList()
                }
            }
        }

        setDeckPickerContextMenuResultListener { result ->
            handleContextMenuSelection(result.option, result.deckId)
        }

        setFragmentResultListener(StudyOptionsFragment.REQUEST_STUDY_OPTIONS_STUDY) { _, _ ->
            Timber.d("Opening study screen from DeckPicker's study options panel")
            openReviewer()
        }

        pullToSyncWrapper.configureView(
            this,
            IMPORT_MIME_TYPES,
            DropHelper.Options
                .Builder()
                .setHighlightColor(R.color.material_lime_green_A700)
                .setHighlightCornerRadiusPx(0)
                .build(),
            onReceiveContentListener,
        )

        setupFlows()
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, exitAndSyncBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        onBackPressedDispatcher.addCallback(this, closeFloatingActionBarBackPressCallback)
        super.setupBackPressedCallbacks()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        fun onDeckDeleted(result: DeckDeletionResult) {
            floatingActionButtonBinding.fabMain.isVisible = true
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(R.string.undo) { undo() }
            }
        }

        fun onCardsEmptied(result: EmptyCardsResult) {
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(R.string.undo) { undo() }
            }
        }

        fun onDeckCountsChanged(unit: Unit) {
            updateDeckList()
            tryShowStudyOptionsPanel()
        }

        fun onDestinationChanged(destination: Destination) {
            startActivity(destination.toIntent(this))
        }

        fun onExportDeck(deckId: DeckId) {
            ExportDialogFragment.newInstance(deckId).show(supportFragmentManager, "exportOptions")
        }

        fun onPromptUserToUpdateScheduler(op: Unit) {
            SchedulerUpgradeDialog(
                activity = this,
                onUpgrade = {
                    launchCatchingRequiringOneWaySyncDiscardUndo {
                        this@DeckPicker.withProgress { withCol { sched.upgradeToV2() } }
                        showThemedToast(this@DeckPicker, TR.schedulingUpdateDone(), false)
                    }
                },
                onCancel = {
                    onBackPressedDispatcher.onBackPressed()
                },
            ).showDialog()
        }

        fun onOptionsMenuUpdated(unused: OptionsMenuState) = invalidateOptionsMenu()

        fun onStudiedTodayChanged(studiedToday: String) {
            deckPickerBinding.reviewSummaryTextView.text = studiedToday
            // Adjust bottom margin of fabLinearLayout based on reviewSummaryTextView height
            deckPickerBinding.reviewSummaryTextView.doOnLayout { view ->
                val layoutParams = floatingActionButtonBinding.fabLinearLayout.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.setMargins(0, 0, 0, view.height / 2)
                floatingActionButtonBinding.fabLinearLayout.layoutParams = layoutParams
            }
        }

        fun onCollectionStatusChanged(isInInitialState: Boolean) {
            // Hide the background when there are no cards to improve text readability.
            deckPickerBinding.background.isVisible = !isInInitialState
            if (animationDisabled()) {
                deckPickerBinding.deckPickerContent.isVisible = !isInInitialState
                deckPickerBinding.noDecksPlaceholder.isVisible = isInInitialState
                return
            }

            val decksListShown = deckPickerBinding.deckPickerContent.isVisible
            val placeholderShown = deckPickerBinding.noDecksPlaceholder.isVisible
            if (isInInitialState) {
                deckPickerBinding.deckPickerContent.fadeOut(shortAnimDuration)
                deckPickerBinding.noDecksPlaceholder.fadeIn(shortAnimDuration).startDelay =
                    if (decksListShown) {
                        shortAnimDuration * 2L
                    } else {
                        0L
                    }
            } else {
                deckPickerBinding.deckPickerContent.fadeIn(shortAnimDuration).startDelay =
                    if (placeholderShown) {
                        shortAnimDuration * 2L
                    } else {
                        0L
                    }
                deckPickerBinding.noDecksPlaceholder.fadeOut(shortAnimDuration)
            }
        }

        fun onResizingDividerVisibilityChanged(isVisible: Boolean) {
            binding.resizingDivider?.isVisible = isVisible
        }

        fun onCardsDueChanged(dueCount: Int?) {
            if (dueCount == null) {
                supportActionBar?.subtitle = null
                return
            }

            supportActionBar?.apply {
                subtitle = if (dueCount == 0) null else resources.getQuantityString(R.plurals.widget_cards_due, dueCount, dueCount)
                val toolbar = findViewById<Toolbar>(R.id.toolbar)
                TooltipCompat.setTooltipText(toolbar, toolbar.subtitle)
            }
        }

        fun onStudyOptionsVisibilityChanged(collectionHasNoCards: Boolean) {
            invalidateOptionsMenu()
            binding.studyoptionsFrame?.isVisible = !collectionHasNoCards
        }

        fun onDeckListChanged(deckList: FlattenedDeckList) {
            deckListAdapter.submit(
                data = deckList.data,
                hasSubDecks = deckList.hasSubDecks,
            )
        }

        fun onFocusedDeckChanged(deckId: DeckId?) {
            val position = deckId?.let { viewModel.findDeckPosition(it) } ?: 0

            // Skip centering if the deck is already on screen.
            // Scrolling during a tap animation causes deck labels to overlap on older devices.
            if (decksLayoutManager.positionIsVisible(position)) {
                return
            }
            // HACK: a small delay is required before scrolling works
            deckPickerBinding.decks.postDelayed({
                decksLayoutManager.scrollToPositionWithOffset(position, deckPickerBinding.decks.height / 2)
            }, 10)
        }

        fun onDecksReloaded(param: Unit) {
            hideProgressBar()
        }

        fun onStartupResponse(response: StartupResponse) {
            Timber.d("onStartupResponse: %s", response)
            when (response) {
                is StartupResponse.RequestPermissions -> {
                    viewModel.flowOfStartupResponse.value = null // Prevent duplicate permission screen launches
                    permissionScreenLauncher.launch(
                        PermissionsActivity.getIntent(this, response.requiredPermissions),
                    )
                }

                is StartupResponse.Success -> {
                    // Set flowOfStartupResponse to null after handling so it isn't re-emitted on resume.
                    // Must stay here: clearing in ViewModel would break cold start (collector is only active at RESUMED).
                    viewModel.flowOfStartupResponse.value = null
                    showStartupScreensAndDialogs(sharedPrefs(), 0)

                    if (tryShowStudyOptionsPanel()) {
                        ResizablePaneManager(
                            parentLayout = requireNotNull(binding.deckpickerXlView) { "deckpickerXlView" },
                            divider = requireNotNull(binding.resizingDivider) { "resizingDivider" },
                            leftPane = deckPickerBinding.root,
                            rightPane = requireNotNull(binding.studyoptionsFragment) { "studyoptionsFragment" },
                            sharedPrefs = Prefs.getUiConfig(this),
                            leftPaneWeightKey = PREF_DECK_PICKER_PANE_WEIGHT,
                            rightPaneWeightKey = PREF_STUDY_OPTIONS_PANE_WEIGHT,
                        )
                    }
                }
                is StartupResponse.FatalError -> handleStartupFailure(response.failure)
            }
        }

        fun onError(errorMessage: String) {
            AlertDialog
                .Builder(this)
                .setTitle(R.string.vague_error)
                .setMessage(errorMessage)
                .show()
        }

        viewModel.deckDeletedNotification.launchCollectionInLifecycleScope(::onDeckDeleted)
        viewModel.emptyCardsNotification.launchCollectionInLifecycleScope(::onCardsEmptied)
        viewModel.flowOfDeckCountsChanged.launchCollectionInLifecycleScope(::onDeckCountsChanged)
        viewModel.flowOfDestination.launchCollectionInLifecycleScope(::onDestinationChanged)
        viewModel.flowOfNavigate.launchCollectionInLifecycleScope { navigate(it) }
        viewModel.flowOfExportDeck.launchCollectionInLifecycleScope(::onExportDeck)
        viewModel.flowOfCreateShortcut.launchCollectionInLifecycleScope(::createIcon)
        viewModel.flowOfDisableShortcuts.launchCollectionInLifecycleScope(::disableDeckAndChildrenShortcuts)
        viewModel.onError.launchCollectionInLifecycleScope(::onError)
        viewModel.flowOfPromptUserToUpdateScheduler.launchCollectionInLifecycleScope(::onPromptUserToUpdateScheduler)
        viewModel.flowOfOptionsMenuState.filterNotNull().launchCollectionInLifecycleScope(::onOptionsMenuUpdated)
        viewModel.flowOfStudiedTodayStats.launchCollectionInLifecycleScope(::onStudiedTodayChanged)
        viewModel.flowOfDeckListInInitialState.filterNotNull().launchCollectionInLifecycleScope(::onCollectionStatusChanged)
        viewModel.flowOfCardsDue.launchCollectionInLifecycleScope(::onCardsDueChanged)
        viewModel.flowOfCollectionHasNoCards.launchCollectionInLifecycleScope(::onStudyOptionsVisibilityChanged)
        viewModel.flowOfDeckList.launchCollectionInLifecycleScope(::onDeckListChanged)
        viewModel.flowOfFocusedDeck.launchCollectionInLifecycleScope(::onFocusedDeckChanged)
        viewModel.flowOfResizingDividerVisible.launchCollectionInLifecycleScope(::onResizingDividerVisibilityChanged)
        viewModel.flowOfDecksReloaded.launchCollectionInLifecycleScope(::onDecksReloaded)
        viewModel.flowOfStartupResponse.filterNotNull().launchCollectionInLifecycleScope(::onStartupResponse)
        viewModel.flowOfShowContextMenu.launchCollectionInLifecycleScope(::showDeckPickerContextMenu)
        viewModel.flowOfShowRightClickContextMenu.launchCollectionInLifecycleScope(::showDeckPickerRightClickContextMenu)
    }

    private val onReceiveContentListener =
        OnReceiveContentListener { _, payload ->
            val (uriContent, remaining) = payload.partition { item -> item.uri != null }

            val clip = uriContent?.clip ?: return@OnReceiveContentListener remaining
            val uri = clip.getItemAt(0).uri
            if (!ImportUtils.FileImporter().isValidImportType(this, uri)) {
                // TODO: This does nothing
                ImportResult.Failure(getString(R.string.import_log_no_apkg))
                return@OnReceiveContentListener remaining
            }

            try {
                // Intent is nullable because `clip.getItemAt(0).intent` always returns null
                ImportUtils.FileImporter().handleContentProviderFile(this, uri, Intent().setData(uri))
                onResume()
            } catch (e: Exception) {
                Timber.w(e)
                CrashReportService.sendExceptionReport(e, "DeckPicker::onReceiveContent")
                return@OnReceiveContentListener remaining
            }

            return@OnReceiveContentListener remaining
        }

    private fun handleContextMenuSelection(
        selectedOption: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) {
        when (selectedOption) {
            DeckPickerContextMenuOption.DELETE_DECK -> {
                Timber.i("ContextMenu: Delete deck selected")

                /* we can only disable the shortcut for now as it is restricted by Google https://issuetracker.google.com/issues/68949561?pli=1#comment4
                 * if fixed or given free hand to delete the shortcut with the help of API update this method and use the new one
                 */
                // TODO: it feels buggy that this is not called on all deck deletion paths
                viewModel.disableDeckAndChildrenShortcuts(deckId)
                dismissAllDialogFragments()
                deleteDeck(deckId)
            }
            DeckPickerContextMenuOption.DECK_OPTIONS -> {
                Timber.i("ContextMenu: Open deck options selected")
                viewModel.openDeckOptions(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY -> {
                Timber.i("ContextMenu: Custom study option selected")
                showDialogFragment(CustomStudyDialog.createInstance(deckId))
            }
            DeckPickerContextMenuOption.CREATE_SHORTCUT -> {
                Timber.i("ContextMenu: Create icon for a deck")
                viewModel.createIcon(deckId)
            }
            DeckPickerContextMenuOption.RENAME_DECK -> {
                Timber.i("ContextMenu: Rename deck selected")
                renameDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EXPORT_DECK -> {
                Timber.i("ContextMenu: Export deck selected")
                viewModel.exportDeck(deckId)
            }
            DeckPickerContextMenuOption.UNBURY -> {
                Timber.i("ContextMenu: Unbury deck selected")
                viewModel.unburyDeck(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD -> {
                Timber.i("ContextMenu: Rebuild deck selected")
                rebuildFiltered(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY -> {
                Timber.i("ContextMenu: Empty deck selected")
                emptyFiltered(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CREATE_SUBDECK -> {
                Timber.i("ContextMenu: Create Subdeck selected")
                createSubDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.BROWSE_CARDS -> {
                Timber.i("ContextMenu: Browse cards")
                viewModel.browseCards(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.ADD_CARD -> {
                Timber.i("ContextMenu: Add selected")
                viewModel.addNote(deckId, setAsCurrent = true)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EDIT_DESCRIPTION -> {
                Timber.i("Editing deck description for deck '%d'", deckId)
                showDialogFragment(EditDeckDescriptionDialog.newInstance(deckId))
            }
            DeckPickerContextMenuOption.SCHEDULE_REMINDERS -> {
                Timber.i("Scheduling review reminders for deck '%d'", deckId)
                viewModel.scheduleReviewReminders(deckId)
                dismissAllDialogFragments()
            }
        }
    }

    /**
     * @see DeckPickerViewModel.handleStartup
     */
    private fun handleStartup() {
        val context = AnkiDroidApp.instance

        val environment: AnkiDroidEnvironment =
            object : AnkiDroidEnvironment {
                private val folder = selectAnkiDroidFolder(context)

                override fun hasRequiredPermissions(): Boolean = folder.hasRequiredPermissions(context)

                override val requiredPermissions: PermissionSet
                    get() = folder.permissionSet

                override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(context)
            }

        viewModel.handleStartup(environment = environment)
    }

    @VisibleForTesting
    fun handleStartupFailure(failure: StartupFailure) {
        when (failure) {
            is SDCardNotMounted -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            is DirectoryNotAccessible -> {
                Timber.i("AnkiDroid directory inaccessible")
                if (ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(this)) {
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL)
                } else {
                    showDirectoryNotAccessibleDialog()
                }
            }
            is FutureAnkidroidVersion -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION)
            }
            is DatabaseLocked -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
            }
            is StartupFailure.InitializationError -> FatalErrorDialog.build(this, failure).show()
            is DiskFull -> displayNoStorageError()
            is DBError -> displayDatabaseFailure(CustomExceptionData.fromException(failure.exception))
        }
    }

    private fun showDirectoryNotAccessibleDialog() {
        val contentView =
            TextView(this).apply {
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
                text =
                    getString(
                        R.string.directory_inaccessible_info,
                        getString(R.string.link_full_storage_access),
                    )
            }
        AlertDialog.Builder(this).show {
            title(R.string.directory_inaccessible)
            customView(
                contentView,
                paddingTop = 16.dp.toPx(this@DeckPicker),
                paddingStart = 32.dp.toPx(this@DeckPicker),
                paddingEnd = 32.dp.toPx(this@DeckPicker),
            )
            positiveButton(R.string.open_settings) {
                val settingsIntent = PreferencesActivity.getIntent(this@DeckPicker, AdvancedSettingsFragment::class)
                requestPathUpdateLauncher.launch(settingsIntent)
            }
        }
    }

    private fun displayDatabaseFailure(exceptionData: CustomExceptionData? = null) {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
    }

    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DISK_FULL)
    }

    private suspend fun applyDeckPickerBackground() {
        val result = BackgroundImage.resolve(this)
        if (result is BackgroundImage.ResolveResult.Failure) {
            showThemedToast(this, result.message(this), shortLength = false)
        }
        val drawable = (result as? BackgroundImage.ResolveResult.Ready)?.drawable
        val applied =
            deckPickerBinding.background.setImageDrawableSafe(drawable) {
                showThemedToast(this, getString(R.string.background_image_too_large), shortLength = false)
            }
        // activityHasBackground calls notifyDataSetChanged, ensure only 1 call
        deckListAdapter.activityHasBackground = applied && drawable != null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewModel.flowOfStartupResponse.value is StartupResponse.FatalError) {
            return false
        }

        Timber.d("onCreateOptionsMenu()")
        floatingActionMenu.closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
        // TODO: Refactor menu handling logic to the activity
        // The menus for the fragmented view should be the responsibility of the activity.
        // This would mean extracting the menu logic out of the fragments, extending it to the full width of the activity,
        // and having the activity be responsible for it. This change should reduce complexity.
        // We should have two menu files for the DeckPicker (fragmented/non), and one for the Options (non-fragmented)
        menuInflater.inflate(R.menu.deck_picker, menu)
        menu.findItem(R.id.deck_picker_action_filter)?.let {
            toolbarSearchItem = it
            setupSearchIcon(it)
            toolbarSearchView = it.actionView as AccessibleSearchView
        }
        toolbarSearchView?.maxWidth = Integer.MAX_VALUE

        menu.findItem(R.id.action_export_collection)?.title = TR.actionsExport()
        menu.findItem(R.id.action_check_database)?.title = TR.sentenceCase.checkDatabase
        menu.findItem(R.id.action_check_media)?.title = TR.sentenceCase.checkMediaAction
        menu.findItem(R.id.action_deck_delete)?.title = TR.sentenceCase.deleteDeck
        setupMediaSyncMenuItem(menu)
        // redraw menu synchronously to avoid flicker
        updateMenuFromState(menu)
        updateSearchVisibilityFromState(menu)
        // ...then launch a task to possibly update the visible icons.
        // Store the job so that tests can easily await it. In the future
        // this may be better done by injecting a custom test scheduler
        // into CollectionManager, and awaiting that.
        createMenuJob =
            launchCatchingTask {
                viewModel.refreshMenuState()
                updateSearchVisibilityFromState(menu)
                updateDeckRelatedMenuItems(menu)
                updateMenuFromState(menu)
            }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_custom_study)?.setShowAsAction(
            if (fragmented) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER,
        )
        return super.onPrepareOptionsMenu(menu)
    }

    fun setupMediaSyncMenuItem(menu: Menu) {
        // shouldn't be necessary, but `invalidateOptionsMenu()` is called way more than necessary
        syncMediaProgressJob?.cancel()

        val syncItem = menu.findItem(R.id.action_sync)
        val progressIndicator =
            syncItem.actionView
                ?.findViewById<LinearProgressIndicator>(R.id.progress_indicator)

        val workManager = WorkManager.getInstance(this)
        val flow = workManager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)

        syncMediaProgressJob =
            lifecycleScope.launch {
                flow.flowWithLifecycle(lifecycle).collectLatest {
                    val workInfo = it.lastOrNull()
                    if (workInfo?.state == WorkInfo.State.RUNNING && progressIndicator?.isVisible == false) {
                        Timber.i("DeckPicker: Showing media sync progress indicator")
                        progressIndicator.isVisible = true
                    } else if (progressIndicator?.isVisible == true) {
                        Timber.i("DeckPicker: Hiding media sync progress indicator")
                        progressIndicator.isVisible = false
                    }
                }
            }
    }

    private fun setupSearchIcon(menuItem: MenuItem) {
        menuItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                // When SearchItem is expanded
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    Timber.i("DeckPicker:: SearchItem opened")
                    // Hide the floating action button if it is visible
                    floatingActionMenu.hideFloatingActionButton()
                    activeSnackBar?.anchorView = null
                    return true
                }

                // When SearchItem is collapsed
                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    Timber.i("DeckPicker:: SearchItem closed")
                    // Show the floating action button if it is hidden
                    floatingActionMenu.showFloatingActionButton()
                    activeSnackBar?.anchorView = floatingActionButtonBinding.fabMain
                    return true
                }
            },
        )

        (menuItem.actionView as AccessibleSearchView).run {
            queryHint = getString(R.string.search_decks)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        viewModel.updateDeckFilter(newText)
                        return true
                    }
                },
            )
        }
        searchDecksIcon = menuItem
    }

    fun updateMenuFromState(menu: Menu) {
        viewModel.optionsMenuState?.run {
            updateUndoLabelFromState(menu.findItem(R.id.action_undo), undoLabel, undoAvailable)
            updateSyncIconFromState(menu.findItem(R.id.action_sync), this)
        }
        updateDeckRelatedMenuItems(menu)
    }

    /**
     * Shows/hides deck related menu items based on the collection being empty or not.
     */
    private fun updateDeckRelatedMenuItems(menu: Menu) {
        viewModel.optionsMenuState?.run {
            menu.findItem(R.id.action_deck_rename)?.isVisible = !isColEmpty
            menu.findItem(R.id.action_deck_delete)?.isVisible = !isColEmpty
            // added to the menu by StudyOptionsFragment
            menu.findItem(R.id.action_deck_or_study_options)?.isVisible = !isColEmpty
        }
    }

    private fun updateSearchVisibilityFromState(menu: Menu) {
        viewModel.optionsMenuState?.run {
            menu.findItem(R.id.deck_picker_action_filter)?.isVisible = searchIcon
        }
    }

    private fun updateUndoLabelFromState(
        menuItem: MenuItem,
        undoLabel: String?,
        undoAvailable: Boolean,
    ) {
        menuItem.run {
            if (undoLabel != null && undoAvailable) {
                isVisible = true
                title = undoLabel
            } else {
                isVisible = false
            }
        }
    }

    private fun updateSyncIconFromState(
        menuItem: MenuItem,
        state: OptionsMenuState,
    ) {
        val provider =
            MenuItemCompat.getActionProvider(menuItem) as? SyncActionProvider
                ?: return
        val tooltipText =
            when (state.syncIcon) {
                SyncIconState.Normal, SyncIconState.PendingChanges -> R.string.button_sync
                SyncIconState.OneWay -> R.string.sync_menu_title_one_way_sync
                SyncIconState.NotLoggedIn -> R.string.sync_menu_title_no_account
            }
        provider.setTooltipText(getString(tooltipText))
        when (state.syncIcon) {
            SyncIconState.Normal -> {
                BadgeDrawableBuilder.removeBadge(provider)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(this)
                    .withColor(getColor(R.color.badge_warning))
                    .replaceBadge(provider)
            }
            SyncIconState.OneWay, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder(this)
                    .withText('!')
                    .withColor(getColor(R.color.badge_error))
                    .replaceBadge(provider)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        when (item.itemId) {
            R.id.action_undo -> {
                Timber.i("DeckPicker:: Undo button pressed")
                undo()
                return true
            }
            R.id.deck_picker_action_filter -> {
                Timber.i("DeckPicker:: Search button pressed")
                return true
            }
            R.id.action_sync -> {
                Timber.i("DeckPicker:: Sync button pressed")
                toolbarSearchItem?.collapseActionView()
                val actionProvider = MenuItemCompat.getActionProvider(item) as? SyncActionProvider
                if (actionProvider?.isProgressShown == true) {
                    launchCatchingTask {
                        monitorMediaSync(this@DeckPicker)
                    }
                } else {
                    sync()
                }
                return true
            }
            R.id.action_import -> {
                Timber.i("DeckPicker:: Import button pressed")
                showImportDialog()
                return true
            }
            R.id.action_check_database -> {
                Timber.i("DeckPicker:: Check database button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }
            R.id.action_check_media -> {
                Timber.i("DeckPicker:: Check media button pressed")
                showMediaCheckDialog()
                return true
            }
            R.id.action_empty_cards -> {
                Timber.i("DeckPicker:: Empty cards button pressed")
                EmptyCardsDialogFragment().show(
                    supportFragmentManager,
                    EmptyCardsDialogFragment.TAG,
                )
                return true
            }
            R.id.action_model_browser_open -> {
                Timber.i("DeckPicker:: Model browser button pressed")
                viewModel.openManageNoteTypes()
                return true
            }
            R.id.action_restore_backup -> {
                Timber.i("DeckPicker:: Restore from backup button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                return true
            }
            R.id.action_deck_rename -> {
                launchCatchingTask {
                    val targetDeckId = withCol { decks.selected() }
                    renameDeckDialog(targetDeckId)
                }
                return true
            }
            R.id.action_deck_delete -> {
                launchCatchingTask {
                    withProgress(resources.getString(R.string.delete_deck)) {
                        viewModel.deleteSelectedDeck().join()
                    }
                }
                return true
            }
            R.id.action_export_collection -> {
                Timber.i("DeckPicker:: Export menu item selected")
                ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
                return true
            }
            R.id.action_create_backup -> {
                Timber.i("DeckPicker::Create backup")
                createBackup()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun createBackup() {
        launchCatchingTask {
            withProgress(message = TR.profilesCreatingBackup()) {
                performBackupInBackground(true)
            }
            showThemedToast(this@DeckPicker, TR.profilesBackupCreated(), false)
        }
    }

    private fun showMediaCheckDialog() {
        Timber.i("showing media check dialog")
        AlertDialog.Builder(this).show {
            title(text = TR.sentenceCase.checkMediaTitle)
            message(text = getString(R.string.check_media_warning))
            positiveButton(R.string.dialog_ok) {
                Timber.i("Starting media check")
                startActivity(MediaCheckFragment.getIntent(this@DeckPicker))
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    fun showCreateFilteredDeckDialog() {
        startActivity(FilteredDeckOptionsFragment.getIntent(this))
    }

    fun exportCollection() {
        ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
    }

    private fun processReviewResults(resultCode: Int) {
        if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
            CongratsPage.onReviewsCompleted(this, getColUnsafe.sched.totalCount() == 0)
            fragment?.refreshInterface()
        }
    }

    override fun onResume() {
        activityPaused = false
        // stop onResume() processing the message.
        // we need to process the message after `loadDeckCounts` is added in refreshState
        // As `loadDeckCounts` is cancelled in `migrate()`
        val message = dialogHandler.popMessage()
        super.onResume()
        if (navDrawerIsReady() && hasCollectionStoragePermissions()) {
            refreshState()
        }
        message?.let { dialogHandler.sendStoredMessage(it) }
    }

    fun refreshState() {
        // Due to the App Introduction, this may be called before permission has been granted.
        if (syncOnResume && hasCollectionStoragePermissions()) {
            syncOnResume = false
            Timber.i("Performing Sync on Resume")
            Permissions.requestNotificationPermissionsForSyncing(this)
            sync()
        } else {
            selectNavigationItem(R.id.nav_decks)
            updateDeckList()
            title = resources.getString(R.string.app_name)
        }
        // Update sync status (if we've come back from a screen)
        invalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("mIsFABOpen", floatingActionMenu.isFABOpen)
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.getString("dbRestorationPath", it.newAnkiDroidDirectory.absolutePath)
            }
        }
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
        floatingActionMenu.showFloatingActionButton()
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        floatingActionMenu.isFABOpen = savedInstanceState.getBoolean("mIsFABOpen")
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            val path = File(path)
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
    }

    override fun onPause() {
        activityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        viewModel.loadDeckCounts?.cancel()
        super.onPause()
    }

    /**
     * Performs a sync if the conditions are met, e.g. user is logged in, there are changes,
     * and auto sync is enabled.
     * @param runInBackground whether the sync should be performed in the background or not
     * @return whether a sync was performed or not.
     */
    private suspend fun automaticSync(runInBackground: Boolean = false): Boolean {
        /**
         * @return whether there are collection changes to be sync.
         *
         * It DOES NOT include if there are media to be synced.
         */
        suspend fun areThereChangesToSync(): Boolean {
            val auth = syncAuth() ?: return false
            val status =
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().syncStatus(auth)
                }.required

            return when (status) {
                SyncStatusResponse.Required.NO_CHANGES,
                SyncStatusResponse.Required.UNRECOGNIZED,
                null,
                -> false
                SyncStatusResponse.Required.FULL_SYNC,
                SyncStatusResponse.Required.NORMAL_SYNC,
                -> true
            }
        }

        fun syncIntervalPassed(): Boolean {
            val automaticSyncIntervalInMS = AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES * 60 * 1000
            return TimeManager.time.intTimeMS() - Prefs.lastSyncTime > automaticSyncIntervalInMS
        }

        when {
            !Prefs.isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
            MeteredSyncPolicy.shouldBlock() -> Timber.d("autoSync: blocked by metered connection")
            !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
            !runInBackground && !syncIntervalPassed() -> Timber.d("autoSync: interval not passed")
            !isLoggedIn() -> Timber.d("autoSync: not logged in")
            !areThereChangesToSync() -> {
                Timber.d("autoSync: no collection changes to sync. Syncing media if set")
                if (shouldFetchMedia()) {
                    val auth = syncAuth() ?: return false
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }
            else -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return false
                    SyncWorker.start(this, auth, shouldFetchMedia())
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync()
                }
                return true
            }
        }
        return false
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (toolbarSearchView?.hasFocus() == true) {
            Timber.d("Skipping keypress: search action bar is focused")
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                Timber.i("Adding Note from keypress")
                viewModel.addNote(deckId = null, setAsCurrent = true)
                return true
            }
            KeyEvent.KEYCODE_B -> {
                if (event.isShiftPressed && event.isCtrlPressed) {
                    // shortcut SHIFT + CTRL + B
                    Timber.i("Create backup from keypress")
                    createBackup()
                } else if (event.isCtrlPressed) {
                    // Shortcut: CTRL + B
                    Timber.i("show restore backup dialog from keypress")
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                } else {
                    // Shortcut: B
                    Timber.i("Open Browser from keypress")
                    openCardBrowser()
                }
                return true
            }
            KeyEvent.KEYCODE_Y -> {
                Timber.i("Sync from keypress")
                sync()
                return true
            }
            KeyEvent.KEYCODE_SLASH -> {
                Timber.d("Search from keypress")
                if (toolbarSearchItem?.isVisible == true) {
                    toolbarSearchItem?.expandActionView()
                }
                return true
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                launchCatchingTask {
                    handleDeckSelection(withCol { decks.selected() }, DeckSelectionType.SKIP_STUDY_OPTIONS)
                }
                return true
            }
            KeyEvent.KEYCODE_T -> {
                Timber.i("Open Statistics from keypress")
                openStatistics()
                return true
            }
            KeyEvent.KEYCODE_C -> {
                // Shortcut: C
                Timber.i("Check database from keypress")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }
            KeyEvent.KEYCODE_D -> {
                // Shortcut: D
                Timber.i("Create Deck from keypress")
                showCreateDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                Timber.i("Create Filtered Deck from keypress")
                showCreateFilteredDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    if (event.isShiftPressed) {
                        // Shortcut: Shift + DEL - Delete deck without confirmation dialog
                        Timber.i("Shift+DEL: Deck deck without confirmation")
                        viewModel.focusedDeck?.let { did -> deleteDeck(did) }
                    } else {
                        // Shortcut: DEL
                        Timber.i("Delete Deck from keypress")
                        showDeleteDeckConfirmationDialog()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                // Shortcut: R
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    Timber.i("Rename Deck from keypress")
                    viewModel.focusedDeck?.let { did -> renameDeckDialog(did) }
                    return true
                }
            }
            KeyEvent.KEYCODE_P -> {
                Timber.i("Open Settings from keypress")
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_M -> {
                Timber.i("Check media from keypress")
                showMediaCheckDialog()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    // Shortcut: CTRL + E
                    Timber.i("Show export dialog from keypress")
                    exportCollection()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + I
                    Timber.i("Show import dialog from keypress")
                    showImportDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + N
                    Timber.i("Open ManageNoteTypes from keypress")
                    viewModel.openManageNoteTypes()
                    return true
                }
            }
            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Displays a confirmation dialog for deleting deck.
     */
    private fun showDeleteDeckConfirmationDialog() =
        launchCatchingTask {
            val focusedDeck =
                viewModel.focusedDeck ?: run {
                    Timber.w("no focused deck")
                    return@launchCatchingTask
                }

            val (deckName, totalCards, isFilteredDeck) =
                withCol {
                    Triple(
                        decks.name(focusedDeck),
                        decks.cardCount(focusedDeck, includeSubdecks = true),
                        decks.isFiltered(focusedDeck),
                    )
                }
            val confirmDeleteDeckDialog =
                DeckPickerConfirmDeleteDeckDialog.newInstance(
                    deckName = deckName,
                    deckId = focusedDeck,
                    totalCards = totalCards,
                    isFilteredDeck = isFilteredDeck,
                )
            showDialogFragment(confirmDeleteDeckDialog)
        }

    /**
     * Perform the following tasks:
     * Automatic backup
     * loadStudyOptionsFragment() if tablet
     * Automatic sync
     */
    private fun onFinishedStartup() {
        launchCatchingTask {
            if (!automaticSync()) {
                BackupPromptDialog.showIfAvailable(this@DeckPicker)
            }
        }
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendMessage(CollectionLoadingErrorDialog().toMessage())
    }

    // VisibleForTesting: method is mocked, should be replaced
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun addNote(did: DeckId? = null) {
        viewModel.addNote(did, true)
    }

    private fun showStartupScreensAndDialogs(
        preferences: SharedPreferences,
        skip: Int,
    ) {
        if (!BackupManager.enoughDiscSpace(CollectionHelper.getCurrentAnkiDroidDirectory(this))) {
            Timber.i("Not enough space to do backup")
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance())
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            Timber.i("No space left")
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance())
            preferences.edit { remove("noSpaceLeft") }
        } else if (InitialActivity.performSetupFromFreshInstallOrClearedPreferences(preferences)) {
            onFinishedStartup()
        } else if (skip < 2 && !InitialActivity.isLatestVersion(preferences)) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")
            // The user might appreciate us now, see if they will help us get better?
            if (!preferences.contains(UsageAnalytics.ANALYTICS_OPTIN_KEY)) {
                displayAnalyticsOptInDialog()
            }

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.pkgVersionCode
            Timber.i("Current AnkiDroid version: %s", current)
            val previous: Long =
                if (preferences.contains(DeckPickerViewModel.UPGRADE_VERSION_KEY)) {
                    // Upgrading currently installed app
                    viewModel.getPreviousVersion(preferences, current)
                } else {
                    // Fresh install
                    current
                }
            preferences.edit { putLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, current) }

            val upgradedPreferences = InitialActivity.upgradePreferences(this, previous)
            // Integrity check loads asynchronously and then restart deck picker when finished
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                ActivityCompat.recreate(this)
                return
            }

            // If no changes are required we go to the new features activity
            // There the "lastVersion" is set, so that this code is not reached again
            if (VersionUtils.isReleaseVersion) {
                Timber.i("Displaying new features")
                val infoIntent = Intent(this, Info::class.java)
                infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
                showNewVersionInfoLauncher.launch(infoIntent)
            } else {
                Timber.i("Dev Build - not showing 'new features'")
                // Don't show new features dialog for development builds
                InitialActivity.setUpgradedToLatestVersion(preferences)
                val ver = resources.getString(R.string.updated_version, VersionUtils.pkgVersionName)
                postSnackbar(ver, Snackbar.LENGTH_SHORT)
                showStartupScreensAndDialogs(preferences, 2)
            }
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    // #16061. We have to queue snackbar to avoid the misaligned snackbar showed from onCreate()
    private fun postSnackbar(
        text: CharSequence,
        duration: Int = Snackbar.LENGTH_LONG,
    ) {
        binding.rootLayout.post { showSnackbar(text, duration) }
    }

    @VisibleForTesting
    protected open fun displayAnalyticsOptInDialog() {
        showDialogFragment(DeckPickerAnalyticsOptInDialog.newInstance())
    }

    private fun undo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: SyncErrorDialog.Type) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(
        dialogType: SyncErrorDialog.Type,
        message: String?,
    ) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        showAsyncDialogFragment(newFragment, Channel.SYNC)
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        CrashReportService.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        // TODO: doesn't work on null collection-only on non-openable(is this still relevant with withCol?)
        launchCatchingTask(resources.getString(R.string.deck_repair_error)) {
            Timber.d("doInBackgroundRepairCollection")
            val result =
                withProgress(resources.getString(R.string.backup_repair_deck_progress)) {
                    Timber.i("RepairCollection: Closing collection")
                    CollectionManager.ensureClosed()
                    val colFile =
                        CollectionManager.collectionPathInValidFolder().requireDiskBasedCollection().colDb
                    BackupManager.repairCollection(colFile)
                }
            if (!result) {
                showThemedToast(this@DeckPicker, resources.getString(R.string.deck_repair_error), true)
                showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle database integrity check
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            AlertDialog.Builder(this).show {
                title(text = TR.sentenceCase.checkDatabase)
                message(text = status.getWarningDetails(this@DeckPicker))
                positiveButton(R.string.integrity_check_continue_anyway) {
                    performIntegrityCheck()
                }
                negativeButton(R.string.dialog_cancel)
            }
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        handleDatabaseCheck()
    }

    override fun mediaCheck() {
        showMediaCheckDialog()
    }

    open fun handleDbLocked() {
        Timber.i("Displaying Database Locked")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
    }

    fun restoreFromBackup(path: String) {
        importColpkg(path)
    }

    // Helper function to check if there are any saved stacktraces
    fun hasErrorFiles(): Boolean {
        for (file in fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true
            }
        }
        return false
    }

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
     it after the user has decided. */
    var mediaUsnOnConflict: Int? = null

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val hkey = Prefs.hkey
        if (hkey.isNullOrEmpty()) {
            Timber.w("User not logged in")
            pullToSyncWrapper.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        MeteredSyncPolicy.confirmThen(
            // After selecting 'upload/download', the user has already accepted the metered warning.
            skipPrompt = conflict != null,
            // TODO: why is this needed? 1f91b2868d
            onDialogShown = ::refreshState,
        ) {
            handleNewSync(conflict, shouldFetchMedia())
        }
    }

    override fun loginToSyncServer() {
        val intent = AccountActivity.getIntent(this, forResult = true)
        loginForSyncLauncher.launch(intent)
    }

    // Callback to import a file -- adding it to existing collection
    override fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        startActivity(AnkiPackageImporterFragment.getIntent(this, importPath))
    }

    // Callback to import a file -- replacing the existing collection
    override fun importReplace(importPath: String) {
        Timber.d("importReplace() for file %s", importPath)
        importColpkg(importPath)
    }

    /**
     * Displays [StudyOptionsFragment] in a side panel on larger devices
     *
     * @see [ActivityHomescreenBinding.studyoptionsFragment]
     *
     * @return whether the panel was shown
     */
    private fun tryShowStudyOptionsPanel(): Boolean {
        val containerId = binding.studyoptionsFragment?.id ?: return false
        supportFragmentManager.commit {
            replace(containerId, StudyOptionsFragment())
        }
        return true
    }

    val fragment: StudyOptionsFragment?
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as? StudyOptionsFragment

    /**
     * Refresh the deck picker when the SD card is inserted.
     */
    override val broadcastsActions =
        super.broadcastsActions +
            mapOf(
                SdCardReceiver.MEDIA_MOUNT
                    to { ActivityCompat.recreate(this) },
            )

    fun openAnkiWebSharedDecks() {
        if (!NetworkUtils.isOnline) {
            showSnackbar(R.string.check_network)
            Timber.d("DeckPicker:: No network, Shared deck download failed")
            return
        }
        val intent = Intent(this, SharedDecksActivity::class.java)
        startActivity(intent)
    }

    private fun openStudyOptions() {
        if (tryShowStudyOptionsPanel()) return

        // otherwise, we need to launch the activity
        Timber.i("Opening Study Options")
        val intent = Intent()
        intent.setClass(this, StudyOptionsActivity::class.java)
        reviewLauncher.launch(intent)
    }

    private fun openReviewerOrStudyOptions(selectionType: DeckSelectionType) {
        when (selectionType) {
            DeckSelectionType.DEFAULT -> {
                if (tryShowStudyOptionsPanel()) return
                openReviewer()
            }
            DeckSelectionType.SHOW_STUDY_OPTIONS -> openStudyOptions()
            DeckSelectionType.SKIP_STUDY_OPTIONS -> openReviewer()
        }
    }

    @NeedsTest("14608: Ensure that the deck options refer to the selected deck")
    @NeedsTest("18586: handle clicking on an empty filtered deck")
    private suspend fun handleDeckSelection(
        did: DeckId,
        selectionType: DeckSelectionType,
    ) {
        // ignore requests(ex: from keyboard shortcuts) when the collection is empty(and
        // adapter has no decks)
        if (deckListAdapter.itemCount <= 0) {
            return
        }

        fun showEmptyDeckSnackbar() =
            showSnackbar(R.string.empty_deck) {
                setAction(R.string.menu_add) { viewModel.addNote(did, true) }
            }

        /** Check if we need to update the fragment or update the deck list */
        fun updateUi() {
            // Tablets must always show the study options that corresponds to the current deck,
            // regardless of whether the deck is currently reviewable or not.
            if (tryShowStudyOptionsPanel()) return

            // On phones, we update the deck list to ensure the currently selected deck is
            // highlighted correctly.
            updateDeckList()
        }

        withCol { decks.select(did) }
        deckListAdapter.updateSelectedDeck(did)
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        viewModel.focusedDeck = did

        // TODO: Reuse dueTree from ViewModel instead of recalculating for better performance.
        val deck = withCol { sched.deckDueTree().find(did) }
        if (deck?.hasCardsReadyToStudy() == true) {
            openReviewerOrStudyOptions(selectionType)
            return
        }

        val isEmpty = withCol { decks.cardCount(did, includeSubdecks = true) == 0 }
        if (!deck?.filtered!! && isEmpty) {
            showEmptyDeckSnackbar()
            updateUi()
        } else {
            onDeckCompleted()
        }
    }

    /**
     * @see DeckPickerViewModel.updateDeckList
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun updateDeckList() {
        launchCatchingTask {
            withProgress { viewModel.updateDeckList().join() }
        }
    }

    private fun createIcon(shortcutData: ShortcutData) {
        // This code should not be reachable with lower versions
        val shortcut =
            ShortcutInfoCompat
                .Builder(this, shortcutData.deckId.toString())
                .setIntent(
                    intentToReviewDeckFromShortcuts(this, shortcutData.deckId),
                ).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setShortLabel(shortcutData.shortLabel)
                .setLongLabel(shortcutData.longLabel)
                .build()
        try {
            // success does not mean the user selected 'create', only that the feature is supported
            val success = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            Timber.i("Create shortcut for deck %d. Request displayed: %b", shortcutData.deckId, success)

            // User report: "success" is true even if Vivo does not have permission
            if (AdaptionUtil.isVivo) {
                showThemedToast(this, getString(R.string.create_shortcut_error_vivo), false)
            }
            // #18601: MIUI gates shortcut creation behind Settings - Privacy Protection -
            // Other permissions - AnkiDroid - Home screen shortcuts [add shortcuts to Home screen]
            // TODO: Determine the result of 'success'
            if (AdaptionUtil.isMiui) {
                showThemedToast(this, getString(R.string.create_shortcut_error_miui, getString(R.string.app_name)), false)
            }
            if (!success) {
                showThemedToast(this, getString(R.string.create_shortcut_failed), false)
            }
        } catch (e: Exception) {
            Timber.w(e)
            showThemedToast(this, getString(R.string.create_shortcut_error, e.localizedMessage), false)
        }
    }

    /** Disables the shortcut of the deck and the children belonging to it.*/
    @NeedsTest("ensure collapsed decks are also deleted")
    private fun disableDeckAndChildrenShortcuts(deckTreeDids: List<String>) {
        val errorMessage: CharSequence = getString(R.string.deck_shortcut_doesnt_exist)
        ShortcutUtils.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        launchCatchingTask {
            val currentName = withCol { decks.name(did) }
            val createDeckDialog =
                CreateDeckDialog(this@DeckPicker, R.string.rename_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
            createDeckDialog.deckName = currentName
            createDeckDialog.onNewDeckCreated = {
                dismissAllDialogFragments()
                deckListAdapter.notifyDataSetChanged()
                updateDeckList()
                tryShowStudyOptionsPanel()
            }
            createDeckDialog.showDialog()
        }
    }

    /**
     * Displays a dialog for creating a new deck.
     *
     * @see CreateDeckDialog
     */
    fun showCreateDeckDialog() {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
        createDeckDialog.onNewDeckCreated = {
            updateDeckList()
            invalidateOptionsMenu()
        }
        createDeckDialog.showDialog()
    }

    /**
     * Deletes the provided deck, child decks, and all cards inside.
     * @param did ID of the deck to delete
     */
    fun deleteDeck(did: DeckId) =
        launchCatchingTask {
            withProgress(resources.getString(R.string.delete_deck)) {
                viewModel.deleteDeck(did).join()
            }
        }

    @NeedsTest("14285: regression test to ensure UI is updated after this call")
    fun rebuildFiltered(did: DeckId) {
        launchCatchingTask {
            withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
                viewModel.rebuildFilteredDeck(did).join()
            }
            updateDeckList()
            tryShowStudyOptionsPanel()
        }
    }

    private fun emptyFiltered(did: DeckId) {
        launchCatchingTask {
            withProgress {
                viewModel.emptyFilteredDeck(did).join()
            }
        }
    }

    override fun onAttachedToWindow() {
        if (!fragmented) {
            val window = window
            window.setFormat(PixelFormat.RGBA_8888)
        }
    }

    private fun openReviewer() {
        Timber.i("Opening Reviewer")
        val intent = Reviewer.getIntent(this)
        reviewLauncher.launch(intent)
    }

    private fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, did)
        createDeckDialog.onNewDeckCreated = {
            // a deck was created
            dismissAllDialogFragments()
            deckListAdapter.notifyDataSetChanged()
            updateDeckList()
            tryShowStudyOptionsPanel()
            invalidateOptionsMenu()
        }
        createDeckDialog.showDialog()
    }

    /**
     * The number of decks which are visible to the user (excluding decks if the parent is collapsed).
     * Not the total number of decks
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val visibleDeckCount: Int
        get() = deckListAdapter.itemCount

    /**
     * Check if at least one deck is being displayed.
     */
    fun hasAtLeastOneDeckBeingDisplayed(): Boolean = deckListAdapter.itemCount > 0 && decksLayoutManager.getChildAt(0) != null

    private enum class DeckSelectionType {
        /** Show study options if fragmented, otherwise, review  */
        DEFAULT,

        /** Always show study options (if the deck counts are clicked)  */
        SHOW_STUDY_OPTIONS,

        /** Always open reviewer (keyboard shortcut)  */
        SKIP_STUDY_OPTIONS,
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOfNotNull(
                    shortcut("A", R.string.menu_add_note),
                    shortcut("B", R.string.card_browser_context_menu),
                    shortcut("Y", R.string.pref_cat_sync),
                    shortcut("/", R.string.deck_conf_cram_search),
                    shortcut("S", Translations::decksStudyDeck),
                    shortcut("T", R.string.open_statistics),
                    shortcut("C") { this.sentenceCase.checkDatabase },
                    shortcut("D", R.string.new_deck),
                    shortcut("F", R.string.new_dynamic_deck),
                    if (fragmented) shortcut("DEL") { this.sentenceCase.deleteDeck } else null,
                    if (fragmented) shortcut("Shift+DEL", R.string.delete_deck_without_confirmation) else null,
                    if (fragmented) shortcut("R", R.string.rename_deck) else null,
                    shortcut("P", R.string.open_settings),
                    shortcut("M") { this.sentenceCase.checkMediaAction },
                    shortcut("Ctrl+E", R.string.export_collection),
                    shortcut("Ctrl+Shift+I", R.string.menu_import),
                    shortcut("Ctrl+Shift+N", R.string.model_browser_label),
                ),
                R.string.deck_picker_group,
            )

    companion object {
        /**
         * Result codes from other activities
         */
        const val RESULT_MEDIA_EJECTED = 202

        /**
         * If passed into the intent, the user should have been logged in and DeckPicker
         * should sync immediately.
         *
         * This is for the 'download existing collection from AnkiWeb' use case
         */
        const val INTENT_SYNC_FROM_LOGIN = "syncFromLogin"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @VisibleForTesting
        const val REQUEST_STORAGE_PERMISSION = 0

        // For automatic syncing
        // 10 minutes in milliseconds..
        private const val AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES: Long = 10
        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400

        private const val PREF_DECK_PICKER_PANE_WEIGHT = "deckPickerPaneWeight"
        private const val PREF_STUDY_OPTIONS_PANE_WEIGHT = "studyOptionsPaneWeight"

        /**
         * Builds an intent for [DeckPicker]
         */
        fun getIntent(
            context: Context,
            autoSync: Boolean = false,
        ) = Intent(context, DeckPicker::class.java).apply {
            if (autoSync) {
                putExtra(INTENT_SYNC_FROM_LOGIN, true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.i("Sync requested from Login")
            this.syncOnResume = true
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        lifecycleScope.launch { viewModel.refreshMenuState() }
        if (changes.studyQueues && handler !== this && handler !== viewModel) {
            if (!activityPaused) {
                // No need to update while the activity is paused, because `onResume` calls `refreshState` that calls `updateDeckList`.
                updateDeckList()
            }
        }
    }

    override fun onImportColpkg(colpkgPath: String?) {
        launchCatchingTask {
            // as the current collection is closed before importing a new collection, make sure the
            // new collection is open before the code to update the DeckPicker ui runs
            withCol { }
            invalidateOptionsMenu()
            updateDeckList()
            importColpkgListener?.onImportColpkg(colpkgPath)
        }
    }

    override fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent> = apkgFileImportResultLauncher

    override fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent> = csvImportResultLauncher
}

/** Android's onCreateOptionsMenu does not play well with coroutines, as
 * it expects the menu to have been fully configured by the time the routine
 * returns. This results in flicker, as the menu gets blanked out, and then
 * configured a moment later when the coroutine runs. To work around this,
 * the current state is stored in the deck picker so that we can redraw the
 * menu immediately. */

class CollectionLoadingErrorDialog :
    DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
        "CollectionLoadErrorDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Collection could not be opened
        activity.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}

val ActivityHomescreenBinding.studyoptionsFrame: FragmentContainerView?
    get() = studyoptionsFragment
