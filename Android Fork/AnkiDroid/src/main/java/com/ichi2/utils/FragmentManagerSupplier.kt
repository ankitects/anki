// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.AnkiActivity

/**
 * A function which returns a [FragmentManager].
 * This is an adapter, accepting either be a [Fragment] or an [AnkiActivity], even though they share
 * no interfaces in common for fragment managers
 */
@FunctionalInterface
fun interface FragmentManagerSupplier {
    fun getFragmentManager(): FragmentManager
}

fun AppCompatActivity.asFragmentManagerSupplier() = FragmentManagerSupplier { this.supportFragmentManager }
