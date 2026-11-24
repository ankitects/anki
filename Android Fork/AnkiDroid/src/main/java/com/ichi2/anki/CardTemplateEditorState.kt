// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 Snowiee <xenonnn4w@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.exception.ReportableException
import com.ichi2.anki.libanki.CardOrdinal

/**
 * Which template editor view is currently displayed.
 * The View layer handles mapping to actual View IDs.
 */
enum class EditorViewType {
    FRONT,
    BACK,
    STYLING,
}

/** Encapsulates the entire state for [CardTemplateEditor] */
sealed class CardTemplateEditorState {
    /**
     * Loading the initial state, no data available yet.
     * [exception] is null while loading and non-null if initialization failed.
     */
    data class Initializing(
        val exception: ReportableException? = null,
    ) : CardTemplateEditorState()

    /**
     * Successfully loaded, all data is valid.
     * This is the main working state where the user is editing templates.
     */
    data class Loaded(
        /** The currently selected template ordinal (0-based index) */
        val currentTemplateOrd: CardOrdinal = 0,
        /** The currently selected editor view (front/back/styling) */
        val currentEditorView: EditorViewType = EditorViewType.FRONT,
        /** Simple transient messages in response to user actions or null for no message */
        val message: UserMessage? = null,
        /**
         * Transient, recoverable error raised while editing (e.g. a failed save) or null for no
         * error. Unlike [Initializing.exception] this keeps the user in the editing screen with
         * their data intact; the View consumes it and calls [CardTemplateEditorViewModel.clearError].
         */
        val error: ReportableException? = null,
    ) : CardTemplateEditorState()

    /** Finished, activity should close */
    data object Finished : CardTemplateEditorState()

    /** Simple message to be shown to the user */
    enum class UserMessage {
        CantDeleteLastTemplate,
        CantAddTemplateToDynamic,
        SaveSuccess,
        DeletionWouldOrphanNote,
    }
}
