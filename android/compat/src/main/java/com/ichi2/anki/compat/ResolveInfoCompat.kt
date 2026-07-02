/*
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
 *
 *  This file incorporates code under the following license:
 *
 *     Copyright (C) 2006 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *     https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/content/pm/PackageManager.java
 *
 * **Changes**
 * * Added _L constants to fix 'Warning: Unexpected reference to null'
 */
package com.ichi2.anki.compat

import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.UserManager
import androidx.annotation.LongDef

class ResolveInfoFlagsCompat private constructor(
    @ResolveInfoFlagsBitsCompat value: Long,
) : Flags(value) {
    companion object {
        fun of(
            @ResolveInfoFlagsBitsCompat value: Long,
        ): ResolveInfoFlagsCompat = ResolveInfoFlagsCompat(value)

        /** Helper property. Does not exist on Platform API */
        val EMPTY
            get() = ResolveInfoFlagsCompat(0)
    }
}

/**
 * [ComponentInfo] flag: return the [ComponentInfo.metaData]
 * data [android.os.Bundle]s that are associated with a component.
 * This applies for any API returning a ComponentInfo subclass.
 */
const val GET_META_DATA = 0x00000080

/** @see GET_META_DATA */
const val GET_META_DATA_L: Long = GET_META_DATA.toLong()

/**
 * [ResolveInfo] flag: return the IntentFilter that
 * was matched for a particular ResolveInfo in
 * [ResolveInfo.filter].
 */
const val GET_RESOLVED_FILTER = 0x00000040

/** @see GET_RESOLVED_FILTER */
const val GET_RESOLVED_FILTER_L: Long = GET_RESOLVED_FILTER.toLong()

/**
 * [ApplicationInfo] flag: return the
 * [paths to the shared libraries][ApplicationInfo.sharedLibraryFiles]
 * that are associated with an application.
 * This applies for any API returning an ApplicationInfo class, either
 * directly or nested inside of another.
 */
const val GET_SHARED_LIBRARY_FILES = 0x00000400

/** @see GET_SHARED_LIBRARY_FILES */
const val GET_SHARED_LIBRARY_FILES_L: Long = GET_SHARED_LIBRARY_FILES.toLong()

/**
 * Querying flag: if set and if the platform is doing any filtering of the
 * results, then the filtering will not happen. This is a synonym for saying
 * that all results should be returned.
 *
 *
 * *This flag should be used with extreme care.*
 */
const val MATCH_ALL = 0x00020000

/** @see MATCH_ALL */
const val MATCH_ALL_L: Long = MATCH_ALL.toLong()

/**
 * Resolution and querying flag: if set, only filters that support the
 * [android.content.Intent.CATEGORY_DEFAULT] will be considered for
 * matching.  This is a synonym for including the CATEGORY_DEFAULT in your
 * supplied Intent.
 */
const val MATCH_DEFAULT_ONLY = 0x00010000

/** @see MATCH_DEFAULT_ONLY */
const val MATCH_DEFAULT_ONLY_L: Long = MATCH_DEFAULT_ONLY.toLong()

/**
 * Querying flag: automatically match components based on their Direct Boot
 * awareness and the current user state.
 *
 *
 * Since the default behavior is to automatically apply the current user
 * state, this is effectively a sentinel value that doesn't change the
 * output of any queries based on its presence or absence.
 *
 *
 * Instead, this value can be useful in conjunction with
 * [android.os.StrictMode.VmPolicy.Builder.detectImplicitDirectBoot]
 * to detect when a caller is relying on implicit automatic matching,
 * instead of confirming the explicit behavior they want, using a
 * combination of these flags:
 *
 *  * [PackageManager.MATCH_DIRECT_BOOT_AWARE]
 *  * [PackageManager.MATCH_DIRECT_BOOT_UNAWARE]
 *  * [PackageManager.MATCH_DIRECT_BOOT_AUTO]
 *
 */
const val MATCH_DIRECT_BOOT_AUTO = 0x10000000

/** @see MATCH_DIRECT_BOOT_AUTO */
const val MATCH_DIRECT_BOOT_AUTO_L: Long = MATCH_DIRECT_BOOT_AUTO.toLong()

/**
 * Querying flag: match components which are direct boot *aware* in
 * the returned info, regardless of the current user state.
 *
 *
 * When neither [.MATCH_DIRECT_BOOT_AWARE] nor
 * [.MATCH_DIRECT_BOOT_UNAWARE] are specified, the default behavior is
 * to match only runnable components based on the user state. For example,
 * when a user is started but credentials have not been presented yet, the
 * user is running "locked" and only [.MATCH_DIRECT_BOOT_AWARE]
 * components are returned. Once the user credentials have been presented,
 * the user is running "unlocked" and both [.MATCH_DIRECT_BOOT_AWARE]
 * and [.MATCH_DIRECT_BOOT_UNAWARE] components are returned.
 *
 * @see UserManager.isUserUnlocked
 */
const val MATCH_DIRECT_BOOT_AWARE = 0x00080000

/** @see MATCH_DIRECT_BOOT_AWARE */
const val MATCH_DIRECT_BOOT_AWARE_L: Long = MATCH_DIRECT_BOOT_AWARE.toLong()

/**
 * Querying flag: match components which are direct boot *unaware* in
 * the returned info, regardless of the current user state.
 *
 *
 * When neither [.MATCH_DIRECT_BOOT_AWARE] nor
 * [.MATCH_DIRECT_BOOT_UNAWARE] are specified, the default behavior is
 * to match only runnable components based on the user state. For example,
 * when a user is started but credentials have not been presented yet, the
 * user is running "locked" and only [.MATCH_DIRECT_BOOT_AWARE]
 * components are returned. Once the user credentials have been presented,
 * the user is running "unlocked" and both [.MATCH_DIRECT_BOOT_AWARE]
 * and [.MATCH_DIRECT_BOOT_UNAWARE] components are returned.
 *
 * @see UserManager.isUserUnlocked
 */
const val MATCH_DIRECT_BOOT_UNAWARE = 0x00040000

/** @see MATCH_DIRECT_BOOT_UNAWARE */
const val MATCH_DIRECT_BOOT_UNAWARE_L: Long = MATCH_DIRECT_BOOT_UNAWARE.toLong()

@LongDef(
    flag = true,
    // prefix = ["GET_", "MATCH_"],
    value = [
        GET_META_DATA_L,
        GET_RESOLVED_FILTER_L,
        GET_SHARED_LIBRARY_FILES_L,
        MATCH_ALL_L,
        MATCH_DISABLED_COMPONENTS_L,
        MATCH_DISABLED_UNTIL_USED_COMPONENTS_L,
        MATCH_DEFAULT_ONLY_L,
        MATCH_DIRECT_BOOT_AUTO_L,
        MATCH_DIRECT_BOOT_AWARE_L,
        MATCH_DIRECT_BOOT_UNAWARE_L,
        MATCH_SYSTEM_ONLY_L,
        MATCH_UNINSTALLED_PACKAGES_L,
        // PackageManager.MATCH_INSTANT, // @SystemApi
        // PackageManager.MATCH_DEBUG_TRIAGED_MISSING, // deprecated
        // PackageManager.GET_DISABLED_COMPONENTS, // deprecated
        // PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS, // deprecated
        // PackageManager.GET_UNINSTALLED_PACKAGES.toLong() // deprecated
    ],
)
@Retention(AnnotationRetention.SOURCE)
annotation class ResolveInfoFlagsBitsCompat
