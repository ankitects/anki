// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.ichi2.anim.enableActivityAnimation
import com.ichi2.anim.enableIntentAnimation
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.anki.common.ui.TransitionDirection.DEFAULT
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.anki.common.android.R as CommonR

// TODO: move the rest of AnkiActivity into this class (#20737)

/**
 * The parts of `AnkiActivity` that a screen outside `:AnkiDroid` needs: the active profile,
 * the theme, transition animations and analytics. The app provides the profile and the theme
 * through [ActivitySetup].
 *
 * What to do now:
 * - Screens in `:AnkiDroid` keep extending `AnkiActivity`.
 * - Screens moving out of `:AnkiDroid`, such as the widget config screens, extend this class.
 *   If one needs something only `AnkiActivity` has, move it here rather than copying it.
 * - Don't extend [AppCompatActivity] directly for an AnkiDroid screen: it would skip the profile,
 *   theme and transitions.
 * - Call [showedActivityFailedScreen] at the start of `onCreate`.
 * - Extension functions should take [FragmentActivity][androidx.fragment.app.FragmentActivity]
 *   when that's all they need, as the transition helpers do. Don't use this class as a receiver
 *   yet: `AnkiActivity` doesn't extend it, so the extension would miss most screens.
 *
 * Long term (#20737):
 * - `AnkiActivity`'s generic code moves here piece by piece, and `AnkiActivity` extends this
 *   class until it only holds the app-specific parts, or is gone.
 * - Ideally this is the one base activity every AnkiDroid screen extends, and the app only plugs
 *   in what it owns through hooks like [ActivitySetup].
 * - Extensions can then use this class as their receiver when they need AnkiDroid's setup.
 *
 * Deficiencies:
 * - `AnkiActivity` doesn't extend this class yet, so the setup here is duplicated there.
 * - There are no collection or dialog helpers. Database errors open in the Deck Picker rather
 *   than on this screen.
 * - If the app hasn't registered [ActivitySetup] (after a restore from backup), the profile and
 *   theme are skipped, and [showedActivityFailedScreen] closes the screen.
 */
open class BaseAnkiActivity(
    @LayoutRes contentLayoutId: Int = 0,
) : AppCompatActivity(contentLayoutId) {
    override val defaultViewModelCreationExtras: CreationExtras
        get() =
            MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
                this[DEFAULT_ARGS_KEY] = Bundle.EMPTY
            }

    override fun attachBaseContext(newBase: Context) {
        val base = activitySetup?.wrapForActiveProfile(newBase) ?: newBase
        activitySetup?.applyNightMode(base)
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO: this belongs in the screens which play audio, not in every activity
        volumeControlStream = AudioManager.STREAM_MUSIC
        activitySetup?.setTheme(this, savedInstanceState)
        disableXiaomiForceDarkMode(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            @Suppress("deprecation")
            window.navigationBarColor = getColor(CommonR.color.transparent)
        }
    }

    override fun onResume() {
        super.onResume()
        Analytics.sendAnalyticsScreenView(javaClass.simpleName)
    }

    override fun startActivity(intent: Intent) {
        enableIntentAnimation(intent)
        super.startActivity(intent)
        enableActivityAnimation(DEFAULT, open = true)
    }

    override fun finish() {
        super.finish()
        enableActivityAnimation(DEFAULT, open = false)
    }

    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state ->
                activitySetup?.setTheme(this, state)
                super.onCreate(state)
            },
        )
}
