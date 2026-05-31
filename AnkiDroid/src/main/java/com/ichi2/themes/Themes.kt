/*
 Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.themes

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.themes.Themes.currentTheme

/**
 * Helper methods to configure things related to AnkiDroid's themes
 */
object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%

    var currentTheme: Theme = DayTheme.LIGHT
    val isNightTheme: Boolean get() = currentTheme is NightTheme

    fun setTheme(context: Context) {
        updateCurrentTheme(context)
        context.setTheme(currentTheme.styleResId)
    }

    fun setTheme(activity: Activity) {
        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        val hadLauncherSplash = tv.resourceId == R.drawable.launch_screen

        setTheme(activity as Context)

        if (hadLauncherSplash) {
            activity.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
            val replacement =
                if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                    tv.data.toDrawable()
                } else {
                    AppCompatResources.getDrawable(activity, tv.resourceId)
                }
            activity.window.setBackgroundDrawable(replacement)
        }
    }

    fun setLegacyActionBar(context: Context) {
        context.setTheme(R.style.ThemeOverlay_LegacyActionBar)
    }

    /**
     * Updates [currentTheme] value based on preferences.
     * If `Follow system` is selected, it's updated to the theme set
     * on `Day` or `Night` theme according to system's current mode
     * Otherwise, updates to the selected theme.
     */
    fun updateCurrentTheme(context: Context) {
        val prefs = PrefsRepository(context)
        val appTheme = prefs.appTheme

        val themeIsDark = (appTheme == AppTheme.FOLLOW_SYSTEM && systemIsInNightMode(context)) || appTheme == AppTheme.NIGHT
        currentTheme =
            if (themeIsDark) {
                prefs.nightTheme
            } else {
                prefs.dayTheme
            }
        val defaultNightMode =
            when (appTheme) {
                AppTheme.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppTheme.DAY -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            }
        AppCompatDelegate.setDefaultNightMode(defaultNightMode)
    }

    /**
     * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
     */
    fun disableXiaomiForceDarkMode(context: Context) {
        // Setting a theme is an additive operation, so this adds a single property.
        context.setTheme(R.style.ThemeOverlay_Xiaomi)
    }
}

@Suppress("deprecation", "API35 properly handle edge-to-edge")
fun FragmentActivity.setTransparentStatusBar() {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
        Themes.currentTheme !is NightTheme
    window.statusBarColor = Color.TRANSPARENT
}

fun FragmentActivity.setTransparentBackground() {
    window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
}
