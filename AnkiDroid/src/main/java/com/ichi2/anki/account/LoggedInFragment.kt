/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.account

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.pages.RemoveAccountFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.isCompactWidth
import com.ichi2.anki.utils.ext.removeFragmentFromContainer
import com.ichi2.anki.utils.ext.showDialogFragment
import timber.log.Timber

class LoggedInFragment : Fragment(R.layout.fragment_my_account_logged_in) {
    // if the 'remove account' fragment is open, close it first
    private val onRemoveAccountBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                closeRemoveAccountScreen()
            }
        }

    private val viewModel: LoggedInViewModel by viewModels()

    private lateinit var loggedInLogo: ImageView

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: MaterialToolbar = view.findViewById(R.id.toolbar)
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)

        activity.supportActionBar?.apply {
            title = TR.preferencesAccount().toSentenceCase(R.string.sync_account)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<TextView>(R.id.username_logged_in).text = Prefs.username

        view.findViewById<Button>(R.id.privacy_policy_button).setOnClickListener { openAnkiDroidPrivacyPolicy() }
        view.findViewById<Button>(R.id.logout_button).apply {
            text = TR.sentenceCase.logOut
            setOnClickListener { logout() }
        }
        view.findViewById<Button>(R.id.remove_account_button).setOnClickListener { openRemoveAccountScreen() }

        loggedInLogo = view.findViewById(R.id.login_logo)
    }

    private fun openAnkiDroidPrivacyPolicy() {
        Timber.i("Opening 'Privacy policy'")
        showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
    }

    private fun logout() {
        viewModel.onLogout()

        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.commit {
            replace(R.id.fragment_container, LoginFragment())
        }
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /**
     * Opens the AnkiWeb 'remove account' WebView
     * @see RemoveAccountFragment
     * @see R.string.remove_account_url
     */
    private fun openRemoveAccountScreen() {
        Timber.i("opening 'remove account'")
        requireActivity()
            .supportFragmentManager
            .beginTransaction()
            .replace(R.id.remove_account_frame, RemoveAccountFragment())
            .commit()
        requireView().findViewById<View>(R.id.remove_account_frame).isVisible = true
        requireView().findViewById<View>(R.id.logged_in_layout).isVisible = false
        onRemoveAccountBackCallback.isEnabled = true
    }

    private fun closeRemoveAccountScreen() {
        Timber.i("closing 'remove account'")
        requireActivity().supportFragmentManager.removeFragmentFromContainer(R.id.remove_account_frame)
        requireView().findViewById<View>(R.id.remove_account_frame).isVisible = false
        requireView().findViewById<View>(R.id.logged_in_layout).isVisible = true
        onRemoveAccountBackCallback.isEnabled = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loggedInLogo.isVisible = !(isCompactWidth && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
}
