// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

        assertThat("no confirmation required", result.confirmationRequired, equalTo(false))
        assertThat("onConfirm ran", result.onConfirmCalled, equalTo(true))
        assertThat(result.permission, equalTo(MeteredSyncPermission.USE_PREFERENCES))
    }

    @Test
    fun `requests confirmation when blocked without starting sync`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()

        assertThat("confirmation requested", result.confirmationRequired, equalTo(true))
        assertThat("onConfirm not yet run", result.onConfirmCalled, equalTo(false))
    }

    @Test
    fun `skipPrompt skips prompt even when metered`() {
        // Issue 20674
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no confirmation required", result.confirmationRequired, equalTo(false))
        assertThat(result.permission, equalTo(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME))
    }

    @Test
    fun `skipPrompt on unmetered network runs directly`() {
        every { MeteredSyncPolicy.shouldBlock() } returns false

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no confirmation required", result.confirmationRequired, equalTo(false))
        assertThat(result.permission, equalTo(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME))
    }

    @Test
    fun `setAlwaysAllow persists choice`() {
        unmockkObject(MeteredSyncPolicy)
        Prefs.allowSyncOnMeteredConnections = false
        MeteredSyncPolicy.setAlwaysAllow(true)
        assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(true))
    }

    /** Records the policy decision without creating UI or starting a sync. */
    private fun attemptMeteredSync(skipPrompt: Boolean = false): MeteredSyncAttemptResult {
        var confirmationRequired = false
        var permission: MeteredSyncPermission? = null
        MeteredSyncPolicy.confirmThen(
            skipPrompt = skipPrompt,
            onConfirmationRequired = { confirmationRequired = true },
        ) { permission = it }
        return MeteredSyncAttemptResult(
            confirmationRequired = confirmationRequired,
            permission = permission,
        )
    }

    private class MeteredSyncAttemptResult(
        val confirmationRequired: Boolean,
        val permission: MeteredSyncPermission?,
    ) {
        val onConfirmCalled: Boolean get() = permission != null
    }
}
