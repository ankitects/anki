// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.ExternalEntryPoints
import com.ichi2.testutils.ExternalEntryPoints.EntryPoint
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the set of externally-reachable entry points (see [ExternalEntryPoints]) so the
 * storage-decision work has a known, complete surface to test against.
 *
 * If you added an exported component, ensure you handle when storage is not yet configured.
 */
@RunWith(AndroidJUnit4::class)
class ExternalEntryPointsTest : RobolectricTest() {
    @Test
    fun entryPointsMatchKnownSet() {
        assertThat(
            ExternalEntryPoints.all(targetContext),
            containsInAnyOrder(*EXPECTED.toTypedArray()),
        )
    }

    companion object {
        /**
         * All externally-reachable entry points, consumed by per-scenario tests.
         */
        internal val EXPECTED =
            listOf(
                // Activities reached via the launcher, deep links, share/import, PROCESS_TEXT or shortcuts
                EntryPoint.Activity("com.ichi2.anki.IntentHandler"),
                EntryPoint.Activity("com.ichi2.anki.IntentHandler2"),
                EntryPoint.Activity("com.ichi2.anki.CardBrowser"),
                EntryPoint.Activity("com.ichi2.anki.Reviewer"),
                EntryPoint.ActivityAlias("com.ichi2.anki.AnkiCardContextMenuAction", "com.ichi2.anki.IntentHandler2"),
                EntryPoint.Activity("com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity"),
                EntryPoint.Activity("com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity"),
                EntryPoint.Activity("com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity"),
                // Widget-configuration activities: launched by the widget host (not `exported`), bypassing IntentHandler
                EntryPoint.WidgetConfig("com.ichi2.widget.deckpicker.DeckPickerWidgetConfig"),
                EntryPoint.WidgetConfig("com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig"),
                // Headless: reachable with no UI (third-party API, system broadcasts, widget updates)
                EntryPoint.Provider("com.ichi2.anki.provider.CardContentProvider"),
                EntryPoint.Receiver("com.ichi2.anki.receiver.SdCardReceiver"),
                EntryPoint.Receiver("com.ichi2.widget.AddNoteWidget"),
                EntryPoint.Receiver("com.ichi2.widget.AnkiDroidWidgetSmall"),
            )
    }
}
