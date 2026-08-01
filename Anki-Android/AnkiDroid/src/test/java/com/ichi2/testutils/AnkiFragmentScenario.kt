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
 *
 *  This file incorporates code under the following license:
 *
 *  https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:fragment/fragment-testing/src/main/java/androidx/fragment/app/testing/FragmentScenario.kt;bpv=1;bpt=0;drc=3762a3b1977b7a12eedefd4e7954e7f3a67ae1f7
 *
 *      Copyright 2018 The Android Open Source Project
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *  Changes:
 *  * Renamed to AnkiFragmentScenario & changed Package name
 *  * Updated to use EmptyAnkiActivity + Robolectric hack to register activity
 *  * Remove redundant public modifier
 *  * Remove deprecated methods
 *  * suppress unused methods
 *  * use `Closeable by activityScenario`
 *  * Don't redefine `FragmentAction`
 *  * remove `themeResId` and `containerResId`
 */

@file:Suppress("unused")

package com.ichi2.testutils

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.fragment.app.testing.FragmentFactoryHolderViewModel
import androidx.fragment.app.testing.FragmentScenario.FragmentAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ichi2.testutils.AnkiFragmentScenario.Companion.launch
import java.io.Closeable

/**
 * Launches a Fragment with given arguments hosted by an empty [FragmentActivity] using
 * given [FragmentFactory] and waits for it to reach [initialState].
 *
 * This method cannot be called from the main thread.
 *
 * @param fragmentArgs a bundle to passed into fragment
 * @param initialState the initial [Lifecycle.State]. Passing in
 * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
 * @param factory a fragment factory to use or null to use default factory
 */
inline fun <reified F : Fragment> launchFragment(
    fragmentArgs: Bundle? = null,
    initialState: Lifecycle.State = Lifecycle.State.RESUMED,
    factory: FragmentFactory? = null,
): AnkiFragmentScenario<F> =
    launch(
        F::class.java,
        fragmentArgs,
        initialState,
        factory,
    )

/**
 * Launches a Fragment with given arguments hosted by an empty [FragmentActivity] using
 * [instantiate] to create the Fragment and waits for it to reach [initialState].
 *
 * This method cannot be called from the main thread.
 *
 * @param fragmentArgs a bundle to passed into fragment
 * @param initialState the initial [Lifecycle.State]. Passing in
 * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
 * @param instantiate method which will be used to instantiate the Fragment.
 */
inline fun <reified F : Fragment> launchFragment(
    fragmentArgs: Bundle? = null,
    initialState: Lifecycle.State = Lifecycle.State.RESUMED,
    crossinline instantiate: () -> F,
): AnkiFragmentScenario<F> =
    launch(
        F::class.java,
        fragmentArgs,
        initialState,
        object : FragmentFactory() {
            override fun instantiate(
                classLoader: ClassLoader,
                className: String,
            ) = when (className) {
                F::class.java.name -> instantiate()
                else -> super.instantiate(classLoader, className)
            }
        },
    )

/**
 * Launches a Fragment in the Activity's root view container `android.R.id.content`, with
 * given arguments hosted by an empty [FragmentActivity] and waits for it to reach [initialState].
 *
 * This method cannot be called from the main thread.
 *
 * @param fragmentArgs a bundle to passed into fragment
 * @param initialState the initial [Lifecycle.State]. Passing in
 * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
 * @param factory a fragment factory to use or null to use default factory
 */
inline fun <reified F : Fragment> launchFragmentInContainer(
    fragmentArgs: Bundle? = null,
    initialState: Lifecycle.State = Lifecycle.State.RESUMED,
    factory: FragmentFactory? = null,
): AnkiFragmentScenario<F> =
    AnkiFragmentScenario.launchInContainer(
        F::class.java,
        fragmentArgs,
        initialState,
        factory,
    )

/**
 * Launches a Fragment in the Activity's root view container `android.R.id.content`, with
 * given arguments hosted by an empty [FragmentActivity] using
 * [instantiate] to create the Fragment and waits for it to reach [initialState].
 *
 * This method cannot be called from the main thread.
 *
 * @param fragmentArgs a bundle to passed into fragment
 * @param initialState the initial [Lifecycle.State]. Passing in
 * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
 * @param instantiate method which will be used to instantiate the Fragment. This is a
 * simplification of the [FragmentFactory] interface for cases where only a single class
 * needs a custom constructor called.
 */
