// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.ReflectionCollector
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AcraBuildConfigTest {
    @Test
    fun `only reviewed build metadata is collected`() {
        val config = CoreConfigurationBuilder().withBuildConfigClass(BuildConfig::class.java).build()
        val report = CrashReportData()

        ReflectionCollector().collect(ReportField.BUILD_CONFIG, getApplicationContext(), config, ReportBuilder(), report)

        val buildConfig = report[ReportField.BUILD_CONFIG.name] as JSONObject
        assertThat(
            "Review new BuildConfig fields for credentials and personal data before adding them here",
            buildConfig.keys().asSequence().toList(),
            containsInAnyOrder(
                "ACRA_URL",
                "APPLICATION_ID",
                "BACKEND_VERSION",
                "BUILD_TIME",
                "BUILD_TYPE",
                "CI",
                "DEBUG",
                "ENABLE_LEAK_CANARY",
                "FLAVOR",
                "GIT_COMMIT_HASH",
                "SHOW_DONATE_LINKS",
                "VERSION_CODE",
                "VERSION_NAME",
            ),
        )
    }
}
