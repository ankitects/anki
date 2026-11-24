// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.core.view.allViews
import androidx.core.view.drawToBitmap
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import org.junit.Before
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File

/**
 * Base class for [roborazzi](https://github.com/takahirom/roborazzi) screenshot tests
 */
@RunWith(RobolectricTestParameterInjector::class)
@Category(ScreenshotTestCategory::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest : RobolectricTest() {
    companion object {
        /**
         * Fixed `versionName` reported to screenshot tests, so screens which display
         * the app version do not change on each release.
         */
        const val STABLE_VERSION_NAME = "X.Y.Z-screenshot"
    }

    var fileNamePrefix = ""

    enum class DeviceConfig(
        private val screen: String,
        private val density: String,
    ) {
        PHONE("w411dp-h914dp-normal-long-notround-any", "420dpi"),
        TABLET("w1280dp-h800dp-xlarge-notlong-notround-any", "xhdpi"),
        FOLDABLE("w852dp-h883dp-large-notlong-notround-any", "390dpi"),
        DESKTOP("w1920dp-h1080dp-xlarge-long-notround-any", "xhdpi"),
        ;

        fun qualifiers(night: Boolean) = listOfNotNull(screen, "night".takeIf { night }, density, "keyshidden-nonav").joinToString("-")
    }

    /**
     * Devices always covered by the annotated class, in addition to the `-Pdevice` selection.
     *
     * Usage:
     *
     * ```kt
     * @IncludeDevices(TABLET)
     * class TestClass : ScreenshotTest() {
     * ```
     */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class IncludeDevices(
        vararg val value: DeviceConfig,
    )

    @TestParameter(valuesProvider = ThemeProvider::class)
    lateinit var theme: ThemeConfig

    @TestParameter(valuesProvider = DeviceProvider::class)
    lateinit var device: DeviceConfig

    @Before
    open fun applyGlobalConfig() {
        stabilizeVersionName()
        applyDeviceConfig()
        applyThemeConfig()
    }

    /** Reports [STABLE_VERSION_NAME] as the `versionName` shown on-screen */
    private fun stabilizeVersionName() {
        shadowOf(targetContext.packageManager)
            .getInternalMutablePackageInfo(targetContext.packageName)
            .versionName = STABLE_VERSION_NAME
    }

    protected open fun applyDeviceConfig() {
        setDeviceQualifiers(device)
        if (device != DeviceConfig.PHONE) {
            fileNamePrefix += "${device.name.lowercase()}_"
        }
    }

    // ./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot -Ptheme=black,plain
    protected open fun applyThemeConfig() {
        if (theme != ThemeConfig.LIGHT) {
            fileNamePrefix += "${theme.name.lowercase()}_"
        }
        val prefs = PrefsRepository(targetContext)
        prefs.appTheme = if (theme.isNight) AppTheme.NIGHT else AppTheme.DAY
        when (theme) {
            ThemeConfig.LIGHT -> prefs.dayTheme = DayTheme.LIGHT
            ThemeConfig.PLAIN -> prefs.dayTheme = DayTheme.PLAIN
            ThemeConfig.EINK -> prefs.dayTheme = DayTheme.EINK
            ThemeConfig.DARK -> prefs.nightTheme = NightTheme.DARK
            ThemeConfig.BLACK -> prefs.nightTheme = NightTheme.BLACK
            ThemeConfig.EINK_NIGHT -> prefs.nightTheme = NightTheme.EINK
        }
    }

    /** Applies the orientation and returns its component of the screenshot filename. */
    protected fun setOrientation(landscape: Boolean): String {
        if (landscape) RuntimeEnvironment.setQualifiers("+land")
        return if (landscape) "landscape" else "portrait"
    }

    /** Pixel-class phone in portrait */
    protected fun setPhoneQualifiers() = setDeviceQualifiers(DeviceConfig.PHONE)

    protected fun setTabletQualifiers() = setDeviceQualifiers(DeviceConfig.TABLET)

    private fun setDeviceQualifiers(device: DeviceConfig) = RuntimeEnvironment.setQualifiers(device.qualifiers(night = theme.isNight))

    /**
     * Captures a screenshot to `build/outputs/roborazzi/<TestClass>/<name>.png`.
     *
     * Writes to /diffs/ if there is an issue.
     * Add `-PaccessibilityScan` to scan the live UI and log accessibility findings.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun captureScreen(name: String) =
        capture(name) { filePath, options ->
            captureScreenRoboImage(filePath = filePath, roborazziOptions = options)
            scanAccessibilityIfEnabled(filePath, options)
        }

    /**
     * Scan the live download UI across all themes, logging findings without failing on them:
     * ```sh
     * ./gradlew :AnkiDroid:testPlayDebugUnitTest -Pscreenshot -PaccessibilityScan -Ptheme=all \
     *   --tests '*SharedDecksScreenshotTest.downloadFragment*'
     * ```
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun scanAccessibilityIfEnabled(
        filePath: String,
        options: RoborazziOptions,
    ) {
        if (!java.lang.Boolean.getBoolean("screenshot.accessibilityScan")) return
        check(Build.VERSION.SDK_INT >= 34) { "Accessibility scans require API 34 or newer" }
        println("Accessibility scan: $filePath")
        onView(isRoot()).checkRoboAccessibility(
            roborazziATFAccessibilityCheckOptions =
                RoborazziATFAccessibilityCheckOptions(
                    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.LogOnly,
                ),
            roborazziOptions = options,
        )
    }

    /** Captures a laid-out view, including content extending beyond the screen. */
    protected fun captureView(
        name: String,
        view: View,
    ) = capture(name) { filePath, options ->
        view.drawToBitmap().captureRoboImage(filePath = filePath, roborazziOptions = options)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        captureImage: (String, RoborazziOptions) -> Unit,
    ) {
        // Note: this.javaClass should not be used inside a lambda, as 'this' will be unnamed
        val classDir = "build/outputs/roborazzi/${this.javaClass.simpleName}"
        val diffDir = File("$classDir/diffs")
        // baseline is always in the root for the class, copied to /diffs/ if a change occurred
        val fileName = "$fileNamePrefix$name.png"
        val baseline = File(classDir, fileName)
        disableScrollbarFading()
        restartTextCursorBlink()
        captureImage(
            baseline.path,
            provideRoborazziContext().options.withCompareOutputDir(diffDir.path),
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

    /** [View.disableScrollbarFading] for every window */
    private fun disableScrollbarFading() = windows.forEach { it.disableScrollbarFading() }

    /** [View.restartTextCursorBlink] for every window */
    private fun restartTextCursorBlink() = windows.forEach { it.restartTextCursorBlink() }

    /** The root view of every window: the activity and any dialogs above it */
    private val windows: List<View>
        get() {
            val windowManager = targetContext.getSystemService<WindowManager>()
            return Shadow.extract<ShadowWindowManagerImpl>(windowManager).views
        }

    class DeviceProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<DeviceConfig> {
            val requestedDevice = System.getProperty("screenshot.device") ?: "phone"
            val requestedDevices = requestedDevice.split(",").map { it.trim().lowercase() }
            if ("all" in requestedDevices) {
                return DeviceConfig.entries
            }
            val includedDevices =
                context
                    ?.testClass()
                    ?.getAnnotation(IncludeDevices::class.java)
                    ?.value
                    .orEmpty()
            return DeviceConfig.entries.filter { it in includedDevices || requestedDevices.contains(it.name.lowercase()) }
        }
    }
}

fun View.disableScrollbarFading() {
    allViews
        .filter { it.isScrollbarFadingEnabled }
        .forEach { it.isScrollbarFadingEnabled = false }
}

/** [EditText.displayFocusedCursors] for every text cursor in the hierarchy */
fun View.restartTextCursorBlink() {
    allViews
        .filterIsInstance<EditText>()
        .forEach { it.displayFocusedCursors() }
}

/** Ensures a cursor is always shown if the field is focused. */
fun EditText.displayFocusedCursors() {
    // cursor is not blinking
    if (!isCursorVisible) return
    // reset the blink cycle, so the cursor always appears in the screenshot
    isCursorVisible = false
    isCursorVisible = true
}

/** Sets the directory for _actual.png and _compare.png */
@OptIn(ExperimentalRoborazziApi::class)
private fun RoborazziOptions.withCompareOutputDir(dir: String): RoborazziOptions =
    copy(compareOptions = compareOptions.copy(outputDirectoryPath = dir))
