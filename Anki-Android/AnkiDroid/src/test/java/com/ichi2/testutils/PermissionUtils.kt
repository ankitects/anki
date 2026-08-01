/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import timber.log.Timber

/** Executes [runnable] without [WRITE_EXTERNAL_STORAGE] and [READ_EXTERNAL_STORAGE] */
fun withNoWritePermission(runnable: (() -> Unit)) {
    try {
        revokeWritePermissions()
        runnable()
    } finally {
        grantWritePermissions()
    }
}

/**
 * [block] runs with both [WRITE_EXTERNAL_STORAGE], [READ_EXTERNAL_STORAGE] granted
 *
 * @see grantWritePermissions
 */
fun withWritePermissions(block: () -> Unit) =
    try {
        grantWritePermissions()
        block()
    } finally {
        revokeWritePermissions()
    }

fun grantWritePermissions() {
    val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
    app.grantPermissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
}

fun revokeWritePermissions() {
    val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
    app.denyPermissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
}

fun grantPermissions(vararg permissions: String) {
    Timber.i("granting permissions: %s", permissions.contentToString())
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*permissions)
}

fun denyPermissions(vararg permissions: String) {
    Timber.i("denying permissions: %s", permissions.contentToString())
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(*permissions)
}

fun withGrantedPermissions(
    vararg permissions: String,
    block: () -> Unit,
) = try {
    grantPermissions(*permissions)
    block()
} finally {
    denyPermissions(*permissions)
}

suspend fun withDeniedPermissions(
    vararg permissions: String,
    block: suspend () -> Unit,
) = try {
    denyPermissions(*permissions)
    block()
} finally {
    grantPermissions(*permissions)
}
