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
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.ichi2.anki.SingleFragmentActivity.Companion.getIntent
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.startup.ensureStoragePermissions
import com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Activity aimed to host a fragment on the entire screen.
 * For that, it uses [R.layout.activity_single_fragment], which has only a [FragmentContainerView]
 *
 * Useful to avoid creating a Activity for every new screen
 * while being able to reuse the fragment on other places.
 *
 * [getIntent] can be used as an easy way to build a [SingleFragmentActivity]
 *
 * See also: [ConfigAwareSingleFragmentActivity]
 */
open class SingleFragmentActivity :
    AnkiActivity(R.layout.activity_single_fragment),
    BaseSnackbarBuilderProvider {
    // delegate to the fragment in all cases
    override val baseSnackbarBuilder: SnackbarBuilder
        get() = (fragment as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder ?: { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }
        setTransparentStatusBar()

        // avoid recreating the fragment on configuration changes
        // the fragment should handle state restoration
        if (savedInstanceState != null) {
            return
        }
        val assignedFragment = intent.getStringExtra(FRAGMENT_NAME_EXTRA)
        // One of the activities inheriting this activity is ManageSpaceActivity which is started
        // only by the system. When we encounter this activity we need to assign it here the fragment
        // it expects, which is ManageSpaceFragment
        val fragmentClassName =
            if (assignedFragment == null && this is ManageSpaceActivity) {
                // the IDE updates this when moving ManageSpaceFragment
                "com.ichi2.anki.ui.windows.managespace.ManageSpaceFragment"
            } else {
                requireNotNull(assignedFragment) { "'$FRAGMENT_NAME_EXTRA' extra should be provided" }
            }

        Timber.d("Creating fragment %s", fragmentClassName)

        val fragment =
            FragmentFactoryUtils.instantiate<Fragment>(this, fragmentClassName).apply {
                arguments = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
            }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG)
        }

        setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                ->
                    openStudyOptionsAndFinish()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)!!
        return if (fragment is DispatchKeyEventListener) {
            fragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    /** Reference to the hosted fragment */
    val fragment
        get() = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)

    override val shortcuts: ShortcutGroup?
        get() = (fragment as? ShortcutGroupProvider)?.shortcuts

    companion object {
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_TAG = "SingleFragmentActivityTag"

        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent =
            Intent(context, SingleFragmentActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
                action = intentAction
            }
    }

    // Begin - implementation of CustomStudyListener methods here for crash fix
    // TODO - refactor https://github.com/ankidroid/Anki-Android/pull/17508#pullrequestreview-2465561993
    private fun openStudyOptionsAndFinish() {
        val intent =
            Intent(this, StudyOptionsActivity::class.java).apply {
                putExtra("withDeckOptions", false)
            }
        startActivity(intent, null)
        this.finish()
    }

    // END CustomStudyListener temporary implementation - should refactor out
}

interface DispatchKeyEventListener {
    fun dispatchKeyEvent(event: KeyEvent): Boolean
}
