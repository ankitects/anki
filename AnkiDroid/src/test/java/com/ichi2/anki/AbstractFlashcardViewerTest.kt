//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.webkit.RenderProcessGoneDetail
import androidx.annotation.CheckResult
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import anki.config.ConfigKey
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AbstractFlashcardViewer.Companion.toAnimationTransition
import com.ichi2.anki.AbstractFlashcardViewer.Signal
import com.ichi2.anki.AbstractFlashcardViewer.Signal.Companion.toSignal
import com.ichi2.anki.AnkiActivity.Companion.FINISH_ANIMATION_EXTRA
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.createBasicTypingNoteType
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.anki.noteeditor.openNoteEditorWithArgs
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.reviewer.AutomaticAnswer
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.AutomaticAnswerSettings
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import timber.log.Timber
import java.util.Locale
import java.util.stream.Stream

@Suppress("SameParameterValue")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O) // getImeHintLocales, toLanguageTags, onRenderProcessGone, RenderProcessGoneDetail
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    class NonAbstractFlashcardViewer : AbstractFlashcardViewer() {
        var answered: Rating? = null
        private var lastTime = 0

        override fun performReload() {
            // intentionally blank
        }

        val typedInput get() = typedInputText

        override fun answerCard(rating: Rating) {
            super.answerCard(rating)
            answered = rating
        }

        override val elapsedRealTime: Long
            get() {
                lastTime +=
                    baseContext
                        .sharedPrefs()
                        .getInt("doubleTapTimeout", DEFAULT_DOUBLE_TAP_TIME_INTERVAL)
                return lastTime.toLong()
            }
        val hintLocale: String?
            get() {
                val imeHintLocales = answerField!!.imeHintLocales ?: return null
                return imeHintLocales.toLanguageTags()
            }

        fun hasAutomaticAnswerQueued(): Boolean = automaticAnswer.timeoutHandler.hasMessages(0)

        /**
         * Fixes an issue with noAutomaticAnswerAfterRenderProcessGoneAndPaused_issue9632
         * where [onMediaGroupCompleted] executed AFTER [executeCommand] completed
         * this lead to an assertion which sometimes occurred before [onMediaGroupCompleted] had
         * been called, which failed
         *
         * This is fine in real life, as we have media to play
         */
        private var mediaGroupCompleted = false

        override fun onMediaGroupCompleted() {
            super.onMediaGroupCompleted()
            mediaGroupCompleted = true
        }

        override fun executeCommand(
            which: ViewerCommand,
            fromGesture: Gesture?,
        ): Boolean {
            mediaGroupCompleted = false
            return super.executeCommand(which, fromGesture).also {
                if (which != ViewerCommand.SHOW_ANSWER) return@also
                Timber.v("waiting for onMediaGroupCompleted")
                for (i in 0..100) {
                    if (mediaGroupCompleted) break
                    Thread.sleep(10)
                }
                require(mediaGroupCompleted) { "mediaGroupCompleted never occurred" }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getSignalFromUrlTest_args")
    fun getSignalFromUrlTest(
        url: String,
        signal: Signal,
    ) {
        assertEquals(url.toSignal(), signal)
    }

    @Test
    fun invalidEncodingDoesNotCrash() {
        // #5944 - input came in as: 'typeblurtext:%'. We've fixed the encoding, but want to make sure there's no crash
        // as JS can call this function with arbitrary data.
        val url = "typeblurtext:%"
        val viewer: NonAbstractFlashcardViewer = getViewer(true)
        assertDoesNotThrow { viewer.handleUrlFromJavascript(url) }
    }

    @Test
    fun validEncodingSetsAnswerCorrectly() {
        // 你好%
        val url = "typechangetext:%E4%BD%A0%E5%A5%BD%25"
        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        viewer.handleUrlFromJavascript(url)

        assertThat(viewer.typedInput, equalTo("你好%"))
    }

    @Test
    fun testEditingCardChangesTypedAnswer() =
        runTest {
            // 7363
            addBasicWithTypingNote("Hello", "World")

            val viewer: NonAbstractFlashcardViewer = getViewer(true)

            assertThat(viewer.correctTypedAnswer, equalTo("World"))

            advanceRobolectricLooper()

            val note = viewer.currentCard!!.note()
            note.setField(1, "David")
            undoableOp { updateNote(note) }

            advanceRobolectricLooper()

            assertThat(viewer.correctTypedAnswer, equalTo("David"))
        }

    @Test
    fun testEditingCardChangesTypedAnswerOnDisplayAnswer() =
        runTest {
            // 7363
            addBasicWithTypingNote("Hello", "World")

            val viewer: NonAbstractFlashcardViewer = getViewer(true)

            assertThat(viewer.correctTypedAnswer, equalTo("World"))

            viewer.displayCardAnswer()

            assertThat(viewer.cardContent, containsString("World"))

            advanceRobolectricLooper()

            val note = viewer.currentCard!!.note()
            note.setField(1, "David")
            undoableOp { updateNote(note) }

            advanceRobolectricLooper()

            assertThat(viewer.correctTypedAnswer, equalTo("David"))
            assertThat(viewer.cardContent, not(containsString("World")))
            // the saving will have caused the screen to switch back to question side
            assertThat(viewer.cardContent, containsString("Hello"))
        }

    @Test
    fun testEditCardProvidesInverseTransition() {
        val viewer: NonAbstractFlashcardViewer = getViewer(true)
        val gestures = listOf(Gesture.SWIPE_LEFT, Gesture.SWIPE_UP, Gesture.DOUBLE_TAP)

        gestures.forEach { gesture ->
            val expectedAnimation =
                AbstractFlashcardViewer.getAnimationTransitionFromGesture(gesture)
            val expectedInverseAnimation =
                ActivityTransitionAnimation.getInverseTransition(expectedAnimation)

            val animation = gesture.toAnimationTransition().invert()
            val bundle =
                Bundle().apply {
                    putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                    putLong(NoteEditorFragment.EXTRA_CARD_ID, viewer.currentCard!!.id)
                    putParcelable(FINISH_ANIMATION_EXTRA, animation as Parcelable)
                }
            val noteEditor = openNoteEditorWithArgs(bundle)
            val actualInverseAnimation =
                BundleCompat.getParcelable(
                    noteEditor.requireArguments(),
                    FINISH_ANIMATION_EXTRA,
                    TransitionDirection::class.java,
                )
            assertEquals(expectedInverseAnimation, actualInverseAnimation)
        }
    }

    @Test
    fun testCommandPerformsAnswerCard() {
        // Regression for #8527/#8572
        // Note: Couldn't get a spy working, so overriding the method

        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        assertThat("Displaying question", viewer.isDisplayingAnswer, equalTo(false))
        viewer.executeCommand(ViewerCommand.ANSWER_EASY)

        assertThat("Displaying answer", viewer.isDisplayingAnswer, equalTo(true))

        viewer.executeCommand(ViewerCommand.ANSWER_EASY)

        assertThat(viewer.answered, notNullValue())
    }

    @Test
    fun defaultLanguageIsNull() {
        assertThat(viewer.hintLocale, nullValue())
    }

    @Test
    @Flaky(OS.ALL, "executeCommand(FLIP_OR_ANSWER_EASE4) cannot be awaited")
    fun typedLanguageIsSet() =
        runTest {
            val withLanguage = col.createBasicTypingNoteType("a")
            val normal = col.createBasicTypingNoteType("b")
            val typedField = 1 // BACK

            LanguageHintService.setLanguageHintForField(col.notetypes, withLanguage, typedField, Locale.JAPANESE)

            addNoteUsingNoteTypeName(withLanguage.name, "ichi", "ni")
            addNoteUsingNoteTypeName(normal.name, "one", "two")
            val viewer = getViewer(false)

            assertThat("A note type with a language hint (japanese) should use it", viewer.hintLocale, equalTo("ja"))

            viewer.executeCommand(ViewerCommand.ANSWER_EASY)
            viewer.executeCommand(ViewerCommand.ANSWER_EASY)

            assertThat("A default note type should have no preference", viewer.hintLocale, nullValue())
        }

    @Test
    fun automaticAnswerDisabledProperty() {
        val controller = getViewerController(addCard = true, startedWithShortcut = false)
        val viewer = controller.get()
        viewer.automaticAnswer.enable()
        assertThat("not disabled initially", viewer.automaticAnswer.isDisabled, equalTo(false))
        controller.pause()
        assertThat("disabled after pause", viewer.automaticAnswer.isDisabled, equalTo(true))
        controller.resume()
        assertThat("enabled after resume", viewer.automaticAnswer.isDisabled, equalTo(false))
    }

    @Test
    @Flaky(OS.ALL) // Flaky on MACOS and WINDOWS, not seen a breakage on LINUX
    fun noAutomaticAnswerAfterRenderProcessGoneAndPaused_issue9632() =
        runTest {
            val controller = getViewerController(addCard = true, startedWithShortcut = false)
            val viewer = controller.get()
            viewer.automaticAnswer = AutomaticAnswer(viewer, AutomaticAnswerSettings(AutomaticAnswerAction.BURY_CARD, 5.0, 5.0))
            viewer.lifecycle.addObserver(viewer.automaticAnswer)
            viewer.automaticAnswer.enable()
            viewer.executeCommand(ViewerCommand.SHOW_ANSWER)
            assertThat("messages after flipping card", viewer.hasAutomaticAnswerQueued(), equalTo(true))
            controller.pause()
            assertThat("disabled after pause", viewer.automaticAnswer.isDisabled, equalTo(true))
            assertThat("no auto answer after pause", viewer.hasAutomaticAnswerQueued(), equalTo(false))
            viewer.onRenderProcessGoneDelegate.onRenderProcessGone(viewer.webView!!, mock(RenderProcessGoneDetail::class.java))
            assertThat("no auto answer after onRenderProcessGone when paused", viewer.hasAutomaticAnswerQueued(), equalTo(false))
        }

    @Test
    fun `Show audio play buttons preference handling - sound`() =
        runTest {
            addBasicWithTypingNote("SOUND [sound:android_audiorec.3gp]", "back")
            getViewerContent().let { content ->
                assertThat("show audio preference default value: enabled", content, containsString("playsound:q:0"))
                assertThat("show audio preference default value: enabled", content, containsString("SOUND"))
            }
            setHidePlayAudioButtons(true)
            getViewerContent().let { content ->
                assertThat("show audio preference disabled", content, not(containsString("playsound:q:0")))
                assertThat("show audio preference disabled", content, containsString("SOUND"))
            }
            setHidePlayAudioButtons(false)
            getViewerContent().let { content ->
                assertThat("show audio preference enabled explicitly", content, containsString("playsound:q:0"))
                assertThat("show audio preference enabled explicitly", content, containsString("SOUND"))
            }
        }

    @Test
    fun `Show audio play buttons preference handling - tts`() =
        runTest {
            addTextToSpeechNote("TTS", "BACK")
            getViewerContent().let { content ->
                assertThat("show audio preference default value: enabled", content, containsString("playsound:q:0"))
                assertThat("show audio preference default value: enabled", content, containsString("TTS"))
            }
            setHidePlayAudioButtons(true)
            getViewerContent().let { content ->
                assertThat("show audio preference disabled", content, not(containsString("playsound:q:0")))
                assertThat("show audio preference disabled", content, containsString("TTS"))
            }
            setHidePlayAudioButtons(false)
            getViewerContent().let { content ->
                assertThat("show audio preference enabled explicitly", content, containsString("playsound:q:0"))
                assertThat("show audio preference enabled explicitly", content, containsString("TTS"))
            }
        }

    private fun setHidePlayAudioButtons(value: Boolean) = col.config.setBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS, value)

    private fun getViewerContent(): String? {
        // PERF: Optimise this to not create a new viewer each time
        return getViewer(addCard = false).cardContent
    }

    @get:CheckResult
    private val viewer: NonAbstractFlashcardViewer
        get() = getViewer(true)

    @CheckResult
    private fun getViewer(addCard: Boolean): NonAbstractFlashcardViewer = getViewer(addCard, false)

    @CheckResult
    private fun getViewer(
        addCard: Boolean,
        startedWithShortcut: Boolean,
    ): NonAbstractFlashcardViewer = getViewerController(addCard, startedWithShortcut).get()

    @CheckResult
    private fun getViewerController(
        addCard: Boolean,
        startedWithShortcut: Boolean,
    ): ActivityController<NonAbstractFlashcardViewer> {
        if (addCard) {
            val n = col.newNote()
            n.setField(0, "a")
            col.addNote(n)
        }
        val intent = Intent()
        if (startedWithShortcut) {
            intent.putExtra(NavigationDrawerActivity.EXTRA_STARTED_WITH_SHORTCUT, true)
        }
        val multimediaController =
            Robolectric
                .buildActivity(NonAbstractFlashcardViewer::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(multimediaController)
        val viewer = multimediaController.get()
        viewer.onCollectionLoaded(col)
        viewer.loadInitialCard()
        // Without this, AbstractFlashcardViewer.mCard is still null, and RobolectricTest.tearDown executes before
        // AsyncTasks spawned by by loading the viewer finish. Is there a way to synchronize these things while under test?
        advanceRobolectricLooper()
        advanceRobolectricLooper()
        return multimediaController
    }

    companion object {
        @JvmStatic // required for @MethodSource
        fun getSignalFromUrlTest_args() =
            Stream.of(
                Arguments.of("signal:show_answer", Signal.SHOW_ANSWER),
                Arguments.of("signal:typefocus", Signal.TYPE_FOCUS),
                Arguments.of("signal:relinquishFocus", Signal.RELINQUISH_FOCUS),
                Arguments.of("signal:answer_ease1", Signal.ANSWER_ORDINAL_1),
                Arguments.of("signal:answer_ease2", Signal.ANSWER_ORDINAL_2),
                Arguments.of("signal:answer_ease3", Signal.ANSWER_ORDINAL_3),
                Arguments.of("signal:answer_ease4", Signal.ANSWER_ORDINAL_4),
                Arguments.of("signal:answer_ease0", Signal.SIGNAL_NOOP),
            )
    }
}

fun AbstractFlashcardViewer.loadInitialCard() = launchCatchingTask { updateCardAndRedraw() }

val AbstractFlashcardViewer.typedInputText get() = typeAnswer!!.input
val AbstractFlashcardViewer.correctTypedAnswer get() = typeAnswer!!.correct
