// SPDX-License-Identifier: GPL-3.0-or-later

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
