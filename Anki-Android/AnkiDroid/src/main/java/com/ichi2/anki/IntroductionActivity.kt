// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.account.LoginFragment
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.SetupCollectionFragment
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption
import com.ichi2.anki.introduction.SetupCollectionFragment.Companion.FRAGMENT_KEY
import com.ichi2.anki.introduction.SetupCollectionFragment.Companion.RESULT_KEY
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.utils.ext.setFragmentResultListener
import timber.log.Timber

/**
 * App introduction for new users.
 *
 * Links to [AccountActivity]/[LoginFragment] ("Sync from AnkiWeb") or [DeckPicker] ("Get Started")
 *
 * @see SetupCollectionFragment
 */
// TODO: Background of introduction_layout does not display on API 25 emulator: https://github.com/ankidroid/Anki-Android/pull/12033#issuecomment-1228429130
@NeedsTest("Ensure that we can get here on first run without an exception dialog shown")
class IntroductionActivity :
    AnkiActivity(R.layout.activity_introduction),
    CollectionPermissionScreenLauncher {
    override val permissionScreenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasCollectionStoragePermissions()) {
                openLoginDialog()
            }
        }

    @NeedsTest("ensure this is called when the activity ends")
    private val onLoginResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                Timber.i("login successful, opening deck picker to sync")
                startDeckPicker(RESULT_SYNC_PROFILE)
            } else {
                Timber.i("login was not successful")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)

        setFragmentResultListener(FRAGMENT_KEY) { _, bundle ->
            val option =
                BundleCompat.getParcelable(bundle, RESULT_KEY, CollectionSetupOption::class.java) ?: error("Missing introduction option!")
            when (option) {
                CollectionSetupOption.DeckPickerWithNewCollection -> startDeckPicker()
                CollectionSetupOption.SyncFromExistingAccount -> openLoginDialog()
            }
        }
    }

    private fun openLoginDialog() {
        if (collectionPermissionScreenWasOpened()) return
        Timber.i("Opening login screen")
        val intent = AccountActivity.getIntent(context = this, forResult = true)
        onLoginResult.launch(intent)
    }

    private fun startDeckPicker(result: Int = RESULT_START_NEW) {
        Timber.i("Opening deck picker, login: %b", result == RESULT_SYNC_PROFILE)
        this.sharedPrefs().edit { putBoolean(INTRODUCTION_SLIDES_SHOWN, true) }
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (result == RESULT_SYNC_PROFILE) {
            deckPicker.putExtra(DeckPicker.INTENT_SYNC_FROM_LOGIN, true)
        }

        startActivity(deckPicker)
        finish()
    }

    companion object {
        const val RESULT_START_NEW = 1
        const val RESULT_SYNC_PROFILE = 2

        /**
         * Key for the preference recording that the slide "Study less/ Remember more" offering to
         * get started or sync from ankiweb, was displayed. If so don't display it again.
         */
        const val INTRODUCTION_SLIDES_SHOWN = "IntroductionSlidesShown"
    }
}

internal fun Context.hasShownAppIntro(): Boolean = sharedPrefs().getBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, false)
