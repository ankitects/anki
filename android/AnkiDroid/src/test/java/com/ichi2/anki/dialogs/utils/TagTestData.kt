/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs.utils

import com.ichi2.utils.FileOperation.Companion.getFileResource
import java.io.File

// this has to be a file, as it can't be a Kotlin list
// Method too large: com/ichi2/anki/dialogs/utils/TagDataKt.<clinit> ()V

/** 16971 tags */
val AnKingTags =
    lazy {
        val fileName = getFileResource("anking_v11_tags.txt")
        return@lazy File(fileName).readLines()
    }
