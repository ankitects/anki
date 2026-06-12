// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Empty service used by the custom tab to bind to, raising the application's importance.
 */
class KeepAliveService : Service() {
    @Suppress("RedundantNullableReturnType")
    // follows the super method which marks its return as nullable
    override fun onBind(intent: Intent): IBinder? = sBinder

    companion object {
        private val sBinder = Binder()
    }
}
