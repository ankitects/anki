// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum UndoableOpKind {
    AddDeck,
    AddNote,
    AnswerCard,
    Bury,
    RemoveDeck,
    RemoveNote,
    Suspend,
    UnburyUnsuspend,
    UpdateCard,
    UpdateDeck,
    UpdateNote,
    UpdatePreferences,
    UpdateTag,
}

impl UndoableOpKind {
    pub(crate) fn needs_study_queue_reset(self) -> bool {
        self != UndoableOpKind::AnswerCard
    }
}

impl Collection {
    pub fn describe_op_kind(&self, op: UndoableOpKind) -> String {
        let key = match op {
            UndoableOpKind::AddDeck => TR::UndoAddDeck,
            UndoableOpKind::AddNote => TR::UndoAddNote,
            UndoableOpKind::AnswerCard => TR::UndoAnswerCard,
            UndoableOpKind::Bury => TR::StudyingBury,
            UndoableOpKind::RemoveDeck => TR::DecksDeleteDeck,
            UndoableOpKind::RemoveNote => TR::StudyingDeleteNote,
            UndoableOpKind::Suspend => TR::StudyingSuspend,
            UndoableOpKind::UnburyUnsuspend => TR::UndoUnburyUnsuspend,
            UndoableOpKind::UpdateCard => TR::UndoUpdateCard,
            UndoableOpKind::UpdateDeck => TR::UndoUpdateDeck,
            UndoableOpKind::UpdateNote => TR::UndoUpdateNote,
            UndoableOpKind::UpdatePreferences => TR::PreferencesPreferences,
            UndoableOpKind::UpdateTag => TR::UndoUpdateTag,
        };

        self.i18n.tr(key).to_string()
    }
}
