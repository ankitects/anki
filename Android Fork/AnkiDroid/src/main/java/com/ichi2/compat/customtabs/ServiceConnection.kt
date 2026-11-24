// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import android.content.ComponentName
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import java.lang.ref.WeakReference

/**
 * Implementation for the CustomTabsServiceConnection that avoids leaking the
 * ServiceConnectionCallback
 */
class ServiceConnection(
    connectionCallback: ServiceConnectionCallback,
) : CustomTabsServiceConnection() {
    // A weak reference to the ServiceConnectionCallback to avoid leaking it.
    private val connectionCallback: WeakReference<ServiceConnectionCallback> = WeakReference(connectionCallback)

    override fun onCustomTabsServiceConnected(
        name: ComponentName,
        client: CustomTabsClient,
    ) {
        val connectionCallback = connectionCallback.get()
        connectionCallback?.onServiceConnected(client)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        val connectionCallback = connectionCallback.get()
        connectionCallback?.onServiceDisconnected()
    }
}
