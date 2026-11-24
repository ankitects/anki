// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class StorageRustTest : InMemoryAnkiTest() {
    @Test
    fun testModelCount() {
        val noteTypeNames = col.notetypes.all().map { x -> x.name }
        MatcherAssert.assertThat(
            noteTypeNames,
            Matchers.containsInAnyOrder(
                "Basic",
                "Basic (and reversed card)",
                "Cloze",
                "Basic (type in the answer)",
                "Basic (optional reversed card)",
                "Image Occlusion",
            ),
        )
    }
}
