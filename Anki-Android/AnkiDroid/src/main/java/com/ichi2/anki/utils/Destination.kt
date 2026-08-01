// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils

import android.content.Context
import android.content.Intent

// TODO: Replace with com.ichi2.anki.common.destinations.Destination + navigate(). See #20558.
interface Destination {
    fun toIntent(context: Context): Intent
}
