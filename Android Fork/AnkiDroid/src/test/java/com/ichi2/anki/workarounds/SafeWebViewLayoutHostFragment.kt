// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/** Minimal host for [SafeWebViewLayout] recovery tests. */
class SafeWebViewLayoutHostFragment :
    Fragment(),
    OnWebViewRecreatedListener {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())

    override fun onWebViewRecreated() {}
}
