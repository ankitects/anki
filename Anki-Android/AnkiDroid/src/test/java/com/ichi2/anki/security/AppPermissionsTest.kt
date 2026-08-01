/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.security

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.security.DangerousJsApiPermission.QUERY_COLLECTION
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.getString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class AppPermissionsTest : RobolectricTest() {
    private val snackbars = mutableListOf<String>()
    private val permissions get() = AppPermissions(targetContext) { snackbars += it }

    @Before
    fun resetDenialCounter() {
        AppPermissions.resetDenialCounterForTesting()
    }

    @Test
    fun jsPermission_defaultsToDenied() {
        val ex =
            assertFailsWith<DangerousJsPermissionDeniedException> {
                permissions.requirePermission(QUERY_COLLECTION)
            }
        assertThat(ex.permission, equalTo(QUERY_COLLECTION.name))
    }

    @Test
    fun jsPermission_allowedWhenToggleOn() {
        allowDangerousJsApis = true
        assertDoesNotThrow { permissions.requirePermission(QUERY_COLLECTION) }
        assertThat(snackbars.size, equalTo(0))
    }

    @Test
    fun jsPermission_deniedWhenToggleOff() {
        allowDangerousJsApis = false
        assertFailsWith<DangerousJsPermissionDeniedException> {
            permissions.requirePermission(QUERY_COLLECTION)
        }
    }

    @Test
    fun jsPermission_snackbarShownForFirstTwoDenialsThenSuppressed() {
        repeat(5) {
            assertFailsWith<DangerousJsPermissionDeniedException> {
                permissions.requirePermission(QUERY_COLLECTION)
            }
        }
        assertThat(snackbars.size, equalTo(2))
    }

    @Test
    fun jsPermission_suppressionIsProcessWideAcrossInstances() {
        repeat(2) {
            assertFailsWith<DangerousJsPermissionDeniedException> {
                AppPermissions(targetContext) { snackbars += it }.requirePermission(QUERY_COLLECTION)
            }
        }
        assertFailsWith<DangerousJsPermissionDeniedException> {
            AppPermissions(targetContext) { snackbars += it }.requirePermission(QUERY_COLLECTION)
        }
        assertThat(snackbars.size, equalTo(2))
    }

    @Test
    fun launchExternalApps_defaultsToDenied() {
        assertThat(permissions.checkCanLaunchExternalApps(), equalTo(false))
    }

    @Test
    fun launchExternalApps_allowedWhenToggleOn() {
        allowCardExternalLaunch = true
        assertThat(permissions.checkCanLaunchExternalApps(), equalTo(true))
        assertThat(snackbars.size, equalTo(0))
    }

    @Test
    fun launchExternalApps_snackbarShownForFirstTwoDenialsThenSuppressed() {
        repeat(5) { assertThat(permissions.checkCanLaunchExternalApps(), equalTo(false)) }
        assertThat(snackbars.size, equalTo(2))
    }

    @Test
    fun launchExternalApps_suppressionIsProcessWideAcrossInstances() {
        repeat(2) {
            AppPermissions(targetContext) { snackbars += it }.checkCanLaunchExternalApps()
        }
        AppPermissions(targetContext) { snackbars += it }.checkCanLaunchExternalApps()
        assertThat(snackbars.size, equalTo(2))
    }
}

var AppPermissionsTest.allowDangerousJsApis: Boolean
    get() = getPreferences().getBoolean(getString(R.string.pref_allow_dangerous_js_api), false)
    set(value) =
        editPreferences {
            putBoolean(getString(R.string.pref_allow_dangerous_js_api), value)
        }

var AppPermissionsTest.allowCardExternalLaunch: Boolean
    get() =
        targetContext.sharedPrefs().getBoolean(
            targetContext.getString(R.string.pref_allow_card_external_launch_key),
            false,
        )
    set(value) =
        targetContext
            .sharedPrefs()
            .edit {
                putBoolean(
                    targetContext.getString(R.string.pref_allow_card_external_launch_key),
                    value,
                )
            }
