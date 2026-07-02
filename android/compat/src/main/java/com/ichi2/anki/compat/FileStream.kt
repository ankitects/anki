/*
 *  Copyright (c) 2022 Arthur Milchior <Arthur@Milchior.fr>
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

package com.ichi2.anki.compat

import java.io.File
import java.io.IOException

/**
 * A type to read a sequence of [File]. It is essentially [DirectoryStream]<File>, but accessible
 * in all API. The result should be closed after use.
 * This is not standard Iterator, because  `hasNext` and `next` may throw the checked exception [IOException]
 * If `hasNext` returned true, we have two guarantees:
 * * if the next function call on this object is to `hasNext`, it returns true.
 * * if the next call on this object is to `next`, this method should not throw.
 * @see [DirectoryStream]
 * @see [Iterable]
 */
interface FileStream : AutoCloseable {
    /**
     * @see [Iterator.hasNext], but can throw a IOException
     */
    @Throws(IOException::class)
    fun hasNext(): Boolean

    /**
     * Before the first call, and between each call to this method, you should call [hasNext].
     * If [hasNext] ever returned `false` on an object `o`, you should not call [next] ever on `o`.
     * If this instruction is not followed, a [IOException] may be thrown.
     * @See [Iterator.next]
     * @See [DirectoryStream]
     */
    @Throws(IOException::class)
    fun next(): File

    @Throws(IOException::class)
    override fun close()
}
