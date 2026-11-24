// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.ichi2.anki.common.android.AdaptionUtil
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registers an exported activity that handles HTTP(S) URLs in Robolectric. */
fun Context.registerWebBrowser() {
    val browser = ComponentName("com.ichi2.testutils.browser", "BrowserActivity")
    shadowOf(packageManager).apply {
        addActivityIfNotPresent(browser).exported = true
        addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            },
        )
    }
    AdaptionUtil.resetWebBrowserAvailabilityCacheForTests()
    assertTrue(AdaptionUtil.hasWebBrowser(this), "A browser must be available to test external navigation")
}

/** Consumes and checks the only activity started since the previous assertion. */
fun Context.assertOpenedUrl(url: Uri) {
    val application = shadowOf(applicationContext as Application)
    val intent = assertNotNull(application.nextStartedActivity, "No activity started for $url")
    assertEquals(Intent.ACTION_VIEW, intent.action, url.toString())
    assertEquals(url, intent.data, url.toString())
    assertNull(application.nextStartedActivity, "More than one activity started for $url")
}

fun Context.assertNoActivityStarted(message: String? = null) {
    assertNull(shadowOf(applicationContext as Application).nextStartedActivity, message)
}
