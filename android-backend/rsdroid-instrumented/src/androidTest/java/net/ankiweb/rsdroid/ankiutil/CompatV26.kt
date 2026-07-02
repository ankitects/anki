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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class CompatV26 : CompatV16() {
    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: String,
    ) {
        Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: OutputStream,
    ): Long = Files.copy(Paths.get(source), target)

    @Throws(IOException::class)
    override fun copyFile(
        source: InputStream,
        target: String,
    ): Long = Files.copy(source, Paths.get(target), StandardCopyOption.REPLACE_EXISTING)
}
