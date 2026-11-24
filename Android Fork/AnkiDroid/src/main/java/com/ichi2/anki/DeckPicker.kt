// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

// usage of 'this' in constructors when class is non-final - weak warning
// should be OK as this is only non-final for tests
@file:Suppress("LeakingThis")

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.SQLException
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.draganddrop.DropHelper
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPickerFloatingActionMenu.CloseAnimation
import com.ichi2.anki.DeckPickerFloatingActionMenu.FloatingActionBarToggleListener
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.DBError
import com.ichi2.anki.InitialActivity.StartupFailure.DatabaseLocked
import com.ichi2.anki.InitialActivity.StartupFailure.DirectoryNotAccessible
import com.ichi2.anki.InitialActivity.StartupFailure.DiskFull
import com.ichi2.anki.InitialActivity.StartupFailure.FutureAnkidroidVersion
import com.ichi2.anki.InitialActivity.StartupFailure.SDCardNotMounted
import com.ichi2.anki.InitialActivity.StartupFailure.StorageUndecided
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsAddEditReminderHandler
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsStudyHandler
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.android.animationDisabled
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.ChangelogDestination
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeDeckPickerBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.DeckDeletionResult
import com.ichi2.anki.deckpicker.DeckPickerProgress
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AutomaticSyncAction
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
import com.ichi2.anki.dialogs.DeckPickerMaintenanceDialog
import com.ichi2.anki.dialogs.DeckPickerMaintenanceDialog.Check
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.EditDeckDescriptionDialog
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.dialogs.FatalErrorDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportViewModel
import com.ichi2.anki.dialogs.MeteredSyncDialog
import com.ichi2.anki.dialogs.ProfileLoadFailureDialog
import com.ichi2.anki.dialogs.SchedulerUpgradeDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.dialogs.registerMeteredSyncHandler
import com.ichi2.anki.dialogs.registerProfileLoadFailureClosedHandler
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResultListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.uiConfigPrefs
import com.ichi2.anki.shareddeck.SharedDecksActivity
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.study.StudyAvailability
import com.ichi2.anki.study.StudyCompletionUi
import com.ichi2.anki.study.currentDeckStudyState
import com.ichi2.anki.sync.MeteredSyncPermission
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySyncDiscardUndo
import com.ichi2.anki.timebudget.showTimeBudgetDialog
import com.ichi2.anki.ui.BottomFadeFrameLayout
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.animations.fadeIn
import com.ichi2.anki.ui.animations.fadeOut
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ShortcutUtils
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.doOnScrolled
import com.ichi2.anki.utils.ext.onGlobalLayout
import com.ichi2.anki.utils.ext.positionIsVisible
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.setImageDrawableSafe
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.anki.widgets.DeckHierarchyLinesDecoration
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.themes.Themes
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.ClipboardUtil.IMPORT_MIME_TYPES
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils
import com.ichi2.utils.configureView
import com.ichi2.utils.customView
import com.ichi2.utils.isRtl
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.showDialogIfWebViewOutdated
import com.ichi2.utils.title
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.io.File
import com.ichi2.anki.common.android.R as CommonR

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
 *   * Such as import: [ImportViewModel]
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
    OnRequestPermissionsResultCallback,
    ChangeManager.Subscriber,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider,
    CollectionPermissionScreenLauncher {
    val viewModel: DeckPickerViewModel by viewModels()

    private val importViewModel: ImportViewModel by viewModels()

    internal lateinit var binding: ActivityHomescreenBinding
        private set

    @VisibleForTesting
    internal val deckPickerBinding: IncludeDeckPickerBinding
        get() = binding.deckPickerPane

    @VisibleForTesting
    val floatingActionButtonBinding: IncludeFloatingAddButtonBinding
        get() = deckPickerBinding.floatingActionButton
    override var fragmented: Boolean
        get() = binding.studyoptionsFragment != null
        set(_) = throw UnsupportedOperationException()

    override val analyticsScreenName: String
        get() = selectedBottomNavItem()?.analyticsScreenName ?: super.analyticsScreenName

    internal val bottomNavigationEnabled: Boolean
        get() = Prefs.devBottomNavEnabled && !fragmented

    // Short animation duration from system
    private var shortAnimDuration = 0

    private lateinit var decksLayoutManager: LinearLayoutManager
    private lateinit var deckListAdapter: DeckAdapter
    private var lastFocusedDeck: DeckId? = null
    private var backgroundImageErrorShown = false
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
        // Follow the FAB as it moves when the keyboard opens or closes.
        isAnchorViewLayoutListenerEnabled = true
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
                    onSelectedFileToImport(it.data!!)
                }
            },
        )

    private val csvImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    onSelectedFileToImport(it.data!!)
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

    internal val bottomNavBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                binding.bottomNavigation?.selectTab(NavigationItem.HOME)
            }
        }

    private val closeFloatingActionBarBackPressCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.SHORT)
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
        val menu = DeckPickerContextMenu.createOrShowError(deckId) ?: return
        CardBrowser.clearLastDeckId()
        showDialogFragment(menu)
    }

    private suspend fun showDeckPickerRightClickContextMenu(request: DeckPickerViewModel.RightClickMenuRequest) {
        DeckPickerMenuContentProvider.show(
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

        super.onCreate(savedInstanceState)
        lastFocusedDeck = savedInstanceState?.getLong(STATE_LAST_FOCUSED_DECK)?.takeIf { it != 0L }
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                    Themes.currentTheme != DayTheme.EINK
                },
            navigationBarStyle = BottomFadeFrameLayout.navigationBarStyle(),
        )

        binding = ActivityHomescreenBinding.inflate(layoutInflater)

        // handle the first load: display the app introduction
        // This screen is currently better equipped to handle errors than IntroductionActivity
        if (!hasShownAppIntro() && !AnkiDroidApp.startupErrors.hasFatalError()) {
            Timber.i("Displaying app intro")
            val appIntro = Intent(this, IntroductionActivity::class.java)
            appIntro.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(appIntro)
            finish() // calls onDestroy() immediately
            return
        }
        Timber.d("Not displaying app intro")
        if (intent.hasExtra(EXTRA_SYNC_FROM_LOGIN)) {
            Timber.d("launched from introduction activity login: syncing")
            syncOnResume = true
        }

        setViewBinding(binding)
        if (!fragmented) {
            // On recreation into this single-pane layout (e.g. a foldable changing form
            // factor), saved state can still hold the split-pane's side panel fragment.
            // A restored fragment without its container is never displayed, but it would
            // still reach its view lifecycle and register its menu items with this
            // activity. Remove it before that happens. See #21555.
            supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)?.let { restoredFragment ->
                supportFragmentManager.commitNow {
                    remove(restoredFragment)
                }
            }
        }
        enableToolbar()
        // TODO This method is run on every activity recreation, which can happen often.
        //  It seems that the original idea was for this to only run once, on app start.
        //  This method triggers backups, sync, and may re-show dialogs
        //  that may have been dismissed. Make this run only once?
        handleStartup()

        registerReceiver()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer()
        if (bottomNavigationEnabled) {
            disableDrawerSwipe()
            disableDrawerIndicator()
        }
        setupBottomNavigation()
        setupEdgeToEdge()
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
        // The first list may arrive after RecyclerView restores its view hierarchy.
        deckListAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        deckPickerBinding.decks.adapter = deckListAdapter
        if (bottomNavigationEnabled) {
            deckPickerBinding.decks.addItemDecoration(
                DeckHierarchyLinesDecoration(this, deckListAdapter),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                applyDeckPickerBackground()
                try {
                    awaitCancellation()
                } finally {
                    deckPickerBinding.background.setImageDrawable(null)
                }
            }
        }

        setupPullToSync()
        // Setup the FloatingActionButtons
        floatingActionMenu =
            DeckPickerFloatingActionMenu(
                context = this,
                homescreenBinding = binding,
                deckPicker = this,
                toggleListener =
                    FloatingActionBarToggleListener { isOpening ->
                        closeFloatingActionBarBackPressCallback.isEnabled = isOpening
                    },
                initiallyOpen = savedInstanceState?.getBoolean("mIsFABOpen") == true,
            )

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

        setFragmentResultListener(Check.MEDIA.requestKey) { _, _ ->
            Timber.i("Starting media check")
            startActivity(MediaCheckFragment.getIntent(this))
        }
        setFragmentResultListener(Check.DATABASE.requestKey) { _, _ ->
            performIntegrityCheck()
        }
        setFragmentResultListener(SchedulerUpgradeDialog.REQUEST_SCHEDULER_UPGRADE) { _, result ->
            if (result.getBoolean(SchedulerUpgradeDialog.RESULT_UPGRADE)) {
                launchCatchingRequiringOneWaySyncDiscardUndo {
                    this@DeckPicker.withProgress { withCol { sched.upgradeToV2() } }
                    showThemedToast(this@DeckPicker, TR.schedulingUpdateDone(), false)
                }
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        registerMeteredSyncHandler { syncMedia ->
            handleNewSync(
                conflict = null,
                syncMedia = syncMedia,
                meteredSyncPermission = MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME,
            )
        }

        registerStudyOptionsStudyHandler {
            Timber.d("Opening study screen from DeckPicker's study options panel")
            openReviewer()
        }

        registerStudyOptionsAddEditReminderHandler { did: DeckId ->
            Timber.d("Opening review reminders screen from DeckPicker's study options panel")
            openScheduleReminders(did)
        }

        // A fatal startup error waits until the profile explanation is closed, so it can't cover it
        registerProfileLoadFailureClosedHandler {
            (viewModel.flowOfStartupResponse.value as? StartupResponse.FatalError)?.let { handleStartupFailure(it.failure) }
        }

        pullToSyncWrapper.configureView(
            this,
            IMPORT_MIME_TYPES,
            DropHelper.Options
                .Builder()
                .setHighlightColor(CommonR.color.material_lime_green_A700)
                .setHighlightCornerRadiusPx(0)
                .build(),
            onReceiveContentListener,
        )

        observeProgress(viewModel) { progress -> progressMessage(progress) }
        setupFlows()
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, exitAndSyncBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        onBackPressedDispatcher.addCallback(this, closeFloatingActionBarBackPressCallback)
        super.setupBackPressedCallbacks()
    }

    override fun fitsSystemWindows(): Boolean = false

    /** Raises the FAB by half the 'Studied X cards' line, so it clears the line's text */
    private fun raiseFabAboveSummary(summaryHeight: Int) {
        val fabBottomMargin = summaryHeight / 2
        val layoutParams = floatingActionButtonBinding.fabLinearLayout.layoutParams as MarginLayoutParams
        if (layoutParams.bottomMargin != fabBottomMargin) {
            layoutParams.bottomMargin = fabBottomMargin
            floatingActionButtonBinding.fabLinearLayout.layoutParams = layoutParams
        }
    }

    /** Applied edge-to-edge insets for the screen */
    private fun setupEdgeToEdge() {
        /**
         * The view the deck list rests above: the FAB, or the 'Studied X cards' line while the
         * FAB is hidden (searching)
         */
        fun listAnchor(): View =
            if (floatingActionButtonBinding.fabMain.isVisible) {
                floatingActionButtonBinding.fabMain
            } else {
                deckPickerBinding.reviewSummaryTextView
            }

        fun setRecyclerViewBottomPaddingAbove(target: View) {
            val recyclerView = deckPickerBinding.decks
            if (recyclerView.height == 0 || target.height == 0) return
            val bottom = recyclerView.locationInWindow().y + recyclerView.height
            val topOfTarget = (target.layoutParams as? MarginLayoutParams)?.topMargin ?: 0
            recyclerView.updatePadding(
                bottom = (bottom - target.locationInWindow().y - topOfTarget).coerceAtLeast(0),
            )
        }

        deckPickerBinding.decksFadeWrapper.setup(window)
        deckPickerBinding.decksFadeWrapper.anchorView = deckPickerBinding.reviewSummaryTextView
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { toolbar, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            toolbar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        // Bottom padding is used. wrap_content meant margin wasn't viable
        ViewCompat.setOnApplyWindowInsetsListener(deckPickerBinding.root) { deckPickerInclude, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            // edge-to-edge disables adjustResize: the keyboard is cleared via the ime inset
            val withKeyboard = insets.getInsets(systemBars() or displayCutout() or ime())
            // BottomFadeFrameLayout handles contrast for bottom nav; let the system draw
            // its own scrim when the nav bar is on the side (no manual fade applies there)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val nav = insets.getInsets(navigationBars())
                window.isNavigationBarContrastEnforced = nav.left > 0 || nav.right > 0
            }
            val isRtl = deckPickerInclude.isRtl()
            deckPickerInclude.updatePadding(
                left = if (isRtl && fragmented) 0 else bars.left,
                right = if (!isRtl && fragmented) 0 else bars.right,
            )
            deckPickerBinding.reviewSummaryTextView.updatePadding(bottom = withKeyboard.bottom)

            val bottomNavOffset =
                if (binding.bottomNavigation?.let { it.isVisible && HideViewOnScrollBehavior.from(it).isScrolledIn } == true) {
                    BOTTOM_NAV_HEIGHT_DP.dp.toPx(this)
                } else {
                    0
                }
            // the keyboard covers the bottom navigation: clear whichever is taller
            floatingActionButtonBinding.root.updatePadding(
                bottom = maxOf(bars.bottom + bottomNavOffset, withKeyboard.bottom),
            )

            insets
        }
        // Insets move the FAB's ancestors without changing the FAB's bounds within its parent.
        // Wait until the whole hierarchy is laid out before reading positions in the window.
        deckPickerBinding.root.onGlobalLayout(this) {
            setRecyclerViewBottomPaddingAbove(listAnchor())
        }
        deckPickerBinding.reviewSummaryTextView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            // exclude paddingBottom: it holds the edge-to-edge inset, which is already applied
            raiseFabAboveSummary(view.height - view.paddingBottom)
        }
        // The summary is hidden until the collection loads.
        // Assume the summary takes up a single line, so it does not 'jump' up on load
        deckPickerBinding.reviewSummaryTextView.apply {
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            raiseFabAboveSummary(measuredHeight - paddingBottom)
        }
        if (fragmented) {
            val studyoptionsView = binding.studyoptionsFragment ?: return
            ViewCompat.setOnApplyWindowInsetsListener(studyoptionsView) { _, insets ->
                val bars = insets.getInsets(systemBars() or displayCutout())
                // the toolbar clears the top and the deck list clears the side touching the divider:
                // the panel's fragments apply the remaining insets themselves, so their scrolled content
                // renders underneath the navigation bar
                val isRtl = studyoptionsView.isRtl()
                insets.inset(
                    if (isRtl) 0 else bars.left,
                    bars.top,
                    if (isRtl) bars.right else 0,
                    0,
                )
            }
        }
    }

    private fun setupPullToSync() {
        pullToSyncWrapper =
            deckPickerBinding.pullToSyncWrapper.apply {
                setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
                setOnRefreshListener {
                    Timber.i("Pull to Sync: Syncing")
                    pullToSyncWrapper.isRefreshing = false
                    sync()
                }
            }
        // Only allow pull-to-sync when the deck list is scrolled to the top.
        deckPickerBinding.decks.doOnScrolled { _, _ ->
            pullToSyncWrapper.isEnabled = decksLayoutManager.findFirstCompletelyVisibleItemPosition() == 0
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        fun onDeckDeleted(result: DeckDeletionResult) {
            floatingActionButtonBinding.fabMain.isVisible = true
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(CommonString.undo) { undo() }
            }
        }

        fun onCardsEmptied(result: EmptyCardsResult) {
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(CommonString.undo) { undo() }
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
            if (supportFragmentManager.findFragmentByTag(SchedulerUpgradeDialog.TAG) == null) {
                SchedulerUpgradeDialog().showNow(supportFragmentManager, SchedulerUpgradeDialog.TAG)
            }
        }

        fun onOptionsMenuUpdated(unused: OptionsMenuState) = invalidateOptionsMenu()

        fun onStudiedTodayChanged(studiedToday: String) {
            deckPickerBinding.reviewSummaryTextView.text = studiedToday
        }

        fun onCollectionStatusChanged(isInInitialState: Boolean) {
            // no summary line in the initial state: the FAB rests unraised
            val summary = deckPickerBinding.reviewSummaryTextView
            if (isInInitialState) {
                raiseFabAboveSummary(summaryHeight = 0)
            } else if (summary.height > 0) {
                // re-showing the summary with unchanged text does not lay it out again,
                // so restore the FAB from the bounds the summary keeps while hidden
                raiseFabAboveSummary(summary.height - summary.paddingBottom)
            }
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
                subtitle = if (dueCount == 0) null else resources.getQuantityString(CommonPlurals.widget_cards_due, dueCount, dueCount)
                val toolbar = findViewById<Toolbar>(R.id.toolbar)
                TooltipCompat.setTooltipText(toolbar, toolbar.subtitle)
            }
        }

        fun onStudyOptionsVisibilityChanged(collectionHasNoCards: Boolean) {
            invalidateOptionsMenu()
            binding.studyoptionsFrame?.isVisible = !collectionHasNoCards
        }

        fun scrollToFocusedDeck() {
            val deckId = viewModel.flowOfFocusedDeck.value ?: return
            // A new collector replays the selection after recreation. Let RecyclerView restore
            // its scroll position unless the selected deck actually changed.
            if (deckId == lastFocusedDeck) return
            if (deckListAdapter.currentList.isEmpty() || deckListAdapter.currentList != viewModel.flowOfDeckList.value.data) return
            val position = viewModel.findDeckPosition(deckId)

            // Skip centering if the deck is already on screen.
            // Scrolling during a tap animation causes deck labels to overlap on older devices.
            if (decksLayoutManager.positionIsVisible(position)) {
                lastFocusedDeck = deckId
                return
            }
            // Keep the scroll outside layout and the tap animation on older devices.
            deckPickerBinding.decks.postDelayed({
                if (viewModel.flowOfFocusedDeck.value != deckId || lastFocusedDeck == deckId) return@postDelayed
                decksLayoutManager.scrollToPositionWithOffset(position, deckPickerBinding.decks.height / 2)
                deckPickerBinding.decks.doOnNextLayout {
                    if (viewModel.flowOfFocusedDeck.value == deckId && decksLayoutManager.positionIsVisible(position)) {
                        lastFocusedDeck = deckId
                    }
                }
            }, 10)
        }

        fun onDeckListChanged(deckList: FlattenedDeckList) {
            // Filtering must not recreate the study options fragment: its menu invalidation closes search.
            deckListAdapter.submit(
                data = deckList.data,
                hasSubDecks = deckList.hasSubDecks,
                onListCommitted = {
                    // Restore the saved viewport before allowing a new selection to override it.
                    deckPickerBinding.decks.doOnNextLayout { scrollToFocusedDeck() }
                    deckPickerBinding.decks.requestLayout()
                },
            )
        }

        fun onFocusedDeckChanged(deckId: DeckId?) {
            // A fresh ViewModel has no focused deck until the collection is loaded.
            if (deckId == null) return
            // Returning to the previous deck is still a new selection if another scroll was pending.
            if (deckId != lastFocusedDeck) lastFocusedDeck = null
            tryShowStudyOptionsPanel()
            scrollToFocusedDeck()
        }

        fun onDecksReloaded(param: Unit) {
            hideProgressBar()
            // TODO: Refresh deck metadata in the reminder pane
            fragment?.refreshInterface()
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
                    invalidateOptionsMenu()
                    showStartupScreensAndDialogs(sharedPrefs(), 0)
                    showProfileLoadFailureIfAny()

                    if (tryShowStudyOptionsPanel()) {
                        ResizablePaneManager(
                            parentLayout = requireNotNull(binding.deckpickerXlView) { "deckpickerXlView" },
                            divider = requireNotNull(binding.resizingDivider) { "resizingDivider" },
                            startPane = deckPickerBinding.root,
                            endPane = requireNotNull(binding.studyoptionsFragment) { "studyoptionsFragment" },
                            sharedPrefs = uiConfigPrefs(),
                            startPaneWeightKey = PREF_DECK_PICKER_PANE_WEIGHT,
                            endPaneWeightKey = PREF_STUDY_OPTIONS_PANE_WEIGHT,
                            onResizingChanged = { isResizing -> fragment?.onResizingChanged(isResizing) },
                        )
                    }
                }
                is StartupResponse.FatalError -> {
                    // the startup check is asynchronous, so the menu may already have been
                    // built by the time a failure arrives; rebuild it so onCreateOptionsMenu
                    // sees the error and blanks it
                    invalidateOptionsMenu()
                    if (showProfileLoadFailureIfAny()) {
                        Timber.i("Showing the startup failure once the profile load failure is closed")
                    } else {
                        handleStartupFailure(response.failure)
                    }
                }
            }
        }

        viewModel.deckDeletedNotification.collectInLifecycle(::onDeckDeleted)
        viewModel.flowOfDeleteDeckConfirmation.collectInLifecycle(::showDeleteDeckConfirmationDialog)
        viewModel.flowOfRenameDeck.collectInLifecycle(::renameDeckDialog)
        viewModel.flowOfStudyDeck.collectInLifecycle { deckId ->
            launchCatchingTask {
                handleDeckSelection(deckId, DeckSelectionType.SKIP_STUDY_OPTIONS)
            }
        }
        viewModel.emptyCardsNotification.collectInLifecycle(::onCardsEmptied)
        viewModel.flowOfDeckCountsChanged.collectInLifecycle(::onDeckCountsChanged)
        viewModel.flowOfDestination.collectInLifecycle(::onDestinationChanged)
        viewModel.flowOfNavigate.collectInLifecycle { navigate(it) }
        viewModel.flowOfExportDeck.collectInLifecycle(::onExportDeck)
        viewModel.flowOfCreateShortcut.collectInLifecycle(::createIcon)
        viewModel.flowOfDisableShortcuts.collectInLifecycle(::disableDeckAndChildrenShortcuts)
        viewModel.onError.receiveAsFlow().collectInLifecycle { showException(it) }
        viewModel.flowOfPromptUserToUpdateScheduler.collectInLifecycle(::onPromptUserToUpdateScheduler)
        viewModel.flowOfOptionsMenuState.filterNotNull().collectInLifecycle(::onOptionsMenuUpdated)
        viewModel.flowOfStudiedTodayStats.collectInLifecycle(::onStudiedTodayChanged)
        viewModel.flowOfDeckListInInitialState.filterNotNull().collectInLifecycle(::onCollectionStatusChanged)
        viewModel.flowOfCardsDue.collectInLifecycle(::onCardsDueChanged)
        viewModel.flowOfCollectionHasNoCards.collectInLifecycle(::onStudyOptionsVisibilityChanged)
        viewModel.flowOfDeckList.collectInLifecycle(::onDeckListChanged)
        viewModel.flowOfFocusedDeck.collectInLifecycle(::onFocusedDeckChanged)
        viewModel.flowOfResizingDividerVisible.collectInLifecycle(::onResizingDividerVisibilityChanged)
        viewModel.flowOfDecksReloaded.collectInLifecycle(::onDecksReloaded)
        viewModel.flowOfStartupResponse.filterNotNull().collectInLifecycle(::onStartupResponse)
        viewModel.flowOfShowContextMenu.collectInLifecycle(::showDeckPickerContextMenu)
        viewModel.flowOfShowRightClickContextMenu.collectInLifecycle(::showDeckPickerRightClickContextMenu)
        // navigation should be done on RESUMED
        importViewModel.importAddFlow.filterNotNull().collectInLifecycle(Lifecycle.State.RESUMED) {
            importViewModel.takeImportAdd()?.let(::importAdd)
        }
        importViewModel.importReplaceFlow.filterNotNull().collectInLifecycle(Lifecycle.State.RESUMED) {
            importViewModel.takeImportReplace()?.let(::importReplace)
        }
    }

    private val onReceiveContentListener =
        OnReceiveContentListener { _, payload ->
            val (uriContent, remaining) = payload.partition { item -> item.uri != null }

            val clip = uriContent?.clip ?: return@OnReceiveContentListener remaining
            val uri = clip.getItemAt(0).uri
            val importJob = onSelectedFileToImport(Intent().setData(uri))
            compat.retainDropPermissions(payload, importJob)

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
                viewModel.rebuildFilteredDeck(deckId)
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
            DeckPickerContextMenuOption.TIME_BUDGETS -> {
                Timber.i("ContextMenu: RWKV time budgets selected")
                dismissAllDialogFragments()
                showTimeBudgetDialog {
                    fragment?.refreshInterface()
                    updateDeckList()
                }
            }
            DeckPickerContextMenuOption.SCHEDULE_REMINDERS -> {
                Timber.i("Scheduling review reminders for deck '%d'", deckId)
                dismissAllDialogFragments()
                openScheduleReminders(deckId)
            }
        }
    }

    /**
     * @see DeckPickerViewModel.handleStartup
     */
    private fun handleStartup() {
        val context = appContext

        val environment: AnkiDroidEnvironment =
            object : AnkiDroidEnvironment {
                private val permissions = selectStoragePermissions(context)

                override fun hasRequiredPermissions(): Boolean = permissions.hasRequiredPermissions(context)

                override val requiredPermissions: StoragePermissionSet
                    get() = permissions

                override val preferences: SharedPreferences
                    get() = context.sharedPrefs()

                override fun decideStorageIfUndecided() = InitialActivity.decideStorageIfUndecided(context)

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
            is StorageUndecided -> {
                // Undecided requires PREF_COLLECTION_PATH to be unset: only expected if
                // choosing a default collection path failed (SystemStorageException) after
                // the permission checks passed
                // TODO: #19552 - replace with the storage setup flow
                Timber.w("storage setup flow (#19552) not implemented; showing load-failure options")
                CrashReportService.sendExceptionReport(
                    IllegalStateException("StorageUndecided reached without a startup failure"),
                    "DeckPicker::handleStartupFailure",
                )
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
            }
        }
    }

    private fun showDirectoryNotAccessibleDialog() {
        val contentView =
            TextView(this).apply {
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
                text =
                    getString(
                        CommonString.directory_inaccessible_info,
                        getString(R.string.link_full_storage_access),
                    )
            }
        AlertDialog.Builder(this).show {
            title(CommonString.directory_inaccessible)
            customView(
                contentView,
                paddingTop = 16.dp.toPx(this@DeckPicker),
                paddingStart = 32.dp.toPx(this@DeckPicker),
                paddingEnd = 32.dp.toPx(this@DeckPicker),
            )
            positiveButton(CommonString.open_settings) {
                requestPathUpdateLauncher.navigate(PreferencesDestination.Advanced)
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
            showBackgroundImageError(result.message(this))
        }
        val drawable = (result as? BackgroundImage.ResolveResult.Ready)?.drawable
        val applied =
            deckPickerBinding.background.setImageDrawableSafe(drawable) {
                showBackgroundImageError(getString(CommonString.background_image_too_large))
            }
        // activityHasBackground calls notifyDataSetChanged, ensure only 1 call
        deckListAdapter.activityHasBackground = applied && drawable != null
    }

    private fun showBackgroundImageError(message: String) {
        if (backgroundImageErrorShown) return
        backgroundImageErrorShown = true
        showThemedToast(this, message, shortLength = false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewModel.flowOfStartupResponse.value is StartupResponse.FatalError) {
            return false
        }

        Timber.d("onCreateOptionsMenu()")
        if (isDrawerOpen) {
            floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.LONG)
        }
        // Fragments own their menus: each fragment registers a MenuProvider against this
        // activity (see StudyOptionsFragment), and the menu host dispatches creation,
        // preparation and selection to them. This activity never drives a fragment's menu.
        menuInflater.inflate(R.menu.deck_picker, menu)
        menu.findItem(R.id.deck_picker_action_filter)?.let {
            toolbarSearchItem = it
            setupSearchIcon(it)
            toolbarSearchView = it.actionView as AccessibleSearchView
        }
        toolbarSearchView?.maxWidth = Integer.MAX_VALUE

        menu.findItem(R.id.action_export_collection)?.title = TR.actionsExport()
        menu.findItem(R.id.action_import)?.title = TR.actionsImport()
        menu.findItem(R.id.action_check_database)?.title = TR.sentenceCase.checkDatabase
        menu.findItem(R.id.action_check_media)?.title = TR.sentenceCase.checkMediaAction
        menu.findItem(R.id.action_empty_cards)?.title = TR.sentenceCase.emptyCards
        menu.findItem(R.id.action_deck_rename)?.title = TR.sentenceCase.renameDeck
        menu.findItem(R.id.action_deck_delete)?.title = TR.sentenceCase.deleteDeck
        setupMediaSyncMenuItem(menu)
        // redraw menu synchronously to avoid flicker
        updateMenuFromState(menu)
        updateSearchVisibilityFromState(menu)
        restoreSearchState(menu)
        // ...then launch a task to possibly update the visible icons.
        // Store the job so that tests can easily await it. In the future
        // this may be better done by injecting a custom test scheduler
        // into CollectionManager, and awaiting that.
        createMenuJob =
            launchCatchingTask {
                viewModel.refreshMenuState()
                updateSearchVisibilityFromState(menu)
                restoreSearchState(menu)
                updateDeckRelatedMenuItems(menu)
                updateMenuFromState(menu)
            }
        return super.onCreateOptionsMenu(menu)
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
                    viewModel.updateDeckSearchExpanded(true)
                    // Hide the floating action button if it is visible
                    floatingActionMenu.hideFloatingActionButton()
                    activeSnackBar?.anchorView = null
                    return true
                }

                // When SearchItem is collapsed
                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    Timber.i("DeckPicker:: SearchItem closed")
                    viewModel.updateDeckSearchExpanded(false)
                    viewModel.updateDeckFilter("")
                    // Show the floating action button if it is hidden
                    floatingActionMenu.showFloatingActionButton()
                    activeSnackBar?.anchorView = floatingActionButtonBinding.fabMain
                    return true
                }
            },
        )

        val searchView = menuItem.actionView as AccessibleSearchView
        searchView.run {
            queryHint = getString(CommonString.search_decks)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        if (newText.isNotEmpty()) {
                            viewModel.updateDeckFilter(newText)
                        } else {
                            // Ignore empty callbacks from a replaced action view.
                            searchView.post {
                                if (
                                    !isDestroyed &&
                                    toolbarSearchView === searchView &&
                                    menuItem.isActionViewExpanded &&
                                    searchView.query.isEmpty()
                                ) {
                                    viewModel.updateDeckFilter("")
                                }
                            }
                        }
                        return true
                    }
                },
            )
        }
        searchDecksIcon = menuItem
    }

    private fun restoreSearchState(menu: Menu) {
        val searchItem = menu.findItem(R.id.deck_picker_action_filter)
        if (viewModel.isDeckSearchExpanded && searchItem.isVisible) {
            val searchView = searchItem.actionView as AccessibleSearchView
            val query = viewModel.deckSearchQuery
            // Expanding the action view clears its text, so restore the query afterwards.
            searchItem.expandActionView()
            searchView.setQuery(query, false)
        }
    }

    fun updateMenuFromState(menu: Menu) {
        viewModel.optionsMenuState?.run {
            // The menu may have been cleared while refreshMenuState was suspended.
            menu.findItem(R.id.action_undo)?.let { updateUndoLabelFromState(it, undoLabel, undoAvailable) }
            menu.findItem(R.id.action_sync)?.let { updateSyncIconFromState(it, this) }
        }
        updateDeckRelatedMenuItems(menu)
    }

    /**
     * Shows/hides deck related menu items using the ViewModel's action eligibility.
     */
    private fun updateDeckRelatedMenuItems(menu: Menu) {
        viewModel.optionsMenuState?.run {
            menu.findItem(R.id.action_deck_rename)?.isVisible = showDeckActions
            menu.findItem(R.id.action_deck_delete)?.isVisible = showDeckActions
            // added to the menu by StudyOptionsFragment
            menu.findItem(R.id.action_deck_or_study_options)?.isVisible = showDeckActions
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
                SyncIconState.Normal, SyncIconState.PendingChanges -> CommonString.button_sync
                SyncIconState.OneWay -> CommonString.sync_menu_title_one_way_sync
                SyncIconState.NotLoggedIn -> CommonString.sync_menu_title_no_account
            }
        provider.setTooltipText(getString(tooltipText))
        when (state.syncIcon) {
            SyncIconState.Normal -> {
                BadgeDrawableBuilder.removeBadge(provider)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(this)
                    .withColorAttr(CommonR.attr.badgeWarningColor)
                    .replaceBadge(provider)
            }
            SyncIconState.OneWay, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder(this)
                    .withText('!')
                    .withColorAttr(CommonR.attr.badgeErrorColor)
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
            R.id.action_time_budgets -> {
                Timber.i("DeckPicker:: RWKV time budgets selected")
                showTimeBudgetDialog {
                    fragment?.refreshInterface()
                    updateDeckList()
                }
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
                viewModel.requestRenameSelectedDeck()
                return true
            }
            R.id.action_deck_delete -> {
                deleteSelectedDeck()
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun createBackup(): Job =
        launchCatchingTask {
            val created = viewModel.createBackup()
            val message = if (created) TR.profilesBackupCreated() else TR.profilesBackupUnchanged()
            showThemedToast(this@DeckPicker, message, false)
        }

    private fun showMediaCheckDialog() {
        Timber.i("showing media check dialog")
        DeckPickerMaintenanceDialog.newInstance(Check.MEDIA).show(supportFragmentManager, DeckPickerMaintenanceDialog.TAG)
    }

    fun showCreateFilteredDeckDialog() {
        startActivity(FilteredDeckOptionsFragment.getIntent(this))
    }

    fun exportCollection() {
        ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
    }

    private fun processReviewResults(resultCode: Int) {
        if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
            launchCatchingTask {
                val state = withCol { currentDeckStudyState() }
                StudyCompletionUi.onReviewsCompleted(this@DeckPicker, state)
            }
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
        viewModel.onScreenResumed()
        message?.let { dialogHandler.sendStoredMessage(it) }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        showDeferredMeteredSyncConfirmation()
    }

    fun refreshState() {
        // Due to the App Introduction, this may be called before permission has been granted.
        if (syncOnResume && hasCollectionStoragePermissions()) {
            syncOnResume = false
            intent.removeExtra(EXTRA_SYNC_FROM_LOGIN)
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
        lastFocusedDeck?.let { outState.putLong(STATE_LAST_FOCUSED_DECK, it) }
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.putString("dbRestorationPath", it.newAnkiDroidDirectory.absolutePath)
            }
        }
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
        outState.putSerializable("mediaNetworkTypeOnConflict", mediaNetworkTypeOnConflict)
        floatingActionMenu.showFloatingActionButton()
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            val path = File(path)
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
        mediaNetworkTypeOnConflict =
            savedInstanceState.getSerializableCompat<NetworkType>("mediaNetworkTypeOnConflict") ?: NetworkType.UNMETERED
        showRestoredBottomNavTab()
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
        if (hasPendingMeteredSyncConfirmation()) {
            Timber.d("autoSync: waiting for metered sync confirmation")
            return false
        }
        val action = viewModel.automaticSyncAction(runInBackground)
        // A manual request may have opened the confirmation during the status check.
        if (hasPendingMeteredSyncConfirmation()) {
            Timber.d("autoSync: waiting for metered sync confirmation")
            return false
        }
        when (action) {
            AutomaticSyncAction.NONE -> return false
            AutomaticSyncAction.MEDIA_ONLY -> {
                if (shouldFetchMedia()) {
                    val auth = syncAuth() ?: return false
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }
            AutomaticSyncAction.COLLECTION -> {
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

    /** Where the current downward drag started: reset whenever the finger moves up */
    private var touchAnchorY = 0f
    private var lastTouchY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Content which fits once the bottom bar hides can no longer scroll, so scrolling alone
        // could never bring the bar back: a downward drag always restores it.
        if (bottomNavigationEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchAnchorY = event.y
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.y < lastTouchY) {
                        touchAnchorY = event.y
                    } else if (event.y - touchAnchorY > ViewConfiguration.get(this).scaledTouchSlop) {
                        binding.bottomNavigation?.let { HideViewOnScrollBehavior.from(it).slideIn(it) }
                    }
                    lastTouchY = event.y
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!bottomNavigationEnabled || event.action != KeyEvent.ACTION_DOWN || !event.isAltPressed) {
            return super.dispatchKeyEvent(event)
        }

        val bottomNavigation = binding.bottomNavigation ?: return super.dispatchKeyEvent(event)
        val destination =
            when (event.keyCode) {
                KeyEvent.KEYCODE_1 -> NavigationItem.HOME
                KeyEvent.KEYCODE_2 -> NavigationItem.BROWSER
                KeyEvent.KEYCODE_3 -> NavigationItem.STATS
                KeyEvent.KEYCODE_4 -> NavigationItem.MORE
                else -> return super.dispatchKeyEvent(event)
            }
        bottomNavigation.selectTab(destination)
        return true
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (selectedBottomNavItem() == NavigationItem.BROWSER && window.currentFocus?.onCheckIsTextEditor() == true) {
            return super.onKeyUp(keyCode, event)
        }
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
                viewModel.requestStudySelectedDeck()
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
                        deleteSelectedDeck()
                    } else {
                        // Shortcut: DEL
                        Timber.i("Delete Deck from keypress")
                        viewModel.requestDeleteDeckConfirmation()
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
                    viewModel.requestRenameSelectedDeck()
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
    private fun showDeleteDeckConfirmationDialog(request: DeckPickerViewModel.DeleteDeckConfirmationRequest) {
        val confirmDeleteDeckDialog =
            DeckPickerConfirmDeleteDeckDialog.newInstance(
                deckName = request.deckName,
                deckId = request.deckId,
                totalCards = request.totalCards,
                isFilteredDeck = request.isFilteredDeck,
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

    private fun showProfileLoadFailureIfAny(): Boolean {
        val failure = ProfileManager.consumeLoadFailure()
        if (supportFragmentManager.findFragmentByTag(ProfileLoadFailureDialog.TAG) != null) return true
        if (failure == null) return false
        Timber.i("Showing the profile load failure")
        ProfileLoadFailureDialog.newInstance(failure).show(supportFragmentManager, ProfileLoadFailureDialog.TAG)
        return true
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
            DeckPickerNoSpaceLeftDialog.show(this)
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            Timber.i("No space left")
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance())
            preferences.edit { remove("noSpaceLeft") }
        } else if (InitialActivity.performSetupFromFreshInstallOrClearedPreferences(preferences)) {
            onFinishedStartup()
        } else if (skip < 2 && !InitialActivity.isLatestVersion(preferences)) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")
            // The user might appreciate us now, see if they will help us get better?
            if (!preferences.contains(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN)) {
                displayAnalyticsOptInDialog()
            }

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.pkgVersionCode
            Timber.i("Current AnkiDroid version: %s", current)
            val previous: Long =
                if (preferences.contains(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)) {
                    // Upgrading currently installed app
                    viewModel.getPreviousVersion(preferences, current)
                } else {
                    // Fresh install
                    current
                }
            preferences.edit { putLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, current) }

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
                showNewVersionInfoLauncher.navigate(ChangelogDestination)
            } else {
                Timber.i("Dev Build - not showing 'new features'")
                // Don't show new features dialog for development builds
                InitialActivity.setUpgradedToLatestVersion(preferences)
                val ver = resources.getString(CommonString.updated_version, VersionUtils.pkgVersionName)
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
        DeckPickerAnalyticsOptInDialog.show(this)
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
        showAsyncDialogFragment(newFragment, NotificationChannel.SYNC)
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        CrashReportService.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        // TODO: doesn't work on null collection-only on non-openable(is this still relevant with withCol?)
        launchCatchingTask(resources.getString(CommonString.deck_repair_error)) {
            Timber.d("doInBackgroundRepairCollection")
            val result =
                withProgress(resources.getString(CommonString.backup_repair_deck_progress)) {
                    Timber.i("RepairCollection: Closing collection")
                    CollectionManager.ensureClosed()
                    val colFile =
                        CollectionManager.collectionPathInValidFolder().requireDiskBasedCollection().colDb
                    BackupManager.repairCollection(colFile)
                }
            if (!result) {
                showThemedToast(this@DeckPicker, resources.getString(CommonString.deck_repair_error), true)
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
            DeckPickerMaintenanceDialog
                .newInstance(Check.DATABASE, status.getWarningDetails(this))
                .show(supportFragmentManager, DeckPickerMaintenanceDialog.TAG)
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

    // TODO: Move both conflict fields into pending full-sync context carried by dialog arguments,
    //  confirmation callbacks, and deferred dialog messages.

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
     it after the user has decided. */
    var mediaUsnOnConflict: Int? = null

    /** Preserve the original attempt's network restriction while the user chooses upload/download. */
    var mediaNetworkTypeOnConflict: NetworkType = NetworkType.UNMETERED

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

        // Preference or network changes must not bypass an unanswered confirmation.
        if (conflict == null && hasPendingMeteredSyncConfirmation()) {
            refreshState()
            return
        }

        val syncMedia = shouldFetchMedia()
        MeteredSyncPolicy.confirmThen(
            // After selecting 'upload/download', the user has already accepted the metered warning.
            skipPrompt = conflict != null,
            onConfirmationRequired = {
                viewModel.deferredMeteredSyncMedia = syncMedia
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    showDeferredMeteredSyncConfirmation()
                }
                // Dismiss the deck-list loading indicator while the sync decision is pending.
                refreshState()
            },
        ) { permission ->
            handleNewSync(conflict, syncMedia, meteredSyncPermission = permission)
        }
    }

    private fun hasPendingMeteredSyncConfirmation(): Boolean =
        viewModel.deferredMeteredSyncMedia != null || supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG) != null

    private fun showDeferredMeteredSyncConfirmation() {
        val syncMedia = viewModel.deferredMeteredSyncMedia ?: return
        if (supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG) == null) {
            MeteredSyncDialog.newInstance(syncMedia).showNow(supportFragmentManager, MeteredSyncDialog.TAG)
        }
        viewModel.deferredMeteredSyncMedia = null
    }

    override fun loginToSyncServer() {
        val intent = AccountActivity.getIntent(this, forResult = true)
        loginForSyncLauncher.launch(intent)
    }

    // Callback to import a file -- adding it to existing collection
    fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        startActivity(AnkiPackageImporterFragment.getIntent(this, importPath))
    }

    // Callback to import a file -- replacing the existing collection
    fun importReplace(importPath: String) {
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

        // Only clear navigation when closing another side panel. Import dialogs can already
        // be on the back stack when startup finishes, and must survive panel initialization.
        val sidePanel = supportFragmentManager.findFragmentById(containerId)
        if (sidePanel != null && sidePanel !is StudyOptionsFragment) {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
        }

        val studyOptions = fragment
        if (studyOptions != null) {
            studyOptions.refreshInterface()
        } else {
            supportFragmentManager.commitNow {
                replace(containerId, StudyOptionsFragment())
            }
        }
        return true
    }

    @NeedsTest("Instrumented tests for review reminders")
    private fun tryShowScheduleRemindersPanel(deckId: DeckId): Boolean {
        val sidePanel = binding.studyoptionsFragment ?: return false
        if (!sidePanel.isVisible) return false
        val newFragment =
            ScheduleRemindersFragment.newInstance(
                scope = ReviewReminderScope.DeckSpecific(deckId),
                host = ScheduleRemindersFragment.FragmentHost.STUDY_OPTIONS_FRAGMENT,
            )
        supportFragmentManager.commit {
            replace(sidePanel.id, newFragment)
            addToBackStack(null)
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
                SdCardReceiver.ACTION_MEDIA_MOUNT
                    to { ActivityCompat.recreate(this) },
            )

    fun openAnkiWebSharedDecks() {
        if (!NetworkUtils.isOnline) {
            showSnackbar(CommonString.check_network)
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
        reviewLauncher.navigate(StudyOptionsDestination)
    }

    @NeedsTest("Instrumented tests for review reminders")
    private fun openScheduleReminders(deckId: DeckId) {
        if (tryShowScheduleRemindersPanel(deckId)) return

        // otherwise, we need to launch the activity
        Timber.i("Opening Schedule Reminders")
        viewModel.scheduleReviewReminders(deckId)
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
            showSnackbar(CommonString.empty_deck) {
                setAction(CommonString.menu_add) { viewModel.addNote(did, true) }
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

        val study =
            withCol {
                decks.select(did)
                currentDeckStudyState()
            }
        deckListAdapter.updateSelectedDeck(did)
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        viewModel.focusedDeck = did

        if (study.availability == StudyAvailability.CardsAvailable) {
            openReviewerOrStudyOptions(selectionType)
            return
        }

        if (study.isEmptyRegularDeck) {
            showEmptyDeckSnackbar()
            updateUi()
        } else {
            StudyCompletionUi.openCongratsPage(this)
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
                    with(DeferredNavigation) { ReviewDeckDestination.ExternalLaunch(shortcutData.deckId).toIntent() },
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
                showThemedToast(this, getString(CommonString.create_shortcut_error_vivo), false)
            }
            // #18601: MIUI gates shortcut creation behind Settings - Privacy Protection -
            // Other permissions - AnkiDroid - Home screen shortcuts [add shortcuts to Home screen]
            // TODO: Determine the result of 'success'
            if (AdaptionUtil.isMiui) {
                showThemedToast(this, getString(CommonString.create_shortcut_error_miui, getString(R.string.app_name)), false)
            }
            if (!success) {
                showThemedToast(this, getString(CommonString.create_shortcut_failed), false)
            }
        } catch (e: Exception) {
            Timber.w(e)
            showThemedToast(this, getString(CommonString.create_shortcut_error, e.localizedMessage), false)
        }
    }

    /** Disables the shortcut of the deck and the children belonging to it.*/
    @NeedsTest("ensure collapsed decks are also deleted")
    private fun disableDeckAndChildrenShortcuts(deckTreeDids: List<String>) {
        val errorMessage: CharSequence = getString(CommonString.deck_shortcut_doesnt_exist)
        ShortcutUtils.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        launchCatchingTask {
            val currentName = withCol { decks.name(did) }
            val createDeckDialog =
                CreateDeckDialog(
                    context = this@DeckPicker,
                    title = TR.sentenceCase.renameDeck,
                    deckDialogType = CreateDeckDialog.DeckDialogType.RENAME_DECK,
                    parentId = null,
                    renamedDeckId = did,
                )
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
        val createDeckDialog =
            CreateDeckDialog(
                context = this@DeckPicker,
                title = TR.sentenceCase.createDeck,
                deckDialogType = CreateDeckDialog.DeckDialogType.DECK,
                parentId = null,
            )
        createDeckDialog.onNewDeckCreated = ::onDeckCreated
        createDeckDialog.showDialog()
    }

    /**
     * Handles a deck created from the deck picker: selects it so the toolbar and, on tablets, the
     * [StudyOptionsFragment] panel follow the new deck instead of the previously current deck, which
     * is often the hidden Default deck.
     */
    private fun onDeckCreated(deckId: DeckId) =
        launchCatchingTask {
            viewModel.selectDeck(deckId).join()
            updateDeckList()
            tryShowStudyOptionsPanel()
            invalidateOptionsMenu()
        }

    /**
     * Deletes the provided deck, child decks, and all cards inside.
     * @param did ID of the deck to delete
     */
    fun deleteDeck(did: DeckId) =
        launchCatchingTask {
            viewModel.deleteDeck(did).join()
        }

    private fun deleteSelectedDeck() =
        launchCatchingTask {
            viewModel.deleteSelectedDeck().join()
        }

    private fun emptyFiltered(did: DeckId) {
        launchCatchingTask {
            viewModel.emptyFilteredDeck(did).join()
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
        reviewLauncher.navigate(ReviewDeckDestination.CurrentDeck)
    }

    private fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog =
            CreateDeckDialog(
                context = this@DeckPicker,
                title = getString(CommonString.create_subdeck),
                deckDialogType = CreateDeckDialog.DeckDialogType.SUB_DECK,
                parentId = did,
            )
        createDeckDialog.onNewDeckCreated = { deckId ->
            // a deck was created
            dismissAllDialogFragments()
            deckListAdapter.notifyDataSetChanged()
            onDeckCreated(deckId)
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
        get(): ShortcutGroup {
            fun bottomNavShortcut(
                keys: String,
                destination: NavigationItem,
            ) = if (bottomNavigationEnabled) shortcut(keys, destination.shortcutLabel) else null

            return ShortcutGroup(
                listOfNotNull(
                    shortcut("A", CommonString.menu_add_note),
                    bottomNavShortcut("Alt+1", NavigationItem.HOME),
                    bottomNavShortcut("Alt+2", NavigationItem.BROWSER),
                    bottomNavShortcut("Alt+3", NavigationItem.STATS),
                    bottomNavShortcut("Alt+4", NavigationItem.MORE),
                    shortcut("B", CommonString.card_browser_context_menu),
                    shortcut("Y", CommonString.pref_cat_sync),
                    shortcut("/", CommonString.deck_conf_cram_search),
                    shortcut("S", Translations::decksStudyDeck),
                    shortcut("T", CommonString.open_statistics),
                    shortcut("C") { this.sentenceCase.checkDatabase },
                    shortcut("D") { sentenceCase.createDeck },
                    shortcut("F", CommonString.new_dynamic_deck),
                    if (fragmented) shortcut("DEL") { this.sentenceCase.deleteDeck } else null,
                    if (fragmented) shortcut("Shift+DEL", CommonString.delete_deck_without_confirmation) else null,
                    if (fragmented) shortcut("R") { this.sentenceCase.renameDeck } else null,
                    shortcut("P", CommonString.open_settings),
                    shortcut("M") { this.sentenceCase.checkMediaAction },
                    shortcut("Ctrl+E", CommonString.export_collection),
                    shortcut("Ctrl+Shift+I", Translations::actionsImport),
                    shortcut("Ctrl+Shift+N", CommonString.model_browser_label),
                ),
                CommonString.deck_picker_group,
            )
        }

    companion object {
        /**
         * Material 3 BottomNavigationView content height in dp, *excluding* the system
         * navigation-bar inset (that inset is added separately here via `bars.bottom`).
         * Used to offset the FAB above the bar. The hosted-fragment container instead uses
         * the bar's measured height, which already includes the inset so the two must not
         * be swapped for one another.
         */
        private const val BOTTOM_NAV_HEIGHT_DP = 80

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
        const val EXTRA_SYNC_FROM_LOGIN = "syncFromLogin"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @VisibleForTesting
        const val REQUEST_STORAGE_PERMISSION = 0

        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400

        private const val PREF_DECK_PICKER_PANE_WEIGHT = "deckPickerPaneWeight"
        private const val PREF_STUDY_OPTIONS_PANE_WEIGHT = "studyOptionsPaneWeight"
        private const val STATE_LAST_FOCUSED_DECK = "lastFocusedDeck"

        /**
         * Builds an intent for [DeckPicker]
         */
        fun getIntent(
            context: Context,
            autoSync: Boolean = false,
        ) = Intent(context, DeckPicker::class.java).apply {
            if (autoSync) {
                putExtra(EXTRA_SYNC_FROM_LOGIN, true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.hasExtra(EXTRA_SYNC_FROM_LOGIN)) {
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

private fun Context.progressMessage(progress: DeckPickerProgress): String =
    when (progress) {
        DeckPickerProgress.DELETING_DECK -> getString(CommonString.delete_deck)
        DeckPickerProgress.REBUILDING_FILTERED_DECK -> getString(CommonString.rebuild_filtered_deck)
        DeckPickerProgress.DELETING_EMPTY_CARDS -> TR.emptyCardsDeleting()
        DeckPickerProgress.CREATING_BACKUP -> TR.sentenceCase.creatingBackup
    }
