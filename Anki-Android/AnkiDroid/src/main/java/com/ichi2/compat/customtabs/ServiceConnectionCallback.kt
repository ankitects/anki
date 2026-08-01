// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import androidx.browser.customtabs.CustomTabsClient

/**
 * Callback for events when connecting and disconnecting from Custom Tabs Service.
 */
interface ServiceConnectionCallback {
    /**
     * Called when the service is connected.
     * @param client a CustomTabsClient
     */
    fun onServiceConnected(client: CustomTabsClient)

    /**
     * Called when the service is disconnected.
     */
    fun onServiceDisconnected()
}
