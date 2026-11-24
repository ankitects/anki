// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.logging

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import timber.log.Timber

class FragmentLifecycleLogger(
    private val activity: Activity,
) : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentAttached(
        fm: FragmentManager,
        f: Fragment,
        context: Context,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onAttach")
    }

    override fun onFragmentCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onCreate")
    }

    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onViewCreated")
    }

    override fun onFragmentStarted(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onStart")
    }

    override fun onFragmentResumed(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onResume")
    }

    override fun onFragmentPaused(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onPause")
    }

    override fun onFragmentStopped(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onStop")
    }

    override fun onFragmentSaveInstanceState(
        fm: FragmentManager,
        f: Fragment,
        outState: Bundle,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onSaveInstanceState")
    }

    override fun onFragmentViewDestroyed(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onViewDestroyed")
    }

    override fun onFragmentDestroyed(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onDestroy")
    }

    override fun onFragmentDetached(
        fm: FragmentManager,
        f: Fragment,
    ) {
        Timber.i("${activity::class.simpleName}::${f::class.simpleName}::onDetach")
    }
}
