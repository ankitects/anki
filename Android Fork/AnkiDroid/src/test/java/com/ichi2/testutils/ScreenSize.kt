// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.View.MeasureSpec
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.common.utils.Dp
import com.ichi2.anki.common.utils.dp
import org.robolectric.RuntimeEnvironment
import timber.log.Timber
import kotlin.math.ceil
import com.google.android.material.R as MaterialR

/** [block] runs with a runtime qualifier emulating a split-pane display */
fun withSplitPaneUi(block: () -> Unit) = withQualifier("sw700dp", block)

fun withQualifier(
    newQualifier: String,
    block: () -> Unit,
) {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    try {
        Timber.d("Adding '$newQualifier' to qualifiers $qualifiers")
        RuntimeEnvironment.setQualifiers("+$newQualifier")
        block()
    } finally {
        Timber.d("Resetting qualifiers to $qualifiers")
        RuntimeEnvironment.setQualifiers(qualifiers)
    }
}

/** [block] runs with a runtime qualifier emulating a split-pane display */
suspend fun withSplitPaneUiAsync(block: suspend () -> Unit) = withQualifierAsync("sw700dp", block)

suspend fun withQualifierAsync(
    newQualifier: String,
    block: suspend () -> Unit,
) {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    try {
        Timber.d("Adding '$newQualifier' to qualifiers $qualifiers")
        RuntimeEnvironment.setQualifiers("+$newQualifier")
        block()
    } finally {
        Timber.d("Resetting qualifiers to $qualifiers")
        RuntimeEnvironment.setQualifiers(qualifiers)
    }
}

/** Height of the screen used to measure a list */
private val MAX_LIST_HEIGHT = 10000.dp

/**
 * Launches [intent] with a full-height screen: tall enough to show all of the list returned
 * by [listView]. The device height is kept if the list already fits.
 *
 * @param setup run after each launch, before [block]
 */
fun <A : Activity> launchForFullHeightScreenshot(
    intent: Intent,
    listView: (A) -> RecyclerView,
    setup: (A) -> Unit = {},
    block: (A) -> Unit,
) {
    val deviceQualifiers = RuntimeEnvironment.getQualifiers()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceHeight = context.resources.configuration.screenHeightDp.dp
    try {
        // measure the list in a tall window, then relaunch at its height
        setScreenHeight(MAX_LIST_HEIGHT)
        var listHeight = 0.dp
        launchAndCheckListFits(intent, listView, setup) { _, list -> listHeight = list.screenHeightToFit() }
        if (listHeight > deviceHeight) {
            setScreenHeight(listHeight)
        } else {
            RuntimeEnvironment.setQualifiers(deviceQualifiers)
        }
        launchAndCheckListFits(intent, listView, setup) { activity, _ -> block(activity) }
    } finally {
        RuntimeEnvironment.setQualifiers(deviceQualifiers)
    }
}

/**
 * Lays out the whole bottom sheet at its current width, preserving orientation resources.
 *
 * Use after any device-sized screenshots. Capture the returned view directly, as it may extend
 * beyond the screen. Activity screenshots can use [launchForFullHeightScreenshot] instead.
 */
fun BottomSheetDialogFragment.layoutForFullHeightScreenshot(list: RecyclerView): View {
    val sheet = requireDialog().findViewById<View>(MaterialR.id.design_bottom_sheet)
    sheet.measure(
        MeasureSpec.makeMeasureSpec(sheet.width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    sheet.layout(0, 0, sheet.measuredWidth, sheet.measuredHeight)
    // Newly created MaterialButtons finish updating their labels and icons before drawing.
    sheet.viewTreeObserver.dispatchOnPreDraw()
    list.checkFitsFullHeightScreenshot()
    return sheet
}

private fun <A : Activity> launchAndCheckListFits(
    intent: Intent,
    listView: (A) -> RecyclerView,
    setup: (A) -> Unit,
    block: (A, RecyclerView) -> Unit,
) {
    ActivityScenario.launch<A>(intent).use { scenario ->
        scenario.onActivity { activity ->
            setup(activity)
            advanceRobolectricLooper() // apply layout changes from setup
            val list = listView(activity)
            list.checkFitsFullHeightScreenshot()
            block(activity, list)
        }
    }
}

private fun RecyclerView.checkFitsFullHeightScreenshot() {
    check(childCount == requireNotNull(adapter).itemCount) { "Full-height screenshot must lay out every list item" }
    check(!canScrollVertically(-1) && !canScrollVertically(1)) { "Full-height screenshot must show the entire list" }
}

private fun setScreenHeight(height: Dp) {
    val qualifiers =
        RuntimeEnvironment.getQualifiers().split("-").mapNotNull { qualifier ->
            when {
                // `+h...dp` is avoided: Robolectric then rebuilds the display from pixels, losing
                // up to 1dp of width per call. Orientation is dropped so a tall screen is not
                // swapped to landscape.
                qualifier.matches(Regex("h\\d+dp")) -> "h${height.dp.toInt()}dp"
                qualifier == "port" || qualifier == "land" -> null
                else -> qualifier
            }
        }
    RuntimeEnvironment.setQualifiers(qualifiers.joinToString("-"))
}

/** The screen height showing every item. Every item must already be laid out */
private fun RecyclerView.screenHeightToFit(): Dp {
    val top = IntArray(2).also { getLocationOnScreen(it) }[1]
    val bottom = top + paddingTop + computeVerticalScrollRange() + paddingBottom
    return (ceil(bottom / resources.displayMetrics.density) + 1).dp // +1: rounding
}
