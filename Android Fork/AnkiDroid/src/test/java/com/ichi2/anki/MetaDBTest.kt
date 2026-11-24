// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetaDBTest {
    private val app: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        MetaDB.closeDB()
    }

    @After
    fun tearDown() {
        MetaDB.closeDB()
    }

    @Test
    fun `profiles keep their own deck settings`() {
        val defaultDeckId = 1L
        MetaDB.storeWhiteboardState(profile("p_work"), defaultDeckId, true)
        MetaDB.closeDB()

        assertFalse(MetaDB.getWhiteboardState(profile("p_home"), defaultDeckId))
        MetaDB.closeDB()

        assertTrue(MetaDB.getWhiteboardState(profile("p_work"), defaultDeckId))
    }

    private fun profile(id: String) = ProfileContextWrapper.create(app, ProfileId(id), File(app.filesDir.parentFile, id))
}
