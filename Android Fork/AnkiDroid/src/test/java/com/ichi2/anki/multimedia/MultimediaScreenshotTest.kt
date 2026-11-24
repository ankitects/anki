// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.insetsOf
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.assertTrue

/**
 * Screenshot tests for [MultimediaActivity]
 *
 * Covers [AudioRecordingFragment] with different system insets and [MultimediaImageFragment]'s
 * bottom toolbar with an image selected.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.multimedia.MultimediaScreenshotTest"`
 */
class MultimediaScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun imageEditorToolbar() =
        withImage { activity ->
            activity.simulateNavigationBar()
            captureScreen("image_editor")
        }

    @Test
    fun imageEditorCameraToolbar() =
        withImage(MultimediaImageFragment.ImageOptions.CAMERA) { activity ->
            activity.simulateNavigationBar()
            captureScreen("image_editor_camera")
        }

    @Test
    fun imageEditorDrawingToolbar() =
        withImage(MultimediaImageFragment.ImageOptions.DRAWING) { activity ->
            activity.simulateNavigationBar()
            captureScreen("image_editor_drawing")
        }

    @Test
    fun imageEditorError() =
        withImage { activity ->
            val snackbar =
                requireNotNull(
                    activity.showSnackbar(
                        activity.getString(CommonString.multimedia_editor_something_wrong),
                        Snackbar.LENGTH_INDEFINITE,
                    ),
                )
            advanceRobolectricLooper()
            captureScreen("image_editor_error")

            val controls = activity.findViewById<View>(R.id.image_actions)
            val controlsBounds = Rect().also { controls.getGlobalVisibleRect(it) }
            val snackbarBounds = Rect().also { snackbar.view.getGlobalVisibleRect(it) }
            assertTrue(
                snackbarBounds.bottom <= controlsBounds.top,
                "Errors must leave the image controls visible: snackbar=$snackbarBounds, controls=$controlsBounds",
            )
        }

    private fun withImage(
        imageOptions: MultimediaImageFragment.ImageOptions = MultimediaImageFragment.ImageOptions.GALLERY,
        block: (MultimediaActivity) -> Unit,
    ) {
        // Each Robolectric test has its own cache directory; discard AndroidX's cached roots.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
        targetContext.registerExternalImageEditor()
        val image = File(targetContext.cacheDir, "image_editor_test.png")
        val bitmap = createBitmap(600, 400)
        bitmap.eraseColor(Color.rgb(100, 149, 237))
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val field = ImageField().apply { mediaFile = image }
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, field)
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(0, field, note, Uri.fromFile(image).toString())
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                MultimediaImageFragment.getIntent(targetContext, extra, imageOptions),
            )
        advanceRobolectricLooper()
        val editLabel = TR.studyingEdit()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(editLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(editLabel).assertIsDisplayed().assertIsEnabled()
        block(activity)
    }

    /** 3-button navigation: a tall bottom inset which `action_done` rests above */
    @Test
    fun multimediaPortrait() =
        withMultimedia { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /** Gesture navigation: a short inset, but larger rounded corners to clear */
    @Test
    fun multimediaGestureNavigation() =
        withMultimedia { activity ->
            activity.simulateGestureNavigationBar()
            captureScreen("gesture_navigation")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The app bar spans both, its content clears them.
     */
    @Test
    fun multimediaLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withMultimedia { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    private fun withMultimedia(block: (MultimediaActivity) -> Unit) {
        // the recorder is only built when the permission is held; without it the fragment
        // renders an empty card
        grantPermissions(RECORD_AUDIO)
        // the recorder reads the note's initial field values to show "Field Contents",
        // so the note needs a frozen field rather than being empty
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, TextField().apply { text = "Front of the card" })
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(index = 0, field = AudioRecordingField(), note = note)
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                AudioRecordingFragment.getIntent(targetContext, extra),
            )
        advanceRobolectricLooper()
        block(activity)
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a device, and overlay a translucent band where
     * the navigation bar would sit, to show whether anything is drawn underneath it.
     */
    private fun MultimediaActivity.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but gesture navigation: a short inset, larger rounded corners */
    private fun MultimediaActivity.simulateGestureNavigationBar() {
        val navBarHeight = 24.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .apply {
                        val radius = 34.dp.toPx(targetContext)
                        setRoundedCorner(
                            RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                            RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                        )
                    }.build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but landscape: a side navigation bar and an opposite cutout */
    private fun MultimediaActivity.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(navBarWidth.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END)
    }

    private fun MultimediaActivity.addNavBarOverlay(
        width: Int,
        height: Int,
        gravity: Int,
    ) {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width, height, gravity))
    }
}
