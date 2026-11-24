// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.logging

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.NavigationDrawerActivity.Companion.EXTRA_STARTED_WITH_SHORTCUT
import com.ichi2.anki.SingleFragmentActivity.Companion.EXTRA_FRAGMENT_ARGS
import com.ichi2.anki.SingleFragmentActivity.Companion.EXTRA_FRAGMENT_NAME
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
// Application startup logs can race with the launch diagnostics captured by these tests.
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ActivityLaunchLoggerTest {
    private val messages = mutableListOf<String>()
    private val tree =
        object : Timber.Tree() {
            override fun isLoggable(
                tag: String?,
                priority: Int,
            ): Boolean = priority >= Log.INFO

            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                messages.add(message)
            }
        }

    @Before
    fun setUp() {
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun `caller supplied text and raw caller identity are omitted`() {
        val privateText = "private-person@example.test"
        val intent =
            Intent("${Intent.ACTION_VIEW}.$privateText").apply {
                component = ComponentName("package.$privateText", "activity.$privateText")
                data = "https://example.test/$privateText".toUri()
                addCategory("${Intent.CATEGORY_LAUNCHER}.$privateText")
                putExtra("$EXTRA_FRAGMENT_NAME.$privateText", privateText)
                putExtra(EXTRA_FRAGMENT_NAME, privateText)
                putExtra(EXTRA_FRAGMENT_ARGS, Bundle().apply { putString(privateText, privateText) })
                putExtra(Intent.EXTRA_REFERRER, "android-app://$privateText".toUri())
            }
        val activity = mockActivity(intent)
        every { activity.referrer } returns "android-app://private-referrer.example.test".toUri()
        every { activity.launchedFromPackage } returns "private.caller.package"
        every { activity.launchedFromUid } returns 1234567

        activity.logActivityCreation(Bundle().apply { putString(privateText, privateText) })

        val message = assertNotNull(messages.singleOrNull(), "Expected one log message; captured: $messages")
        for (privateValue in listOf(privateText, "private-referrer", "private.caller.package", "1234567")) {
            assertFalse(message.contains(privateValue), "Launch diagnostics leaked $privateValue")
        }
        assertTrue(message.contains(EXTRA_FRAGMENT_NAME), "Known extra names should remain available")
        assertTrue(message.contains("unknownCount=1"), "Unknown names should be counted")
    }

    @Test
    fun `known shortcut routing metadata remains useful`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
                putExtra(EXTRA_FRAGMENT_NAME, "private fragment value")
                putExtra(EXTRA_STARTED_WITH_SHORTCUT, true)
            }

        mockActivity(intent).logActivityCreation(null)

        val message = assertNotNull(messages.singleOrNull(), "Expected one log message; captured: $messages")
        assertTrue(message.contains(Intent.ACTION_VIEW))
        assertTrue(message.contains(Intent.CATEGORY_LAUNCHER))
        assertTrue(message.contains(EXTRA_FRAGMENT_NAME))
        assertTrue(message.contains("startedWithShortcut=true"))
        assertTrue(message.contains("flags=0x100000"))
        assertFalse(message.contains("private fragment value"))
    }

    private fun mockActivity(launchIntent: Intent): Activity =
        mockk<Activity>(relaxed = true).also { activity ->
            every { activity.intent } returns launchIntent
        }
}
