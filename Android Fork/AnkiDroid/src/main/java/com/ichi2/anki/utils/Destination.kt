// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Context
import android.content.Intent

// TODO: Replace with com.ichi2.anki.common.destinations.Destination + navigate(). See #20558.
interface Destination {
    fun toIntent(context: Context): Intent
}
