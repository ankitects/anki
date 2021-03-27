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
    pub fn describe(self, tr: &I18n) -> String {
        match self {
            Op::AddDeck => tr.undo_add_deck(),
            Op::AddNote => tr.undo_add_note(),
            Op::AnswerCard => tr.undo_answer_card(),
            Op::Bury => tr.studying_bury(),
            Op::RemoveDeck => tr.decks_delete_deck(),
            Op::RemoveNote => tr.studying_delete_note(),
            Op::RenameDeck => tr.actions_rename_deck(),
            Op::ScheduleAsNew => tr.undo_forget_card(),
            Op::SetDueDate => tr.actions_set_due_date(),
            Op::Suspend => tr.studying_suspend(),
            Op::UnburyUnsuspend => tr.undo_unbury_unsuspend(),
            Op::UpdateCard => tr.undo_update_card(),
            Op::UpdateDeck => tr.undo_update_deck(),
            Op::UpdateNote => tr.undo_update_note(),
            Op::UpdatePreferences => tr.preferences_preferences(),
            Op::UpdateTag => tr.undo_update_tag(),
            Op::SetDeck => tr.browsing_change_deck(),
            Op::SetFlag => tr.undo_set_flag(),
            Op::FindAndReplace => tr.browsing_find_and_replace(),
            Op::ClearUnusedTags => tr.browsing_clear_unused_tags(),
            Op::SortCards => tr.browsing_reschedule(),
            Op::RenameTag => tr.actions_rename_tag(),
            Op::RemoveTag => tr.actions_remove_tag(),
            Op::ReparentTag => tr.actions_rename_tag(),
            Op::ReparentDeck => tr.actions_rename_deck(),
            Op::BuildFilteredDeck => tr.undo_build_filtered_deck(),
            Op::RebuildFilteredDeck => tr.undo_build_filtered_deck(),
            Op::EmptyFilteredDeck => tr.studying_empty(),
        }
        .into()
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
