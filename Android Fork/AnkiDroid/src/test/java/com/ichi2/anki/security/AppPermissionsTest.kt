// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `web URLs are allowed without external app permission or a warning`() {
        allowCardExternalLaunch = false
        for (scheme in listOf("http", "https")) {
            val url = "$scheme://example.org/path?query=value#fragment".toUri()
            val intent = assertNotNull(permissions.createUrlIntent(url))
            assertThat(intent.action, equalTo(Intent.ACTION_VIEW))
            assertThat(intent.data, equalTo(url))
            assertThat(intent.categories, equalTo(setOf(Intent.CATEGORY_BROWSABLE)))
            assertNull(intent.selector)
        }
        assertThat(snackbars.size, equalTo(0))
    }

    @Test
    fun `non-web URLs are denied without external app permission`() {
        allowCardExternalLaunch = false
        val urls =
            listOf(
                "market://details?id=com.example",
                "intent://example.org/#Intent;scheme=https;end",
                "android-app://com.example/https/example.org",
            )
        for (url in urls) {
            assertNull(permissions.createUrlIntent(url.toUri()), url)
        }
        assertThat(snackbars.size, equalTo(2))
    }

    @Test
    fun `non-web URLs are allowed with external app permission`() {
        allowCardExternalLaunch = true
        val url = "market://details?id=com.example".toUri()
        val intent = assertNotNull(permissions.createUrlIntent(url))
        assertThat(intent.action, equalTo(Intent.ACTION_VIEW))
        assertThat(intent.data, equalTo(url))
        assertNull(intent.categories)
        assertThat(snackbars.size, equalTo(0))
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
