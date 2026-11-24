// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.MATCH_DEFAULT_ONLY_L
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Checks local-file resolution through the installed APK's manifest. */
@RunWith(AndroidJUnit4::class)
class ImportIntentFilterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val schemes = listOf("content", "file")
    private val extensions = listOf("apkg", "colpkg", "tsv", "csv", "txt")

    @Test
    fun supportedLocalFilesResolveToIntentHandler() {
        for (scheme in schemes) {
            for (extension in extensions) {
                for (path in listOf("/deck.$extension", "/Download/deck.$extension", "/Download/名前 with spaces.$extension")) {
                    val uri = localUri(scheme, path)
                    assertNotNull(resolveImport(uri), "No import handler for $uri")
                }
            }
        }
    }

    @Test
    fun unsupportedFileExtensionsDoNotResolveToIntentHandler() {
        for (scheme in schemes) {
            for (filename in listOf("deck", "deck.pdf") + extensions.map { "deck.$it.bak" }) {
                val uri = localUri(scheme, "/Download/$filename")
                assertNull(resolveImport(uri), "Unexpected import handler for $uri")
            }
        }
    }

    private fun localUri(
        scheme: String,
        path: String,
    ): Uri =
        Uri
            .Builder()
            .scheme(scheme)
            .authority(if (scheme == "content") "com.example.imports" else "")
            .path(path)
            .build()

    private fun resolveImport(uri: Uri): ResolveInfo? {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                // Avoid the separate MIME-only filter, which would hide broken path matching.
                setDataAndType(uri, "application/x-ankidroid-import-test")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(context.packageName)
            }
        return context.packageManager
            .queryIntentActivitiesCompat(
                intent,
                ResolveInfoFlagsCompat.of(MATCH_DEFAULT_ONLY_L),
            ).singleOrNull { it.activityInfo.name == IntentHandler::class.java.name }
    }
}
