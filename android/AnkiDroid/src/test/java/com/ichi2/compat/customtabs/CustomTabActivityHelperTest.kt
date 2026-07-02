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
package com.ichi2.compat.customtabs

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.CheckResult
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomTabActivityHelperTest {
    @Before
    fun before() {
        CustomTabActivityHelper.resetFailed()
    }

    @Test
    fun ensureInvalidClientWithSecurityExceptionDoesNotCrash() {
        val badClient = getClientThrowingSecurityException()
        val customTabActivityHelper = getValidTabHandler()

        customTabActivityHelper.onServiceConnected(badClient)

        assertThat("Should be failed after call", customTabActivityHelper.isFailed)
    }

    @Test
    fun invalidClientMeansFallbackIsCalled() {
        getValidTabHandler().onServiceConnected(getClientThrowingSecurityException())

        val fallback = mock<CustomTabActivityHelper.CustomTabFallback>()
        val packageManager =
            mock<PackageManager> {
                on {
                    it.queryIntentActivitiesCompat(
                        Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri()),
                        ResolveInfoFlagsCompat.EMPTY,
                    )
                } doReturn emptyList()
            }
        val activity =
            mock<Activity> {
                on { it.packageManager } doReturn packageManager
            }

        CustomTabActivityHelper.openCustomTab(activity, mock(), mock(), fallback)

        verify(fallback, times(1)).openUri(any(), any())
    }

    @CheckResult
    private fun getValidTabHandler(): CustomTabActivityHelper =
        CustomTabActivityHelper().also {
            assertThat("Should not be failed before call", not(it.isFailed))
        }

    @CheckResult
    private fun getClientThrowingSecurityException(): CustomTabsClient {
        val exceptionToThrow = SecurityException("Binder invocation to an incorrect interface")

        return mock {
            on { it.warmup(anyLong()) } doThrow exceptionToThrow
            on { it.extraCommand(anyString(), any()) } doThrow exceptionToThrow
            on { it.newSession(any()) } doThrow exceptionToThrow
        }
    }
}
