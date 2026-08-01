/*
 *  Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki

import android.Manifest.permission.INTERNET
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.SingleFragmentActivity.Companion.EXTRA_FRAGMENT_NAME
import com.ichi2.anki.account.LoginFragment
import com.ichi2.anki.introduction.SetupCollectionFragment
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption.DeckPickerWithNewCollection
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption.SyncFromExistingAccount
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.testutils.withDeniedPermissions
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/** Tests [IntroductionActivity] */
@RunWith(AndroidJUnit4::class)
class IntroductionActivityTest : RobolectricTest() {
    @Test
    fun `Sync without storage permission opens PermissionsActivity`() =
        runTest {
            // Robolectric runs at API 35 where selectStoragePermissions returns PermissionSet.APP_PRIVATE,
            // which is just [INTERNET]. Denying INTERNET is the cheapest
            // way to make hasCollectionStoragePermissions() return false in this environment.
            withDeniedPermissions(INTERNET) {
                val activity = startRegularActivity<IntroductionActivity>(Intent())

                activity.clickSync()

                val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
                assertThat(
                    intent.component?.shortClassName,
                    equalTo(PermissionsActivity::class.java.name),
                )
            }
        }

    @Test
    fun `Sync with storage permission opens the login screen`() =
        runTest {
            val activity = startRegularActivity<IntroductionActivity>(Intent())

            activity.clickSync()

            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertThat(
                "host activity is SingleFragmentActivity",
                intent.component?.shortClassName,
                equalTo(SingleFragmentActivity::class.java.name),
            )
            assertThat(
                "hosted fragment is LoginFragment",
                intent.getStringExtra(EXTRA_FRAGMENT_NAME),
                equalTo(LoginFragment::class.jvmName),
            )
        }

    @Test
    fun `Get Started opens DeckPicker without the sync-from-login flag`() =
        runTest {
            val activity = startRegularActivity<IntroductionActivity>(Intent())

            activity.supportFragmentManager.setFragmentResult(
                SetupCollectionFragment.FRAGMENT_KEY,
                Bundle().apply { putParcelable(SetupCollectionFragment.RESULT_KEY, DeckPickerWithNewCollection) },
            )

            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertThat(
                intent.component?.className,
                equalTo(DeckPicker::class.java.name),
            )
            assertThat(
                "DeckPicker isn't asked to sync (Get Started, not Sync)",
                intent.getBooleanExtra(DeckPicker.INTENT_SYNC_FROM_LOGIN, false),
                equalTo(false),
            )
            assertThat(
                "intro is marked as shown so it doesn't reappear",
                getPreferences().getBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, false),
                equalTo(true),
            )
        }

    private fun IntroductionActivity.clickSync() {
        supportFragmentManager.setFragmentResult(
            SetupCollectionFragment.FRAGMENT_KEY,
            Bundle().apply { putParcelable(SetupCollectionFragment.RESULT_KEY, SyncFromExistingAccount) },
        )
    }
}
