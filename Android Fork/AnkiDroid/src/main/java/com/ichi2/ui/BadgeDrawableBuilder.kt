// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.ContextCompat
import com.ichi2.anki.R
import com.ichi2.anki.SyncActionProvider
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

class BadgeDrawableBuilder(
    private val context: Context,
) {
    private var char = '\u0000'
    private var color: Int? = null

    fun withText(c: Char): BadgeDrawableBuilder {
        char = c
        return this
    }

    fun withColorAttr(attr: Int): BadgeDrawableBuilder {
        this.color = ThemeUtils.getThemeAttrColor(context, attr)
        return this
    }

    fun replaceBadge(provider: SyncActionProvider) {
        Timber.d("Adding badge")
        var originalIcon = provider.icon
        if (originalIcon is BadgeDrawable) {
            originalIcon = originalIcon.current
        }
        val badge = BadgeDrawable(originalIcon, ThemeUtils.getThemeAttrColor(context, CommonR.attr.badgeTextColor))
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
