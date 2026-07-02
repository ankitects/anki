/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package com.ichi2.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ichi2.anki.R
import com.ichi2.anki.SyncActionProvider
import timber.log.Timber

class BadgeDrawableBuilder(
    private val context: Context,
) {
    private var char = '\u0000'
    private var color: Int? = null

    fun withText(c: Char): BadgeDrawableBuilder {
        char = c
        return this
    }

    fun withColor(color: Int?): BadgeDrawableBuilder {
        this.color = color
        return this
    }

    fun replaceBadge(provider: SyncActionProvider) {
        Timber.d("Adding badge")
        var originalIcon = provider.icon
        if (originalIcon is BadgeDrawable) {
            originalIcon = originalIcon.current
        }
        val badge = BadgeDrawable(originalIcon)
        if (char != '\u0000') {
            badge.setText(char)
        }
        if (color != null) {
            val badgeDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.badge_drawable)
            if (badgeDrawable == null) {
                Timber.w("Unable to find badge_drawable - not drawing badge")
                return
            }
            val mutableDrawable = badgeDrawable.mutate()
            mutableDrawable.setTint(color!!)
            badge.setBadgeDrawable(mutableDrawable)
            provider.icon = badge
        }
    }

    companion object {
        fun removeBadge(provider: SyncActionProvider) {
            val icon = provider.icon
            if (icon is BadgeDrawable) {
                provider.icon = icon.drawable
                Timber.d("Badge removed")
            }
        }
    }
}
