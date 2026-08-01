// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.StatisticsDestination

/** Builds the [Intent] that opens the statistics screen. */
fun StatisticsDestination.toIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, fragmentClass = Statistics::class)
