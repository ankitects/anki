// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TypeAnswerWebView
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.singleDayText
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.RecordingInputMethodManager
import com.ichi2.testutils.ext.addNoSuggestNote
import com.ichi2.testutils.ext.createInputConnection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReviewerFragmentTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @After
    fun resetSoftwareRendering() {
        Prefs.sharedPrefs.edit {
            remove(Prefs.key(R.string.disable_hardware_render_key))
        }
    }

    @Test
    fun `recreated WebView retains keyboard support`() =
        runTest {
            addBasicWithTypingNote("front", "back")
            withReviewer {
                val layout = binding.webViewLayout
                val original = assertIs<TypeAnswerWebView>(layout.getChildAt(0))
                layout.onRenderProcessGone(original)
                val replacement = assertIs<TypeAnswerWebView>(layout.getChildAt(0))
                assertNotSame(original, replacement)
                assertNotNull(shadowOf(replacement).getJavascriptInterface("AnkiDroidKeyboard"))
            }
        }

    @Test
    fun `software rendering is applied to the study screen when enabled`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = true

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_SOFTWARE)
            }
        }

    @Test
    fun `software rendering is reapplied after the WebView is recreated`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = true

            withReviewer {
                val webViewLayout = binding.webViewLayout
                val originalWebView = assertIs<WebView>(webViewLayout.getChildAt(0))
                webViewLayout.onRenderProcessGone(originalWebView)

                assertNotSame(originalWebView, webViewLayout.getChildAt(0))
                assertRenderingLayerType(View.LAYER_TYPE_SOFTWARE)
            }
        }

    @Test
    fun `card WebView uses the theme background before the card loads`() =
        runTest {
            addBasicNote()
            RuntimeEnvironment.setQualifiers("+night")
            PrefsRepository(targetContext).apply {
                appTheme = AppTheme.NIGHT
                nightTheme = NightTheme.DARK
            }

            withReviewer {
                val background = ThemeUtils.getThemeAttrColor(requireContext(), android.R.attr.colorBackground)
                val webViewLayout = binding.webViewLayout
                assertEquals(background, shadowOf(assertIs<WebView>(webViewLayout.getChildAt(0))).backgroundColor, "card WebView")

                webViewLayout.onRenderProcessGone(assertIs<WebView>(webViewLayout.getChildAt(0)))
                assertEquals(background, shadowOf(assertIs<WebView>(webViewLayout.getChildAt(0))).backgroundColor, "recreated WebView")
            }
        }

    @Test
    fun `default rendering is preserved when software rendering is disabled`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = false

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_NONE)
            }
        }

    @Test
    fun `default rendering is preserved when the rendering preference is unset`() =
        runTest {
            addBasicNote()
            Prefs.sharedPrefs.edit {
                remove(Prefs.key(R.string.disable_hardware_render_key))
            }

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_NONE)
            }
        }

    @Test
    fun `nosuggest supports Done`() =
        runTest {
            targetContext.sharedPrefs().edit { putBoolean("useInputTag", false) }
            col.addNoSuggestNote()
            withReviewer {
                val field = binding.typeAnswerEditText
                assertTrue(field.onCheckIsTextEditor())
                assertNotEquals(InputType.TYPE_NULL, field.inputType)
                val info = EditorInfo()
                val connection = field.createInputConnection(info)
                assertEquals(InputType.TYPE_NULL, info.inputType)

                connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
            }
        }

    @Test
    @Config(shadows = [RecordingInputMethodManager::class])
    fun `nosuggest resets for the next typing card`() =
        runTest {
            targetContext.sharedPrefs().edit { putBoolean("useInputTag", false) }
            val noSuggestCard = col.addNoSuggestNote().firstCard()
            val normalCard = addBasicWithTypingNote("Normal question", "Answer").firstCard()
            withReviewer {
                val field = binding.typeAnswerEditText
                val originalInputType = field.inputType
                val inputMethodManager = Shadow.extract<RecordingInputMethodManager>(field.context.getSystemService<InputMethodManager>())
                assertEquals(noSuggestCard.id, viewModel.getCardId())
                assertTrue(field.onCheckIsTextEditor())
                assertNotEquals(InputType.TYPE_NULL, field.inputType)
                val noSuggestInfo = EditorInfo()
                field.createInputConnection(noSuggestInfo)
                assertEquals(InputType.TYPE_NULL, noSuggestInfo.inputType)

                viewModel.onShowAnswer()
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)

                inputMethodManager.restartedViews.clear()
                viewModel.answerCard(Rating.EASY)
                advanceUntilIdle()
                assertEquals(normalCard.id, viewModel.getCardId())
                assertContains(inputMethodManager.restartedViews, field)
                assertTrue(field.onCheckIsTextEditor())
                assertEquals(originalInputType, field.inputType)
                val normalInfo = EditorInfo()
                field.createInputConnection(normalInfo)
                assertEquals(originalInputType, normalInfo.inputType)
            }
        }

    @Test
    fun `shaking does not stack set due date dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.RESCHEDULE_NOTE)

    @Test
    fun `shaking does not stack edit tags dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.TAG)

    @Test
    fun `repeated set due date actions preserve the open dialog`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            withReviewer {
                viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                advanceUntilIdle()
                advanceRobolectricLooper()
                val dialog = assertIs<SetDueDateDialog>(currentDialog)
                dialog.singleDayText.setText("12")
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertEquals("12", dialog.singleDayText.text.toString())
                assertEquals(listOf(cardId), dialog.cardIds)
            }
        }

    @Test
    fun `repeated edit tags actions preserve unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.TAG)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertTrue(tags.isChecked("unconfirmed"))
            }
        }

    @Test
    fun `edit tags can reopen after dismissal without unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)

                val reopenedDialog = openEditTags(this)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
                val reopenedTags = reopenedDialog.viewModel.tags.await()
                assertFalse(reopenedTags.isChecked("unconfirmed"))
            }
        }

    @Test
    fun `shake reaches the study screen while the whiteboard is hidden`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            bindToStudyScreenAndWhiteboard(ReviewerBinding.fromGesture(Gesture.SHAKE))
            withReviewer {
                hideWhiteboard(this)
                hearShake()
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertEquals(listOf(cardId), assertIs<SetDueDateDialog>(currentDialog).cardIds)
            }
        }

    @Test
    fun `key press reaches the study screen while the whiteboard is hidden`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            bindToStudyScreenAndWhiteboard(ReviewerBinding(Binding.KeyCode(KeyEvent.KEYCODE_F5), CardSide.BOTH))
            withReviewer {
                hideWhiteboard(this)
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F5))
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertEquals(listOf(cardId), assertIs<SetDueDateDialog>(currentDialog).cardIds)
            }
        }

    private fun bindToStudyScreenAndWhiteboard(binding: ReviewerBinding) {
        val bindings = listOf(binding).toPreferenceString()
        targetContext.sharedPrefs().edit {
            putString(ViewerAction.RESCHEDULE_NOTE.preferenceKey, bindings)
            putString(WhiteboardAction.CLEAR.preferenceKey, bindings)
        }
        StudyScreenRepository().isWhiteboardEnabled = true
    }

    private fun TestScope.hideWhiteboard(reviewer: ReviewerFragment) {
        advanceRobolectricLooper()
        val whiteboard =
            assertIs<WhiteboardFragment>(reviewer.childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName))
        reviewer.viewModel.whiteboardEnabledFlow.value = false
        advanceUntilIdle()
        advanceRobolectricLooper()
        assertTrue(whiteboard.isHidden)
    }

    private fun ReviewerFragment.assertRenderingLayerType(expected: Int) {
        assertEquals(expected, requireView().layerType, "study screen root")
        assertEquals(expected, binding.backButton.layerType, "toolbar button")
        assertEquals(expected, binding.answerArea.layerType, "answer buttons")
        assertEquals(expected, assertIs<WebView>(binding.webViewLayout.getChildAt(0)).layerType, "card WebView")
    }

    private suspend fun TestScope.openEditTags(reviewer: ReviewerFragment): TagsDialog {
        reviewer.viewModel.executeAction(ViewerAction.TAG)
        advanceUntilIdle()
        advanceRobolectricLooper()
        return assertIs<TagsDialog>(reviewer.currentDialog)
    }

    private fun assertShakeDoesNotStackDialogs(action: ViewerAction) =
        runTest {
            addBasicNote()
            targetContext.sharedPrefs().edit {
                putString(action.preferenceKey, listOf(ReviewerBinding.fromGesture(Gesture.SHAKE)).toPreferenceString())
            }

            withReviewer { controller ->
                fun shake() {
                    hearShake()
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }

                shake()
                val dialog = assertNotNull(currentDialog)
                assertTrue(dialog.requireDialog().isShowing)
                val backStackCount = parentFragmentManager.backStackEntryCount

                // Android transfers window focus to the dialog while the reviewer remains resumed.
                controller.windowFocusChanged(false)
                repeat(3) { shake() }
                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)
                controller.windowFocusChanged(true)

                shake()
                val reopenedDialog = assertNotNull(currentDialog)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
            }
        }

    private val ReviewerFragment.currentDialog: DialogFragment?
        get() = parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private suspend fun TestScope.withReviewer(block: suspend ReviewerFragment.(ActivityController<CardViewerActivity>) -> Unit) {
        val intent = ReviewerFragment.getIntent(targetContext)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            controller.setup().windowFocusChanged(true)
            advanceUntilIdle()
            val reviewer = controller.get().fragment as ReviewerFragment
            reviewer.viewModel.onPageFinished(false)
            advanceUntilIdle()
            reviewer.block(controller)
        }
    }
}
