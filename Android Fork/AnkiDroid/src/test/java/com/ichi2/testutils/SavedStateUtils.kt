// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.robolectric.android.controller.ActivityController

/** Stops the activity and captures its saved instance state. */
fun ActivityController<*>.saveState(): Bundle = Bundle().also { pause().stop().saveInstanceState(it) }

fun Bundle.parcelSize(): Int = withParcel { it.dataSize() }

fun Bundle.parcelledCopy(classLoader: ClassLoader?): Bundle =
    withParcel {
        it.setDataPosition(0)
        requireNotNull(it.readBundle(classLoader))
    }

/** Exercises the activity's default factory even when the screen uses custom ViewModel factories. */
fun ComponentActivity.createSavedStateHandleWithDefaultFactory(): SavedStateHandle =
    ViewModelProvider(this)[DefaultSavedStateViewModel::class.java].state

internal class DefaultSavedStateViewModel(
    val state: SavedStateHandle,
) : ViewModel()

/** Finds serialized ViewModel state providers, including those owned by hosted fragments. */
fun Bundle.savedStateHandlePaths(): List<String> = pathsToKey(SAVED_STATE_HANDLES)

/** Ignores copies in FragmentManager's arguments and reports only copies in ViewModel state. */
fun Bundle.savedStateHandlePathsToKey(key: String): List<String> = pathsToKey(key).filter { SAVED_STATE_HANDLES in it }

private const val SAVED_STATE_HANDLES = "androidx.lifecycle.internal.SavedStateHandlesProvider"

@Suppress("DEPRECATION") // Bundle.get is needed to inspect nested values of different types.
private fun Bundle.pathsToKey(
    target: String,
    path: String = "savedState",
): List<String> =
    keySet().flatMap { key ->
        val childPath = "$path/$key"
        val matches = if (key == target) listOf(childPath) else emptyList()
        matches + ((get(key) as? Bundle)?.pathsToKey(target, childPath) ?: emptyList())
    }

private fun <T> Bundle.withParcel(action: (Parcel) -> T): T {
    val parcel = Parcel.obtain()
    try {
        parcel.writeBundle(this)
        return action(parcel)
    } finally {
        parcel.recycle()
    }
}
