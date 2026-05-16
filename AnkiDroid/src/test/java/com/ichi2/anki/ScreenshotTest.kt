/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AppTheme
import org.junit.Before
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

interface ScreenshotTestCategory

/**
 * Base class for [roborazzi](https://github.com/takahirom/roborazzi) screenshot tests
 */
@RunWith(RobolectricTestParameterInjector::class)
@Category(ScreenshotTestCategory::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest : RobolectricTest() {
    var fileNamePrefix = ""

    enum class DeviceConfig { PHONE, TABLET, FOLDABLE, DESKTOP }

    @TestParameter(valuesProvider = DeviceProvider::class)
    lateinit var device: DeviceConfig

    @Before
    open fun applyGlobalConfig() {
        applyDeviceConfig()
        applyThemeConfig()
    }

    protected open fun applyDeviceConfig() {
        when (device) {
            DeviceConfig.PHONE -> setPhoneQualifiers()
            DeviceConfig.TABLET -> {
                setTabletQualifiers()
                fileNamePrefix += "tablet_"
            }
            DeviceConfig.FOLDABLE -> {
                setFoldableQualifiers()
                fileNamePrefix += "foldable_"
            }
            DeviceConfig.DESKTOP -> {
                setDesktopQualifiers()
                fileNamePrefix += "desktop_"
            }
        }
    }

    protected open fun applyThemeConfig() {
        if (System.getProperty("screenshot.theme") == "dark") {
            RuntimeEnvironment.setQualifiers("+night")
            Prefs.appTheme = AppTheme.NIGHT
            fileNamePrefix += "dark_"
        }
    }

    /** Pixel-class phone in portrait */
    protected fun setPhoneQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumPhone)

    protected fun setTabletQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumTablet)

    protected fun setFoldableQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.Pixel9ProFold)

    protected fun setDesktopQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumDesktop)

    /**
     * Captures a screenshot to `build/outputs/roborazzi/<TestClass>/<name>.png`.
     *
     * Writes to /diffs/ if there is an issue.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun captureScreen(name: String) {
        // Note: this.javaClass should not be used inside a lambda, as 'this' will be unnamed
        val classDir = "build/outputs/roborazzi/${this.javaClass.simpleName}"
        val diffDir = File("$classDir/diffs")
        // baseline is always in the root for the class, copied to /diffs/ if a change occurred
        val fileName = "$fileNamePrefix$name.png"
        val baseline = File(classDir, fileName)
        captureScreenRoboImage(
            filePath = baseline.path,
            roborazziOptions = provideRoborazziContext().options.withCompareOutputDir(diffDir.path),
        )

        // copy the baseline into /diffs (if it exists)
        // /diffs/ is used so 'clean' baselines are not mixed with diffs to inspect
        val diffWritten =
            File(diffDir, "${name}_compare.png").exists() ||
                File(diffDir, "${name}_actual.png").exists()
        if (diffWritten && baseline.isFile) {
            baseline.copyTo(File(diffDir, baseline.name), overwrite = true)
        }
    }

    class DeviceProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<DeviceConfig> {
            val requestedDevice = System.getProperty("screenshot.device") ?: "phone"
            if (requestedDevice == "all") {
                return DeviceConfig.entries
            }
            val requestedDevices = requestedDevice.split(",").map { it.trim().lowercase() }
            return DeviceConfig.entries.filter { requestedDevices.contains(it.name.lowercase()) }
        }
    }
}

/** Sets the directory for _actual.png and _compare.png */
@OptIn(ExperimentalRoborazziApi::class)
private fun RoborazziOptions.withCompareOutputDir(dir: String): RoborazziOptions =
    copy(compareOptions = compareOptions.copy(outputDirectoryPath = dir))