inline fun <reified F : Fragment> launchFragmentInContainer(
    fragmentArgs: Bundle? = null,
    initialState: Lifecycle.State = Lifecycle.State.RESUMED,
    crossinline instantiate: () -> F,
): AnkiFragmentScenario<F> =
    AnkiFragmentScenario.launchInContainer(
        F::class.java,
        fragmentArgs,
        initialState,
        object : FragmentFactory() {
            override fun instantiate(
                classLoader: ClassLoader,
                className: String,
            ) = when (className) {
                F::class.java.name -> instantiate()
                else -> super.instantiate(classLoader, className)
            }
        },
    )

/**
 * Run [block] using [AnkiFragmentScenario.onFragment], returning the result of the [block].
 *
 * If any exceptions are raised while running [block], they are rethrown.
 */
@SuppressWarnings("DocumentExceptions")
inline fun <reified F : Fragment, T : Any> AnkiFragmentScenario<F>.withFragment(crossinline block: F.() -> T): T {
    lateinit var value: T
    var err: Throwable? = null
    onFragment { fragment ->
        try {
            value = block(fragment)
        } catch (t: Throwable) {
            err = t
        }
    }
    err?.let { throw it }
    return value
}

/**
 * FragmentScenario provides API to start and drive a Fragment's lifecycle state for testing. It
 * works with arbitrary fragments and works consistently across different versions of the Android
 * framework.
 *
 * FragmentScenario only supports [androidx.fragment.app.Fragment][Fragment]. If you are using
 * a deprecated fragment class such as `android.support.v4.app.Fragment` or
 * [android.app.Fragment], please update your code to
 * [androidx.fragment.app.Fragment][Fragment].
 *
 *
 * @param F The Fragment class being tested
 *
 * @see ActivityScenario a scenario API for Activity
 */
