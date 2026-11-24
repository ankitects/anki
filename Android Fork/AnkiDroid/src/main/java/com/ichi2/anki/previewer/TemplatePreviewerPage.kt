// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTemplatePreviewerContainerBinding
import com.ichi2.anki.previewer.TemplatePreviewerFragment.Companion.ARG_KEY
import com.ichi2.anki.utils.ext.doOnTabSelected
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Container for [TemplatePreviewerFragment] that works as a standalone page
 * by including a toolbar and a TabLayout for changing the current template.
 */
class TemplatePreviewerPage : Fragment(R.layout.fragment_template_previewer_container) {
    private val binding by viewBinding(FragmentTemplatePreviewerContainerBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val fragment: TemplatePreviewerFragment
        if (savedInstanceState == null) {
            val arguments = BundleCompat.getParcelable(requireArguments(), ARG_KEY, TemplatePreviewerArguments::class.java)!!
            fragment = TemplatePreviewerFragment.newInstance(arguments)
            childFragmentManager.commitNow {
                replace(R.id.fragment_container, fragment)
            }
        } else {
            fragment = childFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment
        }

        val viewModel = fragment.viewModel

        lifecycleScope.launch {
            val cardsWithEmptyFronts = viewModel.cardsWithEmptyFronts?.await()
            for ((index, templateName) in viewModel.getTemplateNames().withIndex()) {
                val tabTitle =
                    if (cardsWithEmptyFronts?.get(index) == true) {
                        getString(CommonString.card_previewer_empty_front_indicator, templateName)
                    } else {
                        templateName
                    }
                val newTab = binding.tabLayout.newTab().setText(tabTitle)
                binding.tabLayout.addTab(newTab)
            }
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(viewModel.getCurrentTabIndex()))
            binding.tabLayout.doOnTabSelected { tab ->
                Timber.v("Selected tab %d", tab.position)
                viewModel.onTabSelected(tab.position)
            }
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            arguments: TemplatePreviewerArguments,
        ): Intent =
            CardViewerActivity.getIntent(
                context,
                TemplatePreviewerPage::class,
                Bundle().apply { putParcelable(ARG_KEY, arguments) },
            )
    }
}
