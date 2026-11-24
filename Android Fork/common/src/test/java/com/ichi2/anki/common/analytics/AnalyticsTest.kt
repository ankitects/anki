// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

import org.junit.Test

/**
 * Analytics is reported from the crash path, which can run before startup has
 * registered an implementation. Nothing here may throw, or it would mask the
 * crash being reported.
 *
 * This class must never call [Analytics.setAnalytics]: the accessor is a singleton
 * and registering would leak into the other cases.
 */
class AnalyticsTest {
    @Test
    fun `sending an event without an implementation does nothing`() {
        Analytics.send(AnalyticsEvent.SettingTapped("key"))
    }

    @Test
    fun `sending a screen view without an implementation does nothing`() {
        Analytics.sendAnalyticsScreenView("Screen")
        Analytics.sendAnalyticsScreenView(this)
    }

    @Test
    fun `reporting an exception without an implementation does nothing`() {
        Analytics.sendAnalyticsException(RuntimeException("boom"), fatal = true)
    }
}
