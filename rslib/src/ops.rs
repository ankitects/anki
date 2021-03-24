// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Op {
    AddDeck,
    AddNote,
    AnswerCard,
    BuildFilteredDeck,
    Bury,
    ClearUnusedTags,
    EmptyFilteredDeck,
    FindAndReplace,
    RebuildFilteredDeck,
    RemoveDeck,
    RemoveNote,
    RemoveTag,
    RenameDeck,
    ReparentDeck,
    RenameTag,
    ReparentTag,
    ScheduleAsNew,
    SetDeck,
    SetDueDate,
    SetFlag,
    SortCards,
    Suspend,
    UnburyUnsuspend,
    UpdateCard,
    UpdateDeck,
    UpdateNote,
    UpdatePreferences,
    UpdateTag,
}

impl Op {
    pub fn describe(self, i18n: &I18n) -> String {
        let key = match self {
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
            Op::SetFlag => TR::UndoSetFlag,
            Op::FindAndReplace => TR::BrowsingFindAndReplace,
            Op::ClearUnusedTags => TR::BrowsingClearUnusedTags,
            Op::SortCards => TR::BrowsingReschedule,
            Op::RenameTag => TR::ActionsRenameTag,
            Op::RemoveTag => TR::ActionsRemoveTag,
            Op::ReparentTag => TR::ActionsRenameTag,
            Op::ReparentDeck => TR::ActionsRenameDeck,
            Op::BuildFilteredDeck => TR::UndoBuildFilteredDeck,
            Op::RebuildFilteredDeck => TR::UndoBuildFilteredDeck,
            Op::EmptyFilteredDeck => TR::StudyingEmpty,
        };

        i18n.tr(key).to_string()
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct StateChanges {
    pub card: bool,
    pub note: bool,
    pub deck: bool,
    pub tag: bool,
    pub notetype: bool,
    pub preference: bool,
}

#[derive(Debug, Clone, Copy)]
pub struct OpChanges {
    pub op: Op,
    pub changes: StateChanges,
}

pub struct OpOutput<T> {
    pub output: T,
    pub changes: OpChanges,
}

impl<T> OpOutput<T> {
    pub(crate) fn map<F, N>(self, func: F) -> OpOutput<N>
    where
        F: FnOnce(T) -> N,
    {
        OpOutput {
            output: func(self.output),
            changes: self.changes,
        }
    }
}
