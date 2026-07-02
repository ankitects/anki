/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package net.ankiweb.rsdroid.ankiutil

import android.os.Build
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * This interface defines a set of functions that are not available on all platforms.
 *
 *
 * A set of implementations for the supported platforms are available.
 *
 *
 * Each implementation ends with a `V<n>` prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see [CompatV16].
 *
 *
 * Each implementation should extend the previous implementation and implement this interface.
 *
 *
 * Each implementation should only override the methods that first become available in its own version, use @Override.
 *
 *
 * Methods not supported by its API will default to the empty implementations of CompatV8.  Methods first supported
 * by lower APIs will default to those implementations since we extended them.
 *
 *
 * Example: CompatV21 extends CompatV19. This means that the setSelectableBackground function using APIs only available
 * in API 21, should be implemented properly in CompatV19 with @Override annotation. On the other hand a method
 * like showViewWithAnimation that first becomes available in API 19 need not be implemented again in CompatV21,
 * unless the behaviour is supposed to be different there.
 */
interface Compat {
    @Throws(IOException::class)
    fun copyFile(
        source: String,
        target: String,
    )

    @Throws(IOException::class)
    fun copyFile(
        source: String,
        target: OutputStream,
    ): Long

    @Throws(IOException::class)
    fun copyFile(
        source: InputStream,
        target: String,
    ): Long
}

fun getCompat(): Compat =
    if (Build.VERSION.SDK_INT >= 26) {
        CompatV26()
    } else {
        CompatV16()
    }
