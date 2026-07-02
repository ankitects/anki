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
