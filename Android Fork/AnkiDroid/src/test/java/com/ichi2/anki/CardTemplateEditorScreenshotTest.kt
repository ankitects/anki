// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.core.app.ActivityScenario
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.utils.getInputField
import com.ichi2.utils.positiveButton
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * Screenshot tests for [CardTemplateEditor]
 *
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CardTemplateEditorScreenshotTest"`
 */
class CardTemplateEditorScreenshotTest : ScreenshotTest() {
    @Test
    fun repositionFullSyncConfirmation() =
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            col.setSchemaModified()
            col.db.execute("update col set ls = scm")
            shadowOf(this).clickMenuItem(R.id.action_reposition)
            advanceRobolectricLooper()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getInputField().setText("2")
            dialog.positiveButton.performClick()
            advanceRobolectricLooper()
            captureScreen("reposition_full_sync_confirmation")
        }

    @Test
    fun reposition() =
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            openOptionsMenu()
            advanceRobolectricLooper()
            captureScreen("reposition_menu")
            closeOptionsMenu()
            shadowOf(this).clickMenuItem(R.id.action_reposition)
            advanceRobolectricLooper()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getInputField().setText("2")
            captureScreen("reposition_dialog")
            dialog.positiveButton.performClick()
            advanceRobolectricLooper()
            captureScreen("reposition_result")
        }

    @Test
    fun basic() {
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy("Basic")
        val intent = CardTemplateEditor.getIntent(targetContext, collectionBasicNoteTypeOriginal.id)

        ActivityScenario.launch<CardTemplateEditor>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("basic")
            }
        }
    }

    /**
     * Landscape with 3-button navigation showing the second card.
     */
    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withCardTemplateEditor(noteType = getCurrentDatabaseNoteTypeCopy("Basic (and reversed card)")) {
            mainBinding.cardTemplateEditorPager.setCurrentItem(1, false)
            advanceRobolectricLooper()
            simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    /** How the IME appears while a physical keyboard is attached */
    enum class PhysicalKeyboardIme { BUTTONS, GESTURES, SOFTWARE_KEYBOARD }

    /** The template tabs stay for a physical keyboard, and only hide for the full software keyboard */
    @Test
    fun physicalKeyboard(
        @TestParameter ime: PhysicalKeyboardIme,
    ) {
        RuntimeEnvironment.setQualifiers("+land-keysexposed-qwerty")
        withCardTemplateEditor {
            when (ime) {
                // 3-button navigation reports a visible IME with no height
                PhysicalKeyboardIme.BUTTONS -> dispatchInsets(navBarRight = 48.dp, imeVisible = true)
                // gesture navigation shows only a 48dp strip
                PhysicalKeyboardIme.GESTURES -> dispatchInsets(navBarBottom = 24.dp, imeBottom = 48.dp)
                // the user may still opt into the software keyboard
                PhysicalKeyboardIme.SOFTWARE_KEYBOARD -> simulateKeyboard(keyboardHeight = 240.dp, navBarBottom = 24.dp)
            }
            advanceRobolectricLooper()
            // TODO: with the software keyboard open, this landscape viewport is too short for a
            // complete line of the template.
            captureScreen("physical_keyboard_${ime.name.lowercase()}")
        }
    }

    /** The entire template scrolls above the keyboard while the template controls are hidden. */
    @Test
    fun keyboard() =
        withCardTemplateEditor(noteType = getCurrentDatabaseNoteTypeCopy("Basic (and reversed card)")) {
            mainBinding.cardTemplateEditorPager.setCurrentItem(1, false)
            advanceRobolectricLooper()
            val binding = currentFragment!!.binding
            binding.editText.append(
                (1..40).joinToString(separator = "\n", prefix = "\n", postfix = "\n<!-- End of template -->") { "<!-- Line $it -->" },
            )
            binding.editText.setSelection(binding.editText.length())
            advanceRobolectricLooper()
            val keyboard = simulateKeyboard()
            binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
            advanceRobolectricLooper()
            assertFalse("The keyboard hides the template controls", binding.bottomNavigation.isShown)
            captureScreen("keyboard")

            (window.decorView as ViewGroup).removeView(keyboard)
            dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            captureScreen("keyboard_closed")
        }

    @SuppressLint("RtlHardcoded") // insets and cutouts are physical: not layout-direction relative
    private fun CardTemplateEditor.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val cutoutWidth = 32.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(left = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(right = cutoutWidth))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        advanceRobolectricLooper()
        addOverlay(navBarWidth.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT)
        addOverlay(cutoutWidth.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT)
    }

    private fun CardTemplateEditor.addOverlay(
        width: Int,
        height: Int,
        gravity: Int,
    ): View {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width, height, gravity))
        return overlay
    }
}
