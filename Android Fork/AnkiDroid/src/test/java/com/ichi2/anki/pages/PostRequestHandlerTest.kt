// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PostRequestHandlerTest : RobolectricTest() {
    // ts_funcs.txt includes imports from every upstream route, including routes outside SvelteKitPage.
    // Exclude only functions whose callers are confined to routes AnkiDroid does not expose.
    // Call sites checked against Anki 26.09.3: https://github.com/ankitects/anki/tree/26.09.3/ts

    // ts/routes/editor, including DeckChooser and NotetypeChooser (used only by that route).
    private val experimentalEditorFunctions =
        setOf(
            "addMediaFile",
            "addMediaFromPath",
            "addMediaFromUrl",
            "addNote",
            "askUser",
            "closeAddCards",
            "closeEditCurrent",
            "convertPastedImage",
            "decodeIriPaths",
            "defaultDeckForNotetype",
            "defaultsForAdding",
            "encodeIriPaths",
            "extractMediaFiles",
            "getAbsoluteMediaPath",
            "getCard",
            "getClozeFieldOrds",
            "getConfigBool",
            "getDeck",
            "getNote",
            "getNotetype",
            "htmlToTextLine",
            "newNote",
            "noteFieldsCheck",
            "openCardsDialog",
            "openFieldsDialog",
            "openFilePicker",
            "openLink",
            "openMedia",
            "playFile",
            "readClipboard",
            "recordAudio",
            "showInMediaFolder",
            "showMessageBox",
            "updateNotes",
            "updateNotetype",
            "writeClipboard",
        )

    // ts/lib/tslib/profile.ts is imported only by ts/routes/editor and ts/routes/preferences.
    // Neither route is exposed by SvelteKitPage.
    private val editorAndPreferencesFunctions =
        setOf(
            "getConfigJson",
            "getMetaJson",
            "getProfileConfigJson",
            "setConfigJson",
            "setMetaJson",
            "setProfileConfigJson",
        )

    private val unsupportedRouteFunctions = experimentalEditorFunctions + editorAndPreferencesFunctions

    @Test
    fun `All backend typescript functions should be handled`() {
        assertThat(
            "Mapping exists for every TS backend function call",
            typescriptFunctionsUsedByBackend - unsupportedRouteFunctions,
            // this matcher asserts equality in everything but order, no extras, nothing missing
            containsInAnyOrder((collectionMethods + uiMethods).keys),
        )
    }

    @Test
    fun `saveCustomColours does not throw`() =
        runTest {
            // saveCustomColours is a FrontendService which is not implemented, but should not throw
            assertNotNull(handleCollectionPostRequest("saveCustomColours", byteArrayOf()))
        }

    /**
     * Auto-generated list of all TypeScript funcs created & packaged during backend build
     */
    private val typescriptFunctionsUsedByBackend: List<String> =
        InputStreamReader(targetContext.assets.open("backend/ts_funcs.txt")).use {
            it.readLines().also { lines ->
                assertThat("Stored Typescript functions", lines, not(empty()))
            }
        }
}
