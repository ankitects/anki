// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Activity
import android.content.res.Resources
import android.view.View
import androidx.core.view.OnReceiveContentListener
import androidx.draganddrop.DropHelper

/** @see View.performClick */
fun View.performClickIfEnabled() {
    if (isEnabled) performClick()
}

/**
 * If possible, configures a [View] for drag and drop operations, including highlighting that
 * indicates the view is a drop target. Sets a listener that enables the view to handle dropped data.
 *
 * @see DropHelper.configureView
 */
fun View.configureView(
    activity: Activity,
    mimeTypes: Array<String>,
    options: DropHelper.Options,
    onReceiveContentListener: OnReceiveContentListener,
) {
    DropHelper.configureView(
        activity,
        this,
        mimeTypes,
        options,
        onReceiveContentListener,
    )
}

fun View.isRtl() = isRtl(resources)

fun isRtl(res: Resources): Boolean = res.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
