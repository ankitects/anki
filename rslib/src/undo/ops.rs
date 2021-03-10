// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum UndoableOpKind {
    UpdateCard,
    AnswerCard,
    Bury,
    Suspend,
    UnburyUnsuspend,
    AddNote,
    RemoveNote,
    UpdateTag,
    UpdateNote,
}

impl UndoableOpKind {
    pub(crate) fn needs_study_queue_reset(self) -> bool {
        self != UndoableOpKind::AnswerCard
    }
}

impl Collection {
    pub fn describe_op_kind(&self, op: UndoableOpKind) -> String {
        let key = match op {
            UndoableOpKind::UpdateCard => TR::UndoUpdateCard,
            UndoableOpKind::AnswerCard => TR::UndoAnswerCard,
            UndoableOpKind::Bury => TR::StudyingBury,
            UndoableOpKind::Suspend => TR::StudyingSuspend,
            UndoableOpKind::UnburyUnsuspend => TR::UndoUnburyUnsuspend,
            UndoableOpKind::AddNote => TR::UndoAddNote,
            UndoableOpKind::RemoveNote => TR::StudyingDeleteNote,
            UndoableOpKind::UpdateTag => TR::UndoUpdateTag,
            UndoableOpKind::UpdateNote => TR::UndoUpdateNote,
        };

        self.i18n.tr(key).to_string()
    }
}
