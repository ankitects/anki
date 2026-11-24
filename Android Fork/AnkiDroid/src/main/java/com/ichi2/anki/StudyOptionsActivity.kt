// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.MenuItemCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsAddEditReminderHandler
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsStudyHandler
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.databinding.ActivityStudyOptionsBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.ui.RtlCompliantActionProvider
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Hosts [StudyOptionsFragment] when non-fragmented
 */
class StudyOptionsActivity :
    AnkiActivity(R.layout.activity_study_options),
    ChangeManager.Subscriber {
    private val binding by viewBinding(ActivityStudyOptionsBinding::bind)

    private var undoState = UndoState()

    init {
        ChangeManager.subscribe(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        if (!ensureStorageIsReady()) {
            return
        }
        enableToolbar().apply { title = "" }
        setupEdgeToEdge()
        if (savedInstanceState == null) {
            loadStudyOptionsFragment()
        }
        setResult(RESULT_OK)
        addMenuProvider(menuProvider)

        setFragmentResultListener(REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                ->
                    (currentFragment as? StudyOptionsFragment)?.refreshInterface()
            }
        }
        registerStudyOptionsStudyHandler {
            Timber.i("Opening study screen from study options screen")
            val reviewer = with(DeferredNavigation) { ReviewDeckDestination.CurrentDeck.toIntent() }
            // go back to DeckPicker after studying when not in tablet mode
            reviewer.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            startActivity(reviewer)
            finish()
        }
        registerStudyOptionsAddEditReminderHandler { did: DeckId ->
            Timber.i("Opening review reminders screen from study options screen")
            supportFragmentManager.commit {
                replace(
                    R.id.studyoptions_frame,
                    ScheduleRemindersFragment.newInstance(
                        scope = ReviewReminderScope.DeckSpecific(did),
                        host = ScheduleRemindersFragment.FragmentHost.STUDY_OPTIONS_FRAME,
                    ),
                )
                addToBackStack(null)
            }
            invalidateMenu()
        }
    }

    private val menuProvider: MenuProvider =
        object : MenuProvider {
            override fun onCreateMenu(
                menu: Menu,
                menuInflater: MenuInflater,
            ) {
                menuInflater.inflate(R.menu.activity_study_options, menu)
                val undoMenuItem = menu.findItem(R.id.action_undo)
                val undoActionProvider = MenuItemCompat.getActionProvider(undoMenuItem) as? RtlCompliantActionProvider
                undoActionProvider?.clickHandler = { _, menuItem -> onMenuItemSelected(menuItem) }
            }

            override fun onPrepareMenu(menu: Menu) {
                val undoMenuItem = menu.findItem(R.id.action_undo)
                // TODO: Ideally, the undo button should be owned by StudyOptionsFragment or be provided by a unified UndoMenuProvider
                // Checking the current fragment from the activity to decide whether the button should be visible is hacky; see #21339
                undoMenuItem.isVisible = undoState.hasAction && (currentFragment is StudyOptionsFragment)
                undoMenuItem.title = undoState.label
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean =
                when (item.itemId) {
                    R.id.action_undo -> {
                        Timber.i("Undoing last action from study options screen")
                        launchCatchingTask {
                            undoAndShowSnackbar()
                        }
                        true
                    }
                    else -> false
                }
        }

    /** Applies edge-to-edge insets for the screen */
    private fun setupEdgeToEdge() {
        // systemBars (not just statusBars) so a landscape 3-button navigation bar,
        // which is a side inset, is also cleared
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val bars =
                insets.getInsets(
                    systemBars() or displayCutout(),
                )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.studyoptionsFrame) { _, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            // the toolbar clears the top: the hosted fragments apply the remaining insets
            // themselves, so their scrolled content renders underneath the navigation bar
            insets.inset(0, bars.top, 0, 0)
        }
    }

    private fun loadStudyOptionsFragment() {
        val currentFragment = StudyOptionsFragment()
        supportFragmentManager.commit {
            replace(R.id.studyoptions_frame, currentFragment)
        }
    }

    private val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_frame)

    override fun onResume() {
        super.onResume()
        refreshUndoState()
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        refreshUndoState()
    }

    private fun refreshUndoState() {
        launchCatchingTask {
            val newUndoState =
                withCol {
                    UndoState(
                        hasAction = undoAvailable(),
                        label = undoLabel(),
                    )
                }
            if (undoState != newUndoState) {
                undoState = newUndoState
                invalidateMenu()
            }
        }
    }

    private data class UndoState(
        val hasAction: Boolean = false,
        val label: String? = null,
    )
}
