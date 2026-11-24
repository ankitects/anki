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
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.showException
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.flow.receiveAsFlow

class AnswerButtonsSettingsFragment : Fragment() {
    private val viewModel: AnswerButtonsSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val showNextTimesTitle = TR.preferencesShowNextReviewTimeAboveAnswer()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    AnswerButtonsSettingsScreen(
                        state = state,
                        showNextTimesTitle = showNextTimesTitle,
                        onShowAnswerButtonsChange = viewModel::setShowAnswerButtons,
                        onShowNextTimesChange = viewModel::setShowNextTimes,
                        onHideHardAndEasyButtonsChange = viewModel::setHideHardAndEasyButtons,
                        onUseThemeColorsChange = viewModel::setUseThemeColors,
                        onButtonSizeChange = viewModel::setButtonSize,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.onError.receiveAsFlow().collectInViewLifecycle { requireActivity().showException(it) }
    }
}
