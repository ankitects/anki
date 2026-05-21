/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.android.back

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.HandlerUtils
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates an [OnBackPressedCallback] that requires two back presses to exit.
 *
 * On first back press, [onFirstBack] is called and the callback disables itself
 * for [timeout]. While disabled, a second back press is not intercepted and falls
 * through to the default behavior (finishing the activity). If no second press occurs
 * within the timeout, the callback re-enables itself only if [shouldReEnable] returns true.
 *
 * @param enabled initial enabled state
 * @param timeout how long the callback stays disabled after the first back press
 * @param onFirstBack action on first back press (typically show a snackbar)
 * @param shouldReEnable condition checked after timeout to decide whether to re-enable
 */
fun doubleBackPressCallback(
    enabled: Boolean,
    timeout: Duration = 2.seconds,
    onFirstBack: () -> Unit,
    shouldReEnable: () -> Boolean = { true },
): OnBackPressedCallback =
    object : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            onFirstBack()
            isEnabled = false
            HandlerUtils.executeFunctionWithDelay(timeout.inWholeMilliseconds) {
                isEnabled = shouldReEnable()
            }
        }
    }

/**
 * Note: this uses sharedPreferences via AnkiDroidApp, so must be called after
 * [AnkiActivity.showedActivityFailedScreen]
 *
 * @see Prefs.exitViaDoubleTapBack
 */
// TODO: Convert this to a class when context parameters are usable
fun AnkiActivity.exitViaDoubleTapBackCallback(): OnBackPressedCallback =
    object : OnBackPressedCallback(enabled = Prefs.exitViaDoubleTapBack) {
        lateinit var strongListenerReference: OnSharedPreferenceChangeListener

        override fun handleOnBackPressed() {
            showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT)
            this.isEnabled = false
            HandlerUtils.executeFunctionWithDelay(2.seconds.inWholeMilliseconds) {
                this.isEnabled = true
            }
        }
    }.also { callback ->
        // PreferenceManager uses weak references, so we need our own strong reference which
        // will go out of scope
        callback.strongListenerReference =
            OnSharedPreferenceChangeListener { prefs, key ->
                if (key == getString(R.string.exit_via_double_tap_back_key)) {
                    callback.isEnabled =
                        Prefs.exitViaDoubleTapBack.also {
                            Timber.i("exit via double tap callback -> %b", it)
                        }
                }
            }

        PreferenceManager
            .getDefaultSharedPreferences(AnkiDroidApp.instance)
            .registerOnSharedPreferenceChangeListener(callback.strongListenerReference)
    }
