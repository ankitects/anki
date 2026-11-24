// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimediacard.fields

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class ImageFieldTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `an image field without a file has an empty formatted value`() {
        assertEquals("", ImageField().formattedValue)
    }

    @Test
    fun `a missing image file has an empty formatted value`() {
        val field = ImageField().apply { mediaFile = File(tempFolder.root, "missing.png") }

        assertEquals("", field.formattedValue)
    }

    @Test
    fun `an existing image file is formatted using its filename`() {
        val field = ImageField().apply { mediaFile = tempFolder.newFile("image.png") }

        assertEquals("""<img src="image.png">""", field.formattedValue)
    }
}
