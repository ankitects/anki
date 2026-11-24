// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import com.ichi2.utils.Permissions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject

/** a 'full' build: [MANAGE_EXTERNAL_STORAGE] is declared in the manifest */
fun withManageExternalStorageInManifest(block: () -> Unit) {
    mockkObject(Permissions)
    every { Permissions.canManageExternalStorage(any()) } returns true
    try {
        block()
    } finally {
        unmockkObject(Permissions)
    }
}

/**
 * Whether [REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] is declared in the manifest.
 *
 * It is removed by the 'play' flavor.
 */
fun withRequestIgnoreBatteryOptimizationsInManifest(
    inManifest: Boolean,
    block: () -> Unit,
) {
    mockkObject(Permissions)
    every { Permissions.canRequestIgnoreBatteryOptimizations(any()) } returns inManifest
    try {
        block()
    } finally {
        unmockkObject(Permissions)
    }
}
