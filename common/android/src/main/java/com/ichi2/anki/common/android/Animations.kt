/*
 * Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>
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
package com.ichi2.anki.common.android

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.ichi2.anki.common.preferences.AnimationPreferences

/**
 * Utility class for animation-related helper functions
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
