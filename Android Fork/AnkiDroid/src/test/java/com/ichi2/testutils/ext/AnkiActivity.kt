// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import android.view.Menu
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import kotlin.test.assertNotNull

fun AnkiActivity.menu(): Menu = assertNotNull(findViewById<Toolbar>(R.id.toolbar)?.menu)
