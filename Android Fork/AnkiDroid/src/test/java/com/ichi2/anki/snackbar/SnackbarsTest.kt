// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.snackbar

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SnackbarsTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), CommonR.style.Theme_AnkiDroid)
    private val host = CoordinatorLayout(context)
    private val looper = shadowOf(Looper.getMainLooper())

    @Before
    fun disableSnackbarAnimations() {
        shadowOf(context.getSystemService(AccessibilityManager::class.java))
            .setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    }

    @Test
    fun `Material only queues the latest pending snackbar`() {
        // Material 1.14.0 has one current slot and one pending slot, so retaining only
        // the latest pending snackbar is sufficient. Exercise Material directly here.
        val first = Snackbar.make(host, "First", Snackbar.LENGTH_INDEFINITE)
        val second = Snackbar.make(host, "Second", Snackbar.LENGTH_INDEFINITE)
        val third = Snackbar.make(host, "Third", Snackbar.LENGTH_INDEFINITE)
        first.show()
        looper.idle()

        second.show()
        third.show()

        assertTrue(first.isShown)
        assertFalse(second.isShownOrQueued)
        assertTrue(third.isShownOrQueued)

        looper.idle()
        assertSame(host, third.view.parent)
        assertNull(second.view.parent)
    }

    @Test
    fun `pending snackbar is retained by the root until shown`() {
        val child = View(context).also(host::addView)
        val snackbar = child.showSnackbar("Message")
        assertSame(snackbar, host.getTag(CommonR.id.pending_snackbar))

        looper.idle()
        snackbar.view.layout(0, 0, 300, 60)

        assertNull(host.getTag(CommonR.id.pending_snackbar))
    }

    @Test
    fun `showing an older snackbar does not release its replacement`() {
        val first = host.showSnackbar("First")
        looper.idle()
        val second = host.showSnackbar("Second")
        assertSame(second, host.getTag(CommonR.id.pending_snackbar))

        // Showing the first snackbar must not release the second, which is still queued.
        first.view.layout(0, 0, 300, 60)
        assertSame(second, host.getTag(CommonR.id.pending_snackbar))

        // Finish dismissing the first and attach the second, without laying it out yet.
        looper.idle()
        assertSame(second, host.getTag(CommonR.id.pending_snackbar))
    }

    @Test
    fun `dismissing an older snackbar does not release its replacement`() {
        host.showSnackbar("First")
        looper.idle()
        val second = host.showSnackbar("Second")

        looper.idle()

        assertSame(second, host.getTag(CommonR.id.pending_snackbar))
    }

    @Test
    fun `dismissal before layout releases the pending snackbar`() {
        val snackbar = host.showSnackbar("Message")
        looper.idle()
        assertSame(snackbar, host.getTag(CommonR.id.pending_snackbar))

        snackbar.dismiss()
        looper.idle()

        assertNull(host.getTag(CommonR.id.pending_snackbar))
    }
}
