/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
