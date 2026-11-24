// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.robolectric.annotation.Resetter
import java.io.File
import org.robolectric.shadows.ShadowStatFs as RobolectricStats

/**
 * Workaround to fix [org.robolectric.shadows.ShadowStatFs] failing on macOS
 */
object ShadowStatFs {
    /**
     * Register stats for a path, which will be used when a matching [android.os.StatFs] instance is
     * created.
     *
     * @param path path to the file
     * @param blockCount number of blocks
     * @param freeBlocks number of free blocks
     * @param availableBlocks number of available blocks
     *
     * @see [markAsNonEmpty]
     */
    fun registerStats(
        path: File,
        blockCount: Int,
        freeBlocks: Int,
        availableBlocks: Int,
    ) {
        RobolectricStats.registerStats(path, blockCount, freeBlocks, availableBlocks)
        // call canonicalFile so this works on macOS
        RobolectricStats.registerStats(path.canonicalFile, blockCount, freeBlocks, availableBlocks)
    }

    /**
     * Marks the provided path in Robolectric as non-empty, therefore [android.os.StatFs] will work
     * and operations such as backups will succeed
     *
     * Call [reset] when this is completed.
     *
     * @param path path to the file
     */
    fun markAsNonEmpty(path: File) = registerStats(path, 100, 20, 10000)

    @Resetter
    fun reset() = RobolectricStats.reset()
}
