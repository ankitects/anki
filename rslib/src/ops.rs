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
        match self {
            Op::AddDeck => i18n.undo_add_deck(),
            Op::AddNote => i18n.undo_add_note(),
            Op::AnswerCard => i18n.undo_answer_card(),
            Op::Bury => i18n.studying_bury(),
            Op::RemoveDeck => i18n.decks_delete_deck(),
            Op::RemoveNote => i18n.studying_delete_note(),
            Op::RenameDeck => i18n.actions_rename_deck(),
            Op::ScheduleAsNew => i18n.undo_forget_card(),
            Op::SetDueDate => i18n.actions_set_due_date(),
            Op::Suspend => i18n.studying_suspend(),
            Op::UnburyUnsuspend => i18n.undo_unbury_unsuspend(),
            Op::UpdateCard => i18n.undo_update_card(),
            Op::UpdateDeck => i18n.undo_update_deck(),
            Op::UpdateNote => i18n.undo_update_note(),
            Op::UpdatePreferences => i18n.preferences_preferences(),
            Op::UpdateTag => i18n.undo_update_tag(),
            Op::SetDeck => i18n.browsing_change_deck(),
            Op::SetFlag => i18n.undo_set_flag(),
            Op::FindAndReplace => i18n.browsing_find_and_replace(),
            Op::ClearUnusedTags => i18n.browsing_clear_unused_tags(),
            Op::SortCards => i18n.browsing_reschedule(),
            Op::RenameTag => i18n.actions_rename_tag(),
            Op::RemoveTag => i18n.actions_remove_tag(),
            Op::ReparentTag => i18n.actions_rename_tag(),
            Op::ReparentDeck => i18n.actions_rename_deck(),
            Op::BuildFilteredDeck => i18n.undo_build_filtered_deck(),
            Op::RebuildFilteredDeck => i18n.undo_build_filtered_deck(),
            Op::EmptyFilteredDeck => i18n.studying_empty(),
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
