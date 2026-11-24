// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.compat.setTooltipTextCompat
import com.ichi2.ui.RtlCompliantActionProvider.Companion.unwrapContext

class SyncActionProvider(
    context: Context,
) : ActionProviderCompat(context) {
    val activity: Activity = unwrapContext(context)

    private var progressIndicator: LinearProgressIndicator? = null
    private var syncButton: AppCompatImageButton? = null

    val isProgressShown: Boolean
        get() = progressIndicator?.isVisible == true

    var icon: Drawable?
        get() = syncButton?.drawable
        set(value) {
            syncButton?.setImageDrawable(value)
        }

    override fun onCreateActionView(forItem: MenuItem): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.view_sync_progress_layout, null)

        progressIndicator = view.findViewById(R.id.progress_indicator)
        syncButton =
            view.findViewById<AppCompatImageButton>(R.id.button).apply {
                setOnClickListener {
                    if (!forItem.isEnabled) {
                        return@setOnClickListener
                    }
                    activity.onOptionsItemSelected(forItem)
                }
            }

        return view
    }

    fun setTooltipText(value: CharSequence) {
        syncButton?.setTooltipTextCompat(value)
    }
}
