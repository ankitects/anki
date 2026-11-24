// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.utils

import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager

/**
 * A factory that enable extending another [FragmentFactory].
 *
 * This should be useful if you want to add extra instantiations without overriding the instantiations in an old factory
 */
abstract class ExtendedFragmentFactory : FragmentFactory {
    private var baseFactory: FragmentFactory? = null

    /**
     * Create an extended factory from a base factory
     */
    constructor(baseFactory: FragmentFactory) {
        this.baseFactory = baseFactory
    }

    /**
     * Create a factory with no base, you can assign a base factory later using [.setBaseFactory]
     */
    constructor()

    /**
     * Typically you want to return the result of a super call as the last result, so if the passed class couldn't be
     * instantiated by the extending factory, the base factory should instantiate it.
     */
    @CallSuper
    override fun instantiate(
        classLoader: ClassLoader,
        className: String,
    ): Fragment =
        baseFactory?.instantiate(classLoader, className)
            ?: super.instantiate(classLoader, className)

    /**
     * Attaches the factory to an activity by setting the current activity fragment factory as the base factory
     * and updating the activity with the extended factory
     */
    inline fun <reified F : ExtendedFragmentFactory?> attachToActivity(activity: FragmentActivity): F =
        attachToFragmentManager<ExtendedFragmentFactory>(activity.supportFragmentManager) as F

    /**
     * Attaches the factory to a fragment manager by setting the current fragment factory as the base factory
     * and updating the fragment manager with the extended factory
     */
    fun <F : ExtendedFragmentFactory?> attachToFragmentManager(fragmentManager: FragmentManager): F {
        baseFactory = fragmentManager.fragmentFactory
        fragmentManager.fragmentFactory = this
        @Suppress("UNCHECKED_CAST")
        return this as F
    }
}
