// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.testutils.rules

import com.ichi2.anki.CollectionManager
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.junit.rules.ExternalResource

/**
 * Use [replaceWith] to swap in a test backend. The default backend is restored after the test.
 */
class BackendOverrideRule : ExternalResource() {
    fun replaceWith(backend: () -> Backend) {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride { backend() }
        CollectionManager.getBackend()
    }

    override fun after() {
        BackendFactory.setOverride(null)
        runBlocking { CollectionManager.discardBackend() }
    }
}
