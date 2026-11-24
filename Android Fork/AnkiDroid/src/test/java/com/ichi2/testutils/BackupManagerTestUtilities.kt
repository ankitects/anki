// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import com.ichi2.anki.BackupManager.Companion.enoughDiscSpace
import com.ichi2.anki.common.storage.CollectionHelper
import org.junit.Assert.assertTrue
import java.lang.IllegalStateException

object BackupManagerTestUtilities {
    fun setupSpaceForBackup(context: Context) {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)

        val path =
            currentAnkiDroidDirectory.parentFile
                ?: throw IllegalStateException("currentAnkiDroidDirectory had no parent")
        ShadowStatFs.markAsNonEmpty(path)

        assertTrue(enoughDiscSpace(currentAnkiDroidDirectory))
    }

    fun reset() {
        ShadowStatFs.reset()
    }
}
