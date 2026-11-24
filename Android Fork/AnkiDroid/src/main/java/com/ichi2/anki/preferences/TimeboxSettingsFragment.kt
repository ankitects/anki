// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.compose.theme.AnkiDroidTheme

/**
 * Screen allowing users to enable/disable and configure the review timebox time limit.
 *
 * Hosts the Compose [TimeboxSettingsScreen].
 */
class TimeboxSettingsFragment : Fragment() {
    private val viewModel: TimeboxSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    TimeboxSettingsScreen(
                        isLoading = state.isLoading,
                        isEnabled = state.isEnabled,
                        minutes = state.limit.minutes,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onIncrement = viewModel::increment,
                        onDecrement = viewModel::decrement,
                        onSetMinutes = viewModel::setMinutes,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
}
