// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import org.junit.Test
import kotlin.test.assertNull

class JvmEnvironmentTest {
    @Test
    fun `libanki tests and fixtures do not require Android runtime classes`() {
        val loader = javaClass.classLoader
        for (resource in listOf(
            "android/app/Application.class",
            "android/database/Cursor.class",
            "androidx/sqlite/db/SupportSQLiteDatabase.class",
            "org/robolectric/Robolectric.class",
            "timber/log/Timber.class",
        )) {
            assertNull(loader.getResource(resource), "Unexpected platform dependency: $resource")
        }
    }
}
