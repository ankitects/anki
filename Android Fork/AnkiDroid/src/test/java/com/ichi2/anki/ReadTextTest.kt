// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ReadText.closeForTests
import com.ichi2.anki.ReadText.initializeTts
import com.ichi2.anki.ReadText.releaseTts
import com.ichi2.anki.ReadText.textToSpeech
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.reviewer.CardSide
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowToast
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ReadTextTest : RobolectricTest() {
    @Before
    fun init() {
        // Investigate: This is fine here, but previously caused test failures.
        // E/SQLiteConnectionPool: Failed to close connection, its fate is now in the hands of the merciful GC: SQLiteConnection
        // java.lang.IllegalStateException: Illegal connection pointer 1163. Current pointers for thread Thread[SDK 29 Main Thread,5,SDK 29] []
        //    at org.robolectric.shadows.ShadowSQLiteConnection$Connections.getConnection(ShadowSQLiteConnection.java:369)
        //    at org.robolectric.shadows.ShadowSQLiteConnection$Connections.getStatement(ShadowSQLiteConnection.java:378)
        //    at org.robolectric.shadows.ShadowSQLiteConnection$Connections.finalizeStmt(ShadowSQLiteConnection.java:491)
        //    at org.robolectric.shadows.ShadowSQLiteConnection.nativeFinalizeStatement(ShadowSQLiteConnection.java:124)
        //    at android.database.sqlite.SQLiteConnection.nativeFinalizeStatement(SQLiteConnection.java)
        //    at android.database.sqlite.SQLiteConnection.finalizePreparedStatement(SQLiteConnection.java:1032)
        //    at android.database.sqlite.SQLiteConnection.access$100(SQLiteConnection.java:91)
        //    at android.database.sqlite.SQLiteConnection$PreparedStatementCache.entryRemoved(SQLiteConnection.java:1361)
        //    at android.database.sqlite.SQLiteConnection$PreparedStatementCache.entryRemoved(SQLiteConnection.java:1350)
        //    at android.util.LruCache.trimToSize(LruCache.java:222)
        //    at android.util.LruCache.evictAll(LruCache.java:310)
        //    at android.database.sqlite.SQLiteConnection.dispose(SQLiteConnection.java:248)
        //    at android.database.sqlite.SQLiteConnection.close(SQLiteConnection.java:209)
        //    at android.database.sqlite.SQLiteConnectionPool.closeConnectionAndLogExceptionsLocked(SQLiteConnectionPool.java:610)
        //    at android.database.sqlite.SQLiteConnectionPool.closeAvailableConnectionsAndLogExceptionsLocked(SQLiteConnectionPool.java:548)
        //    at android.database.sqlite.SQLiteConnectionPool.dispose(SQLiteConnectionPool.java:253)
        //    at android.database.sqlite.SQLiteConnectionPool.close(SQLiteConnectionPool.java:232)
        //    at android.database.sqlite.SQLiteDatabase.dispose(SQLiteDatabase.java:356)
        //    at android.database.sqlite.SQLiteDatabase.onAllReferencesReleased(SQLiteDatabase.java:333)
        //    at android.database.sqlite.SQLiteClosable.releaseReference(SQLiteClosable.java:76)
        //    at android.database.sqlite.SQLiteClosable.close(SQLiteClosable.java:108)
        closeForTests()
        initializeTts(targetContext, mock(AbstractFlashcardViewer.ReadTextListener::class.java))
    }

    @Test
    fun saveValue() {
        assertThat(MetaDB.getLanguage(targetContext, 1, 1, CardSide.QUESTION), equalTo(""))
        storeLanguage(1, "French")
        assertThat(MetaDB.getLanguage(targetContext, 1, 1, CardSide.QUESTION), equalTo("French"))
        storeLanguage(1, "German")
        assertThat(MetaDB.getLanguage(targetContext, 1, 1, CardSide.QUESTION), equalTo("German"))
        storeLanguage(2, "English")
        assertThat(MetaDB.getLanguage(targetContext, 2, 1, CardSide.QUESTION), equalTo("English"))
    }

    @Test
    fun testIsTextToSpeechReleased_sameContext() {
        val context = targetContext
        initializeTextToSpeech(context)
        releaseTts(context)
        assertThat(isTextToSpeechShutdown, equalTo(true))
    }

    @Test
    fun testIsTextToSpeechReleased_differentContext() {
        initializeTextToSpeech(targetContext)
        releaseTts(mock(Context::class.java))
        assertThat(isTextToSpeechShutdown, equalTo(false))
    }

    @Test
    fun `legacy TTS initialization failure explains missing engines`() =
        withTtsActivity { activity ->
            initializeTextToSpeech(activity)
            shadowOf(textToSpeech).onInitListener.onInit(TextToSpeech.ERROR)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("No text-to-speech engine installed", dialog.title)
        }

    @Test
    fun `legacy TTS initialization failure with an installed engine shows a toast instead of installation guidance`() =
        withTtsActivity { activity ->
            shadowOf(activity.packageManager).apply {
                val engine = ComponentName("test.tts", "TestTtsService")
                addServiceIfNotPresent(engine)
                addIntentFilterForService(
                    engine,
                    IntentFilter(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).apply { addCategory(Intent.CATEGORY_DEFAULT) },
                )
            }
            initializeTextToSpeech(activity)
            shadowOf(textToSpeech).onInitListener.onInit(TextToSpeech.ERROR)

            assertEquals(activity.getString(CommonString.no_tts_available_message), ShadowToast.getTextOfLatestToast())
            assertNull(ShadowDialog.getLatestDialog())
        }

    @Test
    @Config(shadows = [NoLanguageQueryTextToSpeech::class])
    fun `reviewer TTS waits for cached languages without blocking the callback`() =
        runTest {
            val (viewer, lifecycle) = createViewer()
            val locales = CompletableDeferred<List<Locale>>()
            try {
                mockkObject(TtsVoices) {
                    coEvery { TtsVoices.localesForEngine(any()) } coAnswers { locales.await() }
                    initializeTextToSpeech(viewer)

                    shadowOf(textToSpeech).onInitListener.onInit(TextToSpeech.SUCCESS)
                    runCurrent()
                    verify(viewer, never()).ttsInitialized()

                    locales.complete(listOf(Locale.US))
                    runCurrent()
                    verify(viewer).ttsInitialized()
                    coVerify { TtsVoices.localesForEngine("reviewer.engine") }
                }
            } finally {
                lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }

    @Test
    @Config(shadows = [NoLanguageQueryTextToSpeech::class])
    fun `releasing TTS cancels the reviewer wait without cancelling discovery`() = assertWaitCancelled { viewer, _ -> releaseTts(viewer) }

    @Test
    @Config(shadows = [NoLanguageQueryTextToSpeech::class])
    fun `destroying the reviewer cancels its wait without cancelling discovery`() =
        assertWaitCancelled { _, lifecycle -> lifecycle.currentState = Lifecycle.State.DESTROYED }

    private fun assertWaitCancelled(stop: (AbstractFlashcardViewer, LifecycleRegistry) -> Unit) =
        runTest {
            val (viewer, lifecycle) = createViewer()
            val locales = CompletableDeferred<List<Locale>>()
            try {
                mockkObject(TtsVoices) {
                    coEvery { TtsVoices.localesForEngine(any()) } coAnswers { locales.await() }
                    initializeTextToSpeech(viewer)
                    shadowOf(textToSpeech).onInitListener.onInit(TextToSpeech.SUCCESS)
                    runCurrent()

                    stop(viewer, lifecycle)
                    runCurrent()
                    assertFalse(locales.isCancelled)
                    locales.complete(listOf(Locale.US))
                    runCurrent()

                    verify(viewer, never()).ttsInitialized()
                }
            } finally {
                lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }

    @Test
    @Config(shadows = [NoLanguageQueryTextToSpeech::class])
    fun `an old TTS callback cannot initialize its replacement`() =
        runTest {
            val (viewer, lifecycle) = createViewer()
            try {
                mockkObject(TtsVoices) {
                    coEvery { TtsVoices.localesForEngine(any()) } returns listOf(Locale.US)
                    initializeTextToSpeech(viewer)
                    val oldCallback = shadowOf(textToSpeech).onInitListener
                    initializeTextToSpeech(viewer)

                    oldCallback.onInit(TextToSpeech.SUCCESS)
                    runCurrent()
                    verify(viewer, never()).ttsInitialized()

                    shadowOf(textToSpeech).onInitListener.onInit(TextToSpeech.SUCCESS)
                    runCurrent()
                    verify(viewer).ttsInitialized()
                }
            } finally {
                lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }

    @Implements(TextToSpeech::class)
    class NoLanguageQueryTextToSpeech : ShadowTextToSpeech() {
        @Implementation
        fun getDefaultEngine(): String = "reviewer.engine"

        @Implementation
        fun getAvailableLanguages(): Set<Locale> = throw AssertionError("Reviewer must use the startup language cache")
    }

    private fun createViewer(): Pair<AbstractFlashcardViewer, LifecycleRegistry> {
        val viewer = mock(AbstractFlashcardViewer::class.java)
        val lifecycle = LifecycleRegistry(viewer)
        `when`(viewer.lifecycle).thenReturn(lifecycle)
        lifecycle.currentState = Lifecycle.State.RESUMED
        return viewer to lifecycle
    }

    private val isTextToSpeechShutdown: Boolean
        get() = shadowOf(textToSpeech).isShutdown

    private fun withTtsActivity(block: (AnkiActivity) -> Unit) {
        buildActivity(AnkiActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            try {
                block(activity)
            } finally {
                releaseTts(activity)
            }
        }
    }

    private fun initializeTextToSpeech(context: Context) {
        initializeTts(context, mock(AbstractFlashcardViewer.ReadTextListener::class.java))
    }

    private fun storeLanguage(
        i: Int,
        french: String,
    ) {
        MetaDB.storeLanguage(targetContext, i.toLong(), 1, CardSide.QUESTION, french)
        advanceRobolectricLooper()
        advanceRobolectricLooper()
    }
}
