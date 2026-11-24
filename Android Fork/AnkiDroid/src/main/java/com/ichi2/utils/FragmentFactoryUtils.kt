// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory

object FragmentFactoryUtils {
    /**
     * A convenience util method that instantiate a fragment using the passed activity [FragmentFactory]
     */
    inline fun <reified F : Fragment> instantiate(
        activity: FragmentActivity,
        className: String,
    ): F {
        val factory = activity.supportFragmentManager.fragmentFactory
        return factory.instantiate(activity.classLoader, className) as F
    }

    /**
     * A convenience util method that instantiate a fragment using the passed activity [FragmentFactory]
     */
    inline fun <reified F : Fragment> instantiate(
        activity: FragmentActivity,
        cls: Class<F>,
    ): F = instantiate(activity, cls.name)
}
