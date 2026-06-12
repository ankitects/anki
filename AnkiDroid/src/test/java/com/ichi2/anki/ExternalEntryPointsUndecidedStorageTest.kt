// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentProvider
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.ichi2.anki.storage.StorageDecision
import com.ichi2.testutils.ExternalEntryPoints.EntryPoint
import com.ichi2.testutils.grantPermissions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Handles the [CollectionHelper.storageDecision] returning [StorageDecision.Undecided].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ExternalEntryPointsUndecidedStorageTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var entryPoint: EntryPoint? = null

    // Only used for display, but needs to be defined
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField // required for Parameter
    @Suppress("unused")
    var entryPointName: String? = null

    @Before
    fun setStorageUndecided() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
    }

    @After
    fun resetStorageDecision() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    @Test
    fun `startup is crash-free when storage is undecided`() {
        when (val entryPoint = entryPoint!!) {
            is EntryPoint.Activity -> launchActivity(entryPoint.className)
            is EntryPoint.ActivityAlias -> launchActivity(entryPoint.targetActivity)
            is EntryPoint.WidgetConfig -> launchActivity(entryPoint.className)
            is EntryPoint.WidgetConfigAlias -> launchActivity(entryPoint.targetActivity)
            is EntryPoint.Receiver -> sendBroadcast(entryPoint.className)
            is EntryPoint.Provider -> queryContentProvider(entryPoint.className)
            is EntryPoint.Service -> fail("define how to drive a Service entry point: $entryPoint")
        }
        advanceRobolectricLooper()
    }

    /**
     * The intent a component receives when triggered externally, based on its manifest
     * `<intent-filter>` (keyed on the component: aliases declare their own filters).
     */
    private fun EntryPoint.externalIntent(): Intent =
        when (className) {
            // launcher icon tap
            "com.ichi2.anki.IntentHandler" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            // share text with AnkiDroid
            "com.ichi2.anki.IntentHandler2", "com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity" ->
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "dog")
            "com.ichi2.anki.Reviewer" -> Intent(Intent.ACTION_VIEW)
            // 'Anki Card' text selection menu entry
            "com.ichi2.anki.AnkiCardContextMenuAction" ->
                Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "dog")
            "com.ichi2.anki.CardBrowserDeepLink" -> Intent(Intent.ACTION_VIEW, "anki://x-callback-url/browser?search=dog".toUri())
            // launched by the system's 'Manage space' settings button, with no action or extras
            "com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity" -> Intent()
            // 'more info' button in the OS permission settings
            "com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity" ->
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
                } else {
                    TODO("VERSION.SDK_INT < Q")
                }
            // the widget host configures a newly added widget
            "com.ichi2.widget.deckpicker.DeckPickerWidgetConfig",
            "com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig",
            ->
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
            "com.ichi2.anki.receiver.SdCardReceiver" -> Intent(Intent.ACTION_MEDIA_EJECT, "file:///storage/emulated/0".toUri())
            // the widget host redraws the widgets
            "com.ichi2.widget.AddNoteWidget", "com.ichi2.widget.AnkiDroidWidgetSmall" ->
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(1))
            else -> fail("define the externally-sent intent for new entry point: $this")
        }

    private fun launchActivity(targetClassName: String) {
        val activityClass = Class.forName(targetClassName).asSubclass(Activity::class.java)
        if (AbstractFlashcardViewer::class.java.isAssignableFrom(activityClass)) {
            // fixes 'Don't know what to do with dataSource...' inside Sounds.kt
            ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1, 0) }
        }
        val controller = Robolectric.buildActivity(activityClass, entryPoint!!.externalIntent())
        saveControllerForCleanup(controller)
        // mirrors Android: an activity which finishes during onCreate (e.g. redirectToMainEntryPoint)
        // does not receive the remaining lifecycle callbacks; controller.setup() would force them
        controller.create()
        if (controller.get().isFinishing) return
        controller
            .start()
            .postCreate(null)
            .resume()
            .visible()
    }

    private fun sendBroadcast(className: String) {
        val receiverClass = Class.forName(className).asSubclass(BroadcastReceiver::class.java)
        val intent = entryPoint!!.externalIntent()
        // the host only requests an update for widgets it has bound
        intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.forEach { appWidgetId ->
            shadowOf(AppWidgetManager.getInstance(targetContext))
                .bindAppWidgetId(appWidgetId, ComponentName(targetContext, receiverClass))
        }
        receiverClass.getDeclaredConstructor().newInstance().onReceive(targetContext, intent)
    }

    private fun queryContentProvider(className: String) {
        grantPermissions(FlashCardsContract.READ_WRITE_PERMISSION)
        val providerClass = Class.forName(className).asSubclass(ContentProvider::class.java)
        val provider =
            Robolectric
                .buildContentProvider(providerClass)
                .create(FlashCardsContract.AUTHORITY)
                .get()
        // IllegalStateException is expected. A custom exception kills the process
        assertFailsWith<IllegalStateException> {
            provider.query(FlashCardsContract.Note.CONTENT_URI, null, null, null, null)?.close()
        }
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> = ExternalEntryPointsTest.EXPECTED.map { arrayOf(it, it.toString()) }
    }
}
