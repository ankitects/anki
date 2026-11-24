// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.logging

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.ichi2.anki.NavigationDrawerActivity.Companion.EXTRA_STARTED_WITH_SHORTCUT
import com.ichi2.anki.SingleFragmentActivity.Companion.EXTRA_FRAGMENT_ARGS
import com.ichi2.anki.SingleFragmentActivity.Companion.EXTRA_FRAGMENT_NAME
import timber.log.Timber

/**
 * Logs activity creation and routing metadata, including launches without saved state.
 *
 * Designed to diagnose an activity launch with missing extras
 */
internal fun Activity.logActivityCreation(savedInstanceState: Bundle?) {
    val launchIntent = intent
    // Intent metadata is caller-controlled. Only allowlisted names and fixed labels may be logged.
    val details =
        mapOf(
            "action" to launchIntent?.action?.let { if (it in knownActions) it else "other" },
            "categories" to summarizeNames(launchIntent?.categories, knownCategories),
            "flags" to launchIntent?.flags?.let { "0x${Integer.toHexString(it)}" },
            "extraKeys" to readSafely { summarizeNames(launchIntent?.extras?.keySet(), knownExtraNames) },
            "startedWithShortcut" to
                readSafely {
                    if (launchIntent?.hasExtra(EXTRA_STARTED_WITH_SHORTCUT) == true) {
                        launchIntent.getBooleanExtra(EXTRA_STARTED_WITH_SHORTCUT, false)
                    } else {
                        "absent"
                    }
                },
            "taskId" to readSafely { taskId },
            "isTaskRoot" to readSafely { isTaskRoot },
            "caller" to
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    readSafely {
                        when (launchedFromUid) {
                            Process.INVALID_UID -> "unavailable"
                            Process.myUid() -> "self"
                            Process.ROOT_UID -> "root"
                            Process.SYSTEM_UID -> "system"
                            Process.SHELL_UID -> "shell"
                            else -> "other"
                        }
                    }
                } else {
                    "unavailable"
                },
        )
    Timber.i(
        "%s::onCreate, savedInstanceState: %s, launch: %s",
        javaClass.simpleName,
        readSafely { savedInstanceState?.let { "${it.keySet().size} keys" } },
        details,
    )
}

/** Unparcelling extras or querying the system for diagnostics must not prevent activity startup. */
private fun readSafely(read: () -> Any?): Any? =
    try {
        read()
    } catch (_: Exception) {
        "unavailable"
    }

/** Return only allowlisted names; unknown names may contain personal data. */
private fun summarizeNames(
    names: Set<String>?,
    allowedNames: Set<String>,
): Map<String, Any>? =
    names?.let {
        mapOf(
            "known" to allowedNames.filter { it in names },
            "unknownCount" to names.count { it !in allowedNames },
        )
    }

private val knownActions =
    setOf(
        Intent.ACTION_MAIN,
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        Intent.ACTION_PROCESS_TEXT,
    )

private val knownCategories =
    setOf(
        Intent.CATEGORY_LAUNCHER,
        Intent.CATEGORY_DEFAULT,
        Intent.CATEGORY_BROWSABLE,
    )

private val knownExtraNames =
    setOf(
        EXTRA_FRAGMENT_NAME,
        EXTRA_FRAGMENT_ARGS,
        EXTRA_STARTED_WITH_SHORTCUT,
    )
