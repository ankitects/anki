/*
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.ui.RtlCompliantActionProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hosts [StudyOptionsFragment] when non-fragmented
 */
class StudyOptionsActivity :
    AnkiActivity(R.layout.activity_study_options),
    ChangeManager.Subscriber {
    private var undoState = UndoState()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableToolbar().apply { title = "" }
        if (savedInstanceState == null) {
            loadStudyOptionsFragment()
        }
        setResult(RESULT_OK)

        setFragmentResultListener(REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                ->
                    currentFragment!!.refreshInterface()
            }
        }
        setFragmentResultListener(StudyOptionsFragment.REQUEST_STUDY_OPTIONS_STUDY) { _, _ ->
            Timber.d("Opening study screen from study options screen")
            val reviewer = Reviewer.getIntent(this)
            // go back to DeckPicker after studying when not in tablet mode
            reviewer.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            startActivity(reviewer)
            finish()
        }
    }

    private fun loadStudyOptionsFragment() {
        val currentFragment = StudyOptionsFragment()
        supportFragmentManager.commit {
            replace(R.id.studyoptions_frame, currentFragment)
        }
    }

    private val currentFragment: StudyOptionsFragment?
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_frame) as StudyOptionsFragment?

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_study_options, menu)
        val undoMenuItem = menu.findItem(R.id.action_undo)
        val undoActionProvider = MenuItemCompat.getActionProvider(undoMenuItem) as? RtlCompliantActionProvider
        // Set the proper click target for the undo button's ActionProvider
        undoActionProvider?.clickHandler = { _, menuItem -> onOptionsItemSelected(menuItem) }
        undoMenuItem.isVisible = undoState.hasAction
        undoMenuItem.title = undoState.label
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_undo -> {
                launchCatchingTask {
                    undoAndShowSnackbar()
                    // TODO why are we going to the Reviewer from here? Desktop doesn't do this
                    Reviewer
                        .getIntent(this@StudyOptionsActivity)
                        .apply { flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT }
                        .also { startActivity(it) }
                    finish()
                }
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUndoState()
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        refreshUndoState()
        currentFragment?.refreshInterface()
    }

    private fun refreshUndoState() {
        lifecycleScope.launch {
            val newUndoState =
                withCol {
                    UndoState(
                        hasAction = undoAvailable(),
                        label = undoLabel(),
                    )
                }
            if (undoState != newUndoState) {
                undoState = newUndoState
                invalidateOptionsMenu()
            }
        }
    }

    private data class UndoState(
        val hasAction: Boolean = false,
        val label: String? = null,
    )
}
