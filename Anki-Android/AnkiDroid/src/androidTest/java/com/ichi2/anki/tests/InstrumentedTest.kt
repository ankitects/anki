/*
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.tests

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.testutil.addNote
import com.ichi2.anki.utils.EnsureAllFilesAccessRule
import com.ichi2.testutils.common.IgnoreFlakyTestsInCIRule
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.BackendException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.test.fail

abstract class InstrumentedTest : DeferredNavigation {
    internal val col: Collection
        get() = CollectionManager.getColUnsafe()

    @get:Throws(IOException::class)
    protected val emptyCol: Collection
        get() = Shared.getEmptyCol()

    @get:Rule
    val ensureAllFilesAccessRule = EnsureAllFilesAccessRule()

    /** Allows [com.ichi2.testutils.Flaky] to annotate tests in subclasses */
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    /**
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     * emptied on every invocation of this method so it is suitable to use at the start of each test.
     * Only add files (and not subdirectories) to this directory.
     */
    protected val testDir: File
        get() = Shared.getTestDir(testContext)
    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        /**
         * This is how google detects emulators in flutter and how react-native does it in the device info module
         * https://github.com/react-native-device-info/react-native-device-info/blob/9b17b707fdd3a1427064a333a01bfd98ab81e6a8/android/src/main/java/com/learnium/RNDeviceInfo/RNDeviceModule.java#L298-L321
         * @return boolean true if the execution environment is most likely an emulator
         */
        @Suppress("DEPRECATION")
        @SuppressLint("LocaleRootUsage")
        fun isEmulator(): Boolean =
            (
                Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.lowercase(Locale.ROOT).contains("droid4x") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    Build.HARDWARE.contains("goldfish") ||
                    Build.HARDWARE.contains("ranchu") ||
                    Build.HARDWARE.contains("vbox86") ||
                    Build.PRODUCT.contains("sdk") ||
                    Build.PRODUCT.contains("google_sdk") ||
                    Build.PRODUCT.contains("sdk_google") ||
                    Build.PRODUCT.contains("sdk_x86") ||
                    Build.PRODUCT.contains("vbox86p") ||
                    Build.PRODUCT.contains("emulator") ||
                    Build.PRODUCT.contains("simulator") ||
                    Build.BOARD.lowercase(Locale.ROOT).contains("nox") ||
                    Build.BOOTLOADER.lowercase(Locale.ROOT).contains("nox") ||
                    Build.HARDWARE.lowercase(Locale.ROOT).contains("nox") ||
                    Build.PRODUCT.lowercase(Locale.ROOT).contains("nox") ||
                    Build.SERIAL.lowercase(Locale.ROOT).contains("nox") ||
                    (
                        Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                        // Ignored - needs inputMethodManager
                        // || this.hasKeyboard("memuime"))
                    )
            )
    }

    @Before
    open fun runBeforeEachTest() {
        closeAndroidNotRespondingDialog()
        // resolved issues with the collection being reused if useInMemoryDatabase is false
        CollectionManager.setColForTests(null)
    }

    @After
    fun runAfterEachTest() {
        try {
            if (CollectionManager.isOpenUnsafe()) {
                CollectionManager.getColUnsafe().debugEnsureNoOpenPointers()
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            Timber.i("closeCollection: %s", "InstrumentedTest: End")
            CollectionManager.closeCollectionBlocking()
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
            disableNullCollection()
        }
        runBlocking { CollectionManager.discardBackend() }
    }

    /** Restore regular collection behavior  */
    private fun disableNullCollection() {
        CollectionManager.emulatedOpenFailure = null
    }

    // Instrumented tests can fail if there's a "App not responding"
    // System dialog blocking our test from proceeding
    //
    // See: https://stackoverflow.com/questions/39457305/android-testing-waited-for-the-root-of-the-view-hierarchy-to-have-window-focus/54203607#54203607
    private fun closeAndroidNotRespondingDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var waitButton = device.findObject(UiSelector().textContains("Wait"))
        // There may be multiple dialogs
        while (waitButton.exists()) {
            waitButton.click()
            waitButton = device.findObject(UiSelector().textContains("Wait"))
        }
    }

    @DuplicatedCode("This should be refactored into a shared library later")
    fun Card.update(block: Card.() -> Unit): Card {
        block(this)
        col.updateCard(this)
        return this
    }

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    protected fun Card.moveToReviewQueue() {
        this.queue = QueueType.Rev
        this.type = CardType.Rev
        this.due = 0
        col.updateCard(this, true)
    }

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    internal fun addNoteUsingBasicNoteType(
        front: String = "Front",
        back: String = "Back",
    ): Note = addNoteUsingNoteTypeName("Basic", front, back)

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    private fun addNoteUsingNoteTypeName(
        name: String,
        vararg fields: String,
    ): Note {
        val noteType =
            col.notetypes.byName(name)
                ?: throw IllegalArgumentException("Could not find note type '$name'")
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(noteType)
        for ((i, field) in fields.withIndex()) {
            n.setField(i, field)
        }
        check(col.addNote(n) != 0) { "Could not add note: {${fields.joinToString(separator = ", ")}}" }
        return n
    }

    internal fun addTypedAnswerNote(
        front: String = "Front",
        answer: String = "Answer",
    ): Note = addNoteUsingNoteTypeName("Basic (type in the answer)", front, answer)

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    fun addClozeNote(
        text: String,
        extra: String = "Extra",
    ): Note =
        col.newNote(col.notetypes.byName("Cloze")!!).apply {
            setItem("Text", text)
            col.addNote(this)
        }

    /** Helper method to update a note */
    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    @SuppressLint("CheckResult")
    fun Note.update(block: Note.() -> Unit): Note {
        block(this)
        col.updateNote(this)
        return this
    }

    val notetypes get() = col.notetypes

    val Notetypes.basic
        get() = byName("Basic")!!

    val Notetypes.cloze
        get() = byName("Cloze")!!
}

/**
 * Execute [viewAssertion] every [retryWaitTimeInMilliseconds] ms (by default 100),
 * last try being after [maxWaitTimeInMilliseconds] (by default 10 seconds).
 */
fun ViewInteraction.checkWithTimeout(
    viewAssertion: ViewAssertion,
    retryWaitTimeInMilliseconds: Long = 100,
    maxWaitTimeInMilliseconds: Long = TimeUnit.SECONDS.toMillis(10),
) {
    assertThat(
        "The retry time is greater than the max wait time. You probably gave the argument in the wrong order.",
        retryWaitTimeInMilliseconds,
        lessThanOrEqualTo(maxWaitTimeInMilliseconds),
    )
    val startTime = TimeManager.time.intTimeMS()

    do {
        val timedOut = TimeManager.time.intTimeMS() - startTime >= maxWaitTimeInMilliseconds
        try {
            check(viewAssertion)
            return
        } catch (e: Throwable) {
            if (timedOut) {
                fail("View assertion was not true within $maxWaitTimeInMilliseconds milliseconds")
            } else {
                Thread.sleep(retryWaitTimeInMilliseconds)
            }
        }
    } while (true)
}