class AnkiFragmentScenario<F : Fragment> private constructor(
    // MemberVisibilityCanBePrivate: synthetic access
    @Suppress("MemberVisibilityCanBePrivate")
    val fragmentClass: Class<F>,
    private val activityScenario: ActivityScenario<EmptyAnkiActivity>,
) : Closeable by activityScenario {
    /**
     * Moves Fragment state to a new state.
     *
     *  If a new state and current state are the same, this method does nothing. It accepts
     * all [Lifecycle.State]s. [DESTROYED][Lifecycle.State.DESTROYED] is a terminal state.
     * You cannot move to any other state after the Fragment reaches that state.
     *
     * This method cannot be called from the main thread.
     */
    fun moveToState(newState: Lifecycle.State): AnkiFragmentScenario<F> {
        if (newState == Lifecycle.State.DESTROYED) {
            activityScenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager
                        .findFragmentByTag(FRAGMENT_TAG)
                // Null means the fragment has been destroyed already.
                if (fragment != null) {
                    activity.supportFragmentManager.commitNow {
                        remove(fragment)
                    }
                }
            }
        } else {
            activityScenario.onActivity { activity ->
                val fragment =
                    requireNotNull(
                        activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG),
                    ) {
                        "The fragment has been removed from the FragmentManager already."
                    }
                activity.supportFragmentManager.commitNow {
                    setMaxLifecycle(fragment, newState)
                }
            }
        }
        return this
    }

    /**
     * Recreates the host Activity.
     *
     * After this method call, it is ensured that the Fragment state goes back to the same state
     * as its previous state.
     *
     * This method cannot be called from the main thread.
     */
    fun recreate(): AnkiFragmentScenario<F> {
        activityScenario.recreate()
        return this
    }

    /**
     * Runs a given [action] on the current Activity's main thread.
     *
     * Note that you should never keep Fragment reference passed into your [action]
     * because it can be recreated at anytime during state transitions.
     *
     * Throwing an exception from [action] makes the host Activity crash. You can
     * inspect the exception in logcat outputs.
     *
     * This method cannot be called from the main thread.
     */
    fun onFragment(action: FragmentAction<F>): AnkiFragmentScenario<F> {
        activityScenario.onActivity { activity ->
            val fragment =
                requireNotNull(
                    activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG),
                ) {
                    "The fragment has been removed from the FragmentManager already."
                }
            check(fragmentClass.isInstance(fragment))
            action.perform(requireNotNull(fragmentClass.cast(fragment)))
        }
        return this
    }

    companion object {
        private const val FRAGMENT_TAG = "AnkiFragmentScenario_Fragment_Tag"

        /**
         * Launches a Fragment with given arguments hosted by an empty [FragmentActivity] using
         * the given [FragmentFactory] and waits for it to reach the resumed state.
         *
         *
         * This method cannot be called from the main thread.
         *
         * @param fragmentClass a fragment class to instantiate
         * @param fragmentArgs a bundle to passed into fragment
         * @param factory a fragment factory to use or null to use default factory
         */
        @JvmStatic
        fun <F : Fragment> launch(
            fragmentClass: Class<F>,
            fragmentArgs: Bundle?,
            factory: FragmentFactory?,
        ): AnkiFragmentScenario<F> =
            launch(
                fragmentClass,
                fragmentArgs,
                Lifecycle.State.RESUMED,
                factory,
            )

        /**
         * Launches a Fragment with given arguments hosted by an empty [FragmentActivity],
         * using the given [FragmentFactory] and waits for it to reach [initialState].
         *
         * This method cannot be called from the main thread.
         *
         * @param fragmentClass a fragment class to instantiate
         * @param fragmentArgs a bundle to passed into fragment
         * @param initialState The initial [Lifecycle.State]. Passing in
         * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
         * @param factory a fragment factory to use or null to use default factory
         */
        @JvmOverloads
        @JvmStatic
        fun <F : Fragment> launch(
            fragmentClass: Class<F>,
            fragmentArgs: Bundle? = null,
            initialState: Lifecycle.State = Lifecycle.State.RESUMED,
            factory: FragmentFactory? = null,
        ): AnkiFragmentScenario<F> =
            internalLaunch(
                fragmentClass,
                fragmentArgs,
                initialState,
                factory,
                0,
            )

        /**
         * Launches a Fragment in the Activity's root view container `android.R.id.content`, with
         * given arguments hosted by an empty [FragmentActivity] using the given
         * [FragmentFactory] and waits for it to reach the resumed state.
         *
         * This method cannot be called from the main thread.
         *
         * @param fragmentClass a fragment class to instantiate
         * @param fragmentArgs a bundle to passed into fragment
         * @param factory a fragment factory to use or null to use default factory
         */
        @JvmStatic
        fun <F : Fragment> launchInContainer(
            fragmentClass: Class<F>,
            fragmentArgs: Bundle?,
            factory: FragmentFactory?,
        ): AnkiFragmentScenario<F> =
            launchInContainer(
                fragmentClass,
                fragmentArgs,
                Lifecycle.State.RESUMED,
                factory,
            )

        /**
         * Launches a Fragment in the Activity's root view container `android.R.id.content`, with
         * given arguments hosted by an empty [FragmentActivity],
         * using the given [FragmentFactory] and waits for it to reach [initialState].
         *
         * This method cannot be called from the main thread.
         *
         * @param fragmentClass a fragment class to instantiate
         * @param fragmentArgs a bundle to passed into fragment
         * @param initialState The initial [Lifecycle.State]. Passing in
         * [DESTROYED][Lifecycle.State.DESTROYED] will result in an [IllegalArgumentException].
         * @param factory a fragment factory to use or null to use default factory
         */
        @JvmOverloads
        @JvmStatic
        fun <F : Fragment> launchInContainer(
            fragmentClass: Class<F>,
            fragmentArgs: Bundle? = null,
            initialState: Lifecycle.State = Lifecycle.State.RESUMED,
            factory: FragmentFactory? = null,
        ): AnkiFragmentScenario<F> =
            internalLaunch(
                fragmentClass,
                fragmentArgs,
                initialState,
                factory,
                android.R.id.content,
            )

        internal fun <F : Fragment> internalLaunch(
            fragmentClass: Class<F>,
            fragmentArgs: Bundle?,
            initialState: Lifecycle.State,
            factory: FragmentFactory?,
            @IdRes containerViewId: Int,
        ): AnkiFragmentScenario<F> {
            require(initialState != Lifecycle.State.DESTROYED) {
                "Cannot set initial Lifecycle state to $initialState for FragmentScenario"
            }
            val componentName =
                ComponentName(
                    ApplicationProvider.getApplicationContext(),
                    EmptyAnkiActivity::class.java,
                )

            Robolectric.registerTestActivity<EmptyAnkiActivity>()

            val startActivityIntent = Intent.makeMainActivity(componentName)

            val scenario =
                AnkiFragmentScenario(
                    fragmentClass,
                    ActivityScenario.launch(
                        startActivityIntent,
                    ),
                )
            scenario.activityScenario.onActivity { activity ->
                if (factory != null) {
                    FragmentFactoryHolderViewModel.getInstance(activity).fragmentFactory = factory
                    activity.supportFragmentManager.fragmentFactory = factory
                }
                val fragment =
                    activity.supportFragmentManager.fragmentFactory
                        .instantiate(requireNotNull(fragmentClass.classLoader), fragmentClass.name)
                fragment.arguments = fragmentArgs
                activity.supportFragmentManager.commitNow {
                    add(containerViewId, fragment, FRAGMENT_TAG)
                    setMaxLifecycle(fragment, initialState)
                }
            }
            return scenario
        }
    }
}
