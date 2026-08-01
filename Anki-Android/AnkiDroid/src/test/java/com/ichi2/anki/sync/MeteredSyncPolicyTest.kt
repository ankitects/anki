/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.sync

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.EmptyAnkiActivity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** Tests for [MeteredSyncPolicy] */
@RunWith(RobolectricTestRunner::class)
class MeteredSyncPolicyTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(MeteredSyncPolicy)
    }

    @After
    override fun tearDown() {
        unmockkObject(MeteredSyncPolicy)
        super.tearDown()
    }

    @Test
    fun `runs onConfirm immediately when not blocked`() {
        every { MeteredSyncPolicy.shouldBlock() } returns false

        val result = attemptMeteredSync()

        assertThat("onDialogShown not called", result.dialogShown, equalTo(false))
        assertThat("onConfirm ran", result.onConfirmCalled, equalTo(true))
        assertThat("no dialog shown", result.dialog, nullValue())
    }

    @Test
    fun `shows dialog and notifies onDialogShown when blocked`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()

        assertThat("onDialogShown invoked", result.dialogShown, equalTo(true))
        assertThat("onConfirm not yet run", result.onConfirmCalled, equalTo(false))
        assertThat("dialog shown", result.dialog, notNullValue())
    }

    @Test
    fun `Continue runs onConfirm`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()
        assertThat("onConfirm not called initially", result.onConfirmCalled, equalTo(false))

        result.clickContinue()

        assertThat("onConfirm ran after Continue", result.onConfirmCalled, equalTo(true))
    }

    @Test
    fun `Cancel does not run onConfirm`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()
        assertThat("onConfirm not called initially", result.onConfirmCalled, equalTo(false))

        result.clickCancel()

        assertThat("onConfirm not run after Cancel", result.onConfirmCalled, equalTo(false))
    }

    @Test
    fun `skipPrompt skips prompt even when metered`() {
        // Issue 20674
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no dialog shown", result.dialog, nullValue())
        assertThat("onDialogShown not called", result.dialogShown, equalTo(false))
    }

    @Test
    fun `skipPrompt on unmetered network runs directly`() {
        every { MeteredSyncPolicy.shouldBlock() } returns false

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no dialog shown", result.dialog, nullValue())
    }

    @Test
    fun `setAlwaysAllow persists choice`() {
        unmockkObject(MeteredSyncPolicy)
        Prefs.allowSyncOnMeteredConnections = false
        MeteredSyncPolicy.setAlwaysAllow(true)
        assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(true))
    }

    /** Invokes [MeteredSyncPolicy.confirmThen] from a freshly-built activity. */
    private fun attemptMeteredSync(skipPrompt: Boolean = false): MeteredSyncAttemptResult {
        var dialogShown = false
        var confirmed = false
        with(startRegularActivity<EmptyAnkiActivity>()) {
            MeteredSyncPolicy.confirmThen(
                skipPrompt = skipPrompt,
                onDialogShown = { dialogShown = true },
            ) { confirmed = true }
        }
        return MeteredSyncAttemptResult(
            dialogShown = dialogShown,
            isConfirmed = { confirmed },
            dialog = ShadowDialog.getLatestDialog() as? AlertDialog,
        )
    }

    /**
     * Tracks the side effects of a single call to [MeteredSyncPolicy.confirmThen].
     *
     * @property dialogShown whether the warning dialog's `onDialogShown` callback fired
     * @property onConfirmCalled whether `onConfirm` has been invoked
     * @property dialog the most recently displayed [AlertDialog], or `null` if none was shown
     */
    private class MeteredSyncAttemptResult(
        val dialogShown: Boolean,
        private val isConfirmed: () -> Boolean,
        val dialog: AlertDialog?,
    ) {
        val onConfirmCalled: Boolean get() = isConfirmed()

        fun clickContinue() = clickDialogButton(DialogInterface.BUTTON_POSITIVE)

        fun clickCancel() = clickDialogButton(DialogInterface.BUTTON_NEGATIVE)

        private fun clickDialogButton(button: Int) {
            dialog!!.getButton(button).performClick()
            ShadowLooper.runUiThreadTasks()
        }
    }
}
