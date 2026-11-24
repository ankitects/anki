// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.SharedPreferences
import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.settings.PrefsRepository
import io.mockk.every
import io.mockk.mockk

/**
 * [Resources] whose `getString(id)` returns `id.toString()`.
 *
 * Lets [PrefsRepository] resolve `@StringRes` keys and enum values without Android.
 * Keys are therefore not the real ones: use [PrefsRepository.key] to address them.
 */
fun idAsStringResources(): Resources =
    mockk<Resources>().also { resources ->
        every { resources.getString(any()) } answers { firstArg<Int>().toString() }
    }

/**
 * A [PrefsRepository] backed by in-memory [SharedPreferences], for pure JVM tests
 * of classes which take a `prefs: PrefsRepository` parameter.
 *
 * To isolate the [com.ichi2.anki.settings.Prefs] singleton instead, use [JvmPreferencesRule].
 */
fun inMemoryPrefs(sharedPrefs: SharedPreferences = SPMockBuilder().createSharedPreferences()): PrefsRepository =
    PrefsRepository(sharedPrefs, idAsStringResources())
