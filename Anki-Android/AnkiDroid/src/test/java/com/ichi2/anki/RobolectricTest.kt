/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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

package com.ichi2.anki

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Looper
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.RobolectricTest.CollectionStorageMode.IN_MEMORY_NO_FOLDERS
import com.ichi2.anki.RobolectricTest.CollectionStorageMode.IN_MEMORY_WITH_MEDIA
import com.ichi2.anki.RobolectricTest.CollectionStorageMode.ON_DISK
import com.ichi2.anki.common.annotations.UseContextParameter
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManager
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManagerWithMediaFolder
import com.ichi2.anki.libanki.testutils.TestCollectionManager
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.ProductionCollectionManager
import com.ichi2.testutils.common.FailOnUnhandledExceptionRule
import com.ichi2.testutils.common.IgnoreFlakyTestsInCIRule
import com.ichi2.testutils.filter
import com.ichi2.testutils.grantPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.json.JSONException
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.junit.rules.TimeoutRule
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import timber.log.Timber
import kotlin.test.assertNotNull

open class RobolectricTest :
    AnkiTest,
    AndroidTest {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.wait(timeMs: Long) = (this as Object).wait(timeMs)

    private val controllersForCleanup = ArrayList<ActivityController<*>>()

    protected fun saveControllerForCleanup(controller: ActivityController<*>) {
        controllersForCleanup.add(controller)
    }

    /** Allows [com.ichi2.testutils.common.Flaky] to annotate tests in subclasses */
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    @get:Rule
    val testName = TestName()

    @get:Rule
    val failOnUnhandledExceptions = FailOnUnhandledExceptionRule()

    @get:Rule
    val timeoutRule: TimeoutRule = TimeoutRule.seconds(60)

    @get:Rule
    val tempFolder = TemporaryFolder()

    override val collectionManager: TestCollectionManager by lazy {
        when (getCollectionStorageMode()) {
            ON_DISK -> ProductionCollectionManager as TestCollectionManager
            // tempFolder.newFolder() requires `lazy { }`
            IN_MEMORY_WITH_MEDIA -> InMemoryCollectionManagerWithMediaFolder(tempFolder.newFolder())
            IN_MEMORY_NO_FOLDERS -> InMemoryCollectionManager()
        }
    }

    protected open fun getCollectionStorageMode(): CollectionStorageMode = IN_MEMORY_NO_FOLDERS

    protected enum class CollectionStorageMode {
        IN_MEMORY_NO_FOLDERS,
        IN_MEMORY_WITH_MEDIA,
        ON_DISK,
    }

    @Before
    @CallSuper
    open fun setUp() {
        println("""-- executing test "${testName.methodName}"""")
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))
        throwOnShowError = true

        // See the Android logging (from Timber)
        ShadowLog.stream =
            System.out
                // Filters for non-Timber sources. Prefer filtering in RobolectricDebugTree if possible
                // LifecycleMonitor: not needed as we already use registerActivityLifecycleCallbacks for logs
                // W/ShadowLegacyPath: android.graphics.Path#op() not supported yet.
                .filter("^(?!(W/ShadowLegacyPath|D/LifecycleMonitor)).*$")

        ChangeManager.resetForTesting()

        validateRunWithAnnotationPresent()

        val config =
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, config)

        // resolved issues with the collection being reused if useInMemoryDatabase is false
        CollectionManager.setColForTests(null)

        maybeSetupBackend()

        // Reset static variable for custom tabs failure.
        CustomTabActivityHelper.resetFailed()

        // See: #6140 - This global ideally shouldn't exist, but it will cause crashes if set.
        DialogHandler.discardMessage()

        // BUG: We do not reset the MetaDB
        MetaDB.closeDB()

        // https://github.com/ankidroid/Anki-Android/pull/19004#discussion_r2739833965
        grantPermissions(Manifest.permission.INTERNET)
    }

    @After
    @CallSuper
    open fun tearDown() {
        throwOnShowError = false
        // If you don't clean up your ActivityControllers you will get OOM errors
        for (controller in controllersForCleanup) {
            Timber.d("Calling destroy on controller %s", controller.get().toString())
            try {
                controller.destroy()
            } catch (e: Exception) {
                // Any exception here is likely because the test code already destroyed it, which is fine
                // No exception here should halt test execution since tests are over anyway.
            }
        }
        controllersForCleanup.clear()

        if (AnkiDroidApp.sharedPreferencesTestingOverride != null) {
            Timber.w("AnkiDroidApp SharedPrefs test override was not reset to null! Setting it to null.")
            AnkiDroidApp.sharedPreferencesTestingOverride = null
        }

        try {
            if (CollectionManager.isOpenUnsafe()) {
                CollectionManager.getColUnsafe().debugEnsureNoOpenPointers()
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            Timber.i("closeCollection: %s", "RobolectricTest: End")
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

            // called on each AnkiDroidApp.onCreate(), and spams the build
            // there is no onDestroy(), so call it here.
            Timber.uprootAll()

            TimeManager.reset()
        }
        WorkManagerTestInitHelper.closeWorkDatabase()
        Dispatchers.resetMain()
        runBlocking { CollectionManager.discardBackend() }
        println("""-- completed test "${testName.methodName}"""")
    }

    /**
     * Click on a dialog button for an AlertDialog dialog box. Replaces the above helper.
     */
    protected fun clickAlertDialogButton(
        button: Int,
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ) {
        val dialog = getLatestAlertDialog()

        dialog.getButton(button).performClick()
        // Need to run UI thread tasks to actually run the onClickHandler
        ShadowLooper.runUiThreadTasks()

        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", Shadows.shadowOf(dialog).hasBeenDismissed())
        }
    }

    /**
     * Get the current dialog text for AlertDialogs (which are replacing MaterialDialogs). Will return null if no dialog visible
     * *or* if you check for dismissed and it has been dismissed
     *
     * @param checkDismissed true if you want to check for dismissed, will return null even if dialog exists but has been dismissed
     * TODO: Rename to getDialogText when all MaterialDialogs are changed to AlertDialogs
     */
    protected fun getAlertDialogText(
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ): String? {
        val dialog = getLatestAlertDialog()
        if (checkDismissed && Shadows.shadowOf(dialog).hasBeenDismissed()) {
            Timber.e("The latest dialog has already been dismissed.")
            return null
        }
        val messageViewWithinDialog = dialog.findViewById<TextView>(android.R.id.message)
        Assert.assertFalse(messageViewWithinDialog == null)

        return messageViewWithinDialog?.text?.toString()
    }

    companion object {
        // Robolectric needs a manual advance in PAUSED looper mode
        fun advanceRobolectricLooper() {
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        }

        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun <T : Activity?> startActivityNormallyOpenCollectionWithIntent(
            testClass: RobolectricTest,
            clazz: Class<T>?,
            i: Intent?,
        ): T {
            if (AbstractFlashcardViewer::class.java.isAssignableFrom(clazz!!)) {
                // fixes 'Don't know what to do with dataSource...' inside Sounds.kt
                // solution from https://github.com/robolectric/robolectric/issues/4673
                ShadowMediaPlayer.setMediaInfoProvider {
                    ShadowMediaPlayer.MediaInfo(1, 0)
                }
            }
            val controller =
                Robolectric
                    .buildActivity(clazz, i)
                    .create()
                    .start()
                    .resume()
                    .visible()
            advanceRobolectricLooper()
            testClass.saveControllerForCleanup(controller)
            return controller.get()
        }
    }

    val targetContext: Context
        get() = ApplicationProvider.getApplicationContext()

    val resources: Resources get() = targetContext.resources

    /**
     * Returns an instance of [SharedPreferences] using the test context
     * @see [editPreferences] for editing
     */
    fun getPreferences(): SharedPreferences = targetContext.sharedPrefs()

    protected fun getResourceString(res: Int): String = targetContext.getString(res)

    protected fun getQuantityString(
        res: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = targetContext.resources.getQuantityString(res, quantity, *formatArgs)

    /** A collection. Created one second ago, not near cutoff time.
     * Each time time is checked, it advance by 10 ms. Not enough to create any change visible to user, but ensure
     * we don't get two equal time. */

    override val col: Collection
        get() =
            try {
                collectionManager.getColUnsafe()
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Failed to load collection. Did you call super.setUp()?", e)
            }

    protected val collectionTime: MockTime
        get() = TimeManager.time as MockTime

    /** Call this method in your test if you to test behavior with a null collection  */
    protected fun enableNullCollection() {
        CollectionManager.closeCollectionBlocking()
        CollectionManager.setColForTests(null)
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.LOCKED
    }

    /** Restore regular collection behavior  */
    protected fun disableNullCollection() {
        CollectionManager.emulatedOpenFailure = null
    }

    /**
     * Emulates a null collection and a `BackendDbLockedException` while [block] runs,
     * restoring normal collection behavior afterwards.
     *
     * @see enableNullCollection
     */
    protected inline fun withNullCollection(block: () -> Unit) =
        try {
            enableNullCollection()
            block()
        } finally {
            disableNullCollection()
        }

    @Throws(JSONException::class)
    protected fun getCurrentDatabaseNoteTypeCopy(noteTypeName: String): NotetypeJson {
        val collectionModels = col.notetypes
        return collectionModels.byName(noteTypeName)!!.deepClone()
    }

    internal fun <T : Activity?> startActivityNormallyOpenCollectionWithIntent(
        clazz: Class<T>?,
        i: Intent?,
    ): T = startActivityNormallyOpenCollectionWithIntent(this, clazz, i)

    internal inline fun <reified T : Activity?> startRegularActivity(): T = startRegularActivity(null)

    internal inline fun <reified T : Activity?> startRegularActivity(i: Intent? = null): T =
        startActivityNormallyOpenCollectionWithIntent(T::class.java, i)

    fun equalFirstField(
        expected: Card,
        obtained: Card,
    ) {
        MatcherAssert.assertThat(obtained.note().fields[0], Matchers.equalTo(expected.note().fields[0]))
    }

    /**
     * Allows editing of preferences, followed by a call to [apply][SharedPreferences.Editor.apply]:
     *
     * ```
     * editPreferences { putString("key", value) }
     * ```
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun editPreferences(action: SharedPreferences.Editor.() -> Unit) = getPreferences().edit(action = action)

    protected fun grantRecordAudioPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val app = Shadows.shadowOf(application)
        app.grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    private fun validateRunWithAnnotationPresent() {
        try {
            ApplicationProvider.getApplicationContext<Application>()
        } catch (e: IllegalStateException) {
            if (e.message != null && e.message!!.startsWith("No instrumentation registered!")) {
                // Explicitly ignore the inner exception - generates line noise
                throw IllegalStateException("Annotate class: '${javaClass.simpleName}' with '@RunWith(AndroidJUnit4::class)'")
            }
            throw e
        }
    }

    /** Helper method to update a note */
    @SuppressLint("CheckResult")
    @UseContextParameter("TestClass")
    suspend fun Note.updateOp(block: Note.() -> Unit): Note =
        this.also { note ->
            block(note)
            undoableOp<OpChanges> { col.updateNote(note) }
        }

    private fun maybeSetupBackend() {
        try {
            targetContext
        } catch (exc: IllegalStateException) {
            // We must make sure not to load the backend library into a test running outside
            // the Robolectric classloader, or subsequent Robolectric tests that run in this
            // process will be unable to make calls into the backend.
            println("not annotated with junit, not setting up backend")
            return
        }
        try {
            RustBackendLoader.ensureSetup()
        } catch (e: UnsatisfiedLinkError) {
            if (e.message.toString().contains("library load disallowed by system policy")) {
                throw IllegalStateException(
                    """library load disallowed by system policy.
"To fix:
* Run the test such that the "developer cannot be verified" message appears
* Press "OK" on the "Apple cannot check it for malicious software" prompt
* Run the Test Again
* Apple Menu - System Preferences - Security & Privacy - General (tab) - Unlock Settings - Select Allow Anyway". 
    Button is underneath the text: "librsdroid.dylib was blocked from use because it is not from an identified developer"
* Press "OK" on the "Apple cannot check it for malicious software" prompt
* Test should execute correctly""",
                )
            }
            throw e
        }
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        ioDispatcher = dispatcher
    }

    override suspend fun TestScope.runTestInner(testBody: suspend TestScope.() -> Unit) {
        (collectionManager as? ProductionCollectionManager)
            ?.setTestDispatcher(UnconfinedTestDispatcher(testScheduler))
        testBody()
    }
}

private fun getLatestAlertDialog(): AlertDialog =
    assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog, "A dialog should be displayed")
