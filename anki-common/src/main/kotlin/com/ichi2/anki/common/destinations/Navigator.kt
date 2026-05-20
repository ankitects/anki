// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment

// TODO: Move this into anki-common:android after libanki becomes a java-library

/**
 * Global navigation instance, set during app initialization.
 *
 * Accessible via [navigate].
 */
private lateinit var navigatorInstance: Navigator

/**
 * Resolves a [Destination] to an [Intent].
 *
 * Implementations should use the application `Context` internally.
 */
interface Navigator {
    // the intent constructor only uses context for the package name,
    // so using the app context is acceptable.
    fun toIntent(destination: Destination): Intent

    companion object {
        /**
         * Use during app startup to set the global [Navigator] instance.
         *
         * Placed on the companion object, so calers may use `Navigator.register` to avoid
         * collisions with other top-level `register` functions.
         */
        fun register(navigator: Navigator) {
            navigatorInstance = navigator
        }
    }
}

/** Starts the activity corresponding to [destination]. */
context(activity: Activity)
fun navigate(destination: Destination) {
    activity.startActivity(navigatorInstance.toIntent(destination))
}

/** Starts the activity corresponding to [destination] from the host of this Fragment. */
context(fragment: Fragment)
fun navigate(destination: Destination) {
    fragment.requireActivity().startActivity(navigatorInstance.toIntent(destination))
}

/** Launches [destination] via an [ActivityResultLauncher], so the caller can observe the result. */
context(launcher: ActivityResultLauncher<Intent>)
fun navigate(destination: Destination) {
    launcher.launch(navigatorInstance.toIntent(destination))
}
