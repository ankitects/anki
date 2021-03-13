// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Op {
    AddDeck,
    AddNote,
    AnswerCard,
    Bury,
    RemoveDeck,
    RemoveNote,
    RenameDeck,
    ScheduleAsNew,
    SetDueDate,
    Suspend,
    UnburyUnsuspend,
    UpdateCard,
    UpdateDeck,
    UpdateNote,
    UpdatePreferences,
    UpdateTag,
    SetDeck,
}

impl Op {
    pub(crate) fn needs_study_queue_reset(self) -> bool {
        self != Op::AnswerCard
    }
}

impl Collection {
    pub fn describe_op_kind(&self, op: Op) -> String {
        let key = match op {
            Op::AddDeck => TR::UndoAddDeck,
            Op::AddNote => TR::UndoAddNote,
            Op::AnswerCard => TR::UndoAnswerCard,
            Op::Bury => TR::StudyingBury,
            Op::RemoveDeck => TR::DecksDeleteDeck,
            Op::RemoveNote => TR::StudyingDeleteNote,
            Op::RenameDeck => TR::ActionsRenameDeck,
            Op::ScheduleAsNew => TR::UndoForgetCard,
            Op::SetDueDate => TR::ActionsSetDueDate,
            Op::Suspend => TR::StudyingSuspend,
            Op::UnburyUnsuspend => TR::UndoUnburyUnsuspend,
            Op::UpdateCard => TR::UndoUpdateCard,
            Op::UpdateDeck => TR::UndoUpdateDeck,
            Op::UpdateNote => TR::UndoUpdateNote,
            Op::UpdatePreferences => TR::PreferencesPreferences,
            Op::UpdateTag => TR::UndoUpdateTag,
            Op::SetDeck => TR::BrowsingChangeDeck,
        };

        self.i18n.tr(key).to_string()
    }
}
