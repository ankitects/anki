// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class MetaTest : InMemoryAnkiTest() {
    @Test
    fun ensureDatabaseIsInMemory() {
        val path = col.db.queryString("select file from pragma_database_list")
        assertThat("Default test database should be in-memory.", path, equalTo(""))
    }
}
