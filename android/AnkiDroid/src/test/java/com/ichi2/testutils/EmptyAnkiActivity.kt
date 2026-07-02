/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils

import android.os.Bundle
import androidx.fragment.app.testing.FragmentFactoryHolderViewModel
import com.ichi2.anki.AnkiActivity

/**
 * An empty activity inheriting FragmentActivity. This Activity is used to host Fragment in
 * FragmentScenario.
 */
class EmptyAnkiActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        // Checks if we have a custom FragmentFactory and set it.
        val factory = FragmentFactoryHolderViewModel.getInstance(this).fragmentFactory
        if (factory != null) {
            supportFragmentManager.fragmentFactory = factory
        }

        // FragmentFactory needs to be set before calling the super.onCreate, otherwise the
        // Activity crashes when it is recreating and there is a fragment which has no
        // default constructor.
        super.onCreate(savedInstanceState)
    }
}
