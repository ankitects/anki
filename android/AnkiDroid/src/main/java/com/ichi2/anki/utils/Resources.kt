/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService

/**
 * @param resId must be a [StringRes] or a [PluralsRes]
 */
fun Resources.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String =
    when (getResourceTypeName(resId)) {
        "string" -> getString(resId, quantity)
        "plurals" -> getQuantityString(resId, quantity, quantity)
        else -> throw IllegalArgumentException("Provided resId is not a valid @StringRes or @PluralsRes")
    }

/**
 * @see [Resources.getFormattedStringOrPlurals]
 */
fun Context.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String = resources.getFormattedStringOrPlurals(resId, quantity)

@SuppressLint("DiscouragedApi") // Resources.getIdentifier: Use of this function is discouraged
// because resource reflection makes it harder to perform build optimizations and compile-time
// verification of code. It is much more efficient to retrieve resources by identifier
// (e.g. `R.foo.bar`) than by name (e.g. `getIdentifier("bar", "foo", null)`)
private fun Context.getSystemBoolean(resName: String, fallback: Boolean): Boolean =
    try {
        val id = Resources.getSystem().getIdentifier(resName, "bool", "android")
        if (id != 0) {
            createPackageContext("android", 0).resources.getBoolean(id)
        } else {
            fallback
        }
    } catch (e: Exception) {
        CrashReportService.sendExceptionReport(e, "Context::getSystemBoolean")
        fallback
    }

@NeedsTest("true if the navbar is transparent and needs a scrim, false if not")
val FragmentActivity.navBarNeedsScrim: Boolean
    get() = getSystemBoolean("config_navBarNeedsScrim", true)

// https://m3.material.io/foundations/layout/applying-layout/window-size-classes
// adopted smallestScreenWidthDp instead of screenWidthDp
// to avoid layout changes when rotating the device
fun Resources.isWindowCompact() = configuration.smallestScreenWidthDp < 600
