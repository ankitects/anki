// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.common.android

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.ichi2.anki.common.preferences.AnimationPreferences

/**
 * Animation related functionality for the app.
 */
object Animations {
    /** resolved against the supplied [Context] so the result uses the active profile */
    private lateinit var preferencesProvider: (Context) -> AnimationPreferences

    /** Use during app startup to register the source of [AnimationPreferences]. */
    context(_: Application)
    fun setPreferencesProvider(provider: (Context) -> AnimationPreferences) {
        preferencesProvider = provider
    }

    /**
     * @return whether the animations are enabled by the system settings,
     * i.e. the 'Remove animations' setting is disabled.
     * On most cases, using [Animations.areAnimationsEnabled] is preferred
     * because it considers the app's own 'Remove animations' setting
     */
    fun areSystemAnimationsEnabled(context: Context): Boolean =
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        } catch (_: Exception) {
            true // Default to animations enabled if unable to read settings
        }

    /**
     * @return whether animations are enabled on the system and app settings
     */
    fun areAnimationsEnabled(context: Context): Boolean =
        areSystemAnimationsEnabled(context) && !preferencesProvider(context).removeAppAnimations
}
