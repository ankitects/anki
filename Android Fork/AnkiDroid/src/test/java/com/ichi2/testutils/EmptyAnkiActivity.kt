// SPDX-License-Identifier: GPL-3.0-or-later

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
