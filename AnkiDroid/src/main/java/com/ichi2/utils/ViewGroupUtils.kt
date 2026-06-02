/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.ichi2.anki.common.preferences.sharedPrefs
import timber.log.Timber
import java.util.ArrayList

object ViewGroupUtils {
    fun getAllChildren(viewGroup: ViewGroup): List<View> {
        val childrenCount = viewGroup.childCount
        val views: MutableList<View> = ArrayList(childrenCount)
        for (i in 0 until childrenCount) {
            views.add(viewGroup.getChildAt(i))
        }
        return views
    }

    fun getAllChildrenRecursive(viewGroup: ViewGroup): MutableList<View> {
        val views: MutableList<View> = ArrayList()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            views.add(child)
            if (child is ViewGroup) {
                views.addAll(getAllChildrenRecursive(child))
            }
        }
        return views
    }

    fun setRenderWorkaround(activity: Activity) {
        if (activity.sharedPrefs().getBoolean("softwareRender", false)) {
            Timber.i("ViewGroupUtils::setRenderWorkaround - software render requested, altering Views...")
            setContentViewLayerTypeSoftware(activity)
        } else {
            Timber.i("ViewGroupUtils::setRenderWorkaround - using default / hardware rendering")
        }
    }

    /**
     * Gets all the Views for the given Activity's ContentView, and sets their layerType
     * to the given layerType
     *
     * @param activity Activity containing the View hierarchy to alter
     */
    private fun setContentViewLayerTypeSoftware(activity: Activity) {
        val rootViewGroup =
            (activity.findViewById<View>(android.R.id.content) as ViewGroup)
                .getChildAt(0) as ViewGroup
        val allViews = getAllChildrenRecursive(rootViewGroup)
        allViews.add(rootViewGroup)
        for (v in allViews) {
            Timber.d(
                "ViewGroupUtils::setContentViewLayerTypeSoftware for view %s",
                v.id,
            )
            v.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }
}

/**
 * Removes all children which satisfy [predicate].
 * Errors or runtime exceptions thrown during iteration or by [predicate] are relayed to the caller.

 * @param predicate a predicate which returns `true` for children to be removed.
 */
fun ViewGroup.removeChildren(predicate: (View) -> Boolean) {
    children.filter(predicate).toList().forEach {
        removeView(it)
    }
}
