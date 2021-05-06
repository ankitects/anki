// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, PartialEq)]
pub enum Op {
    Custom(String),
    AddDeck,
    AddNote,
    AddNotetype,
    AnswerCard,
    BuildFilteredDeck,
    Bury,
    ClearUnusedTags,
    EmptyFilteredDeck,
    ExpandCollapse,
    FindAndReplace,
    RebuildFilteredDeck,
    RemoveDeck,
    RemoveNote,
    RemoveNotetype,
    RemoveTag,
    RenameDeck,
    ReparentDeck,
    RenameTag,
    ReparentTag,
    ScheduleAsNew,
    SetCardDeck,
    SetDueDate,
    SetFlag,
    SortCards,
    Suspend,
    UnburyUnsuspend,
    UpdateCard,
    UpdateDeck,
    UpdateDeckConfig,
    UpdateNote,
    UpdatePreferences,
    UpdateTag,
    UpdateNotetype,
    SetCurrentDeck,
}

impl Op {
    pub fn describe(&self, tr: &I18n) -> String {
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
            Op::SetCardDeck => tr.browsing_change_deck(),
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
            Op::ExpandCollapse => tr.undo_expand_collapse(),
            Op::SetCurrentDeck => tr.browsing_change_deck(),
            Op::UpdateDeckConfig => tr.deck_config_title(),
            Op::AddNotetype => tr.undo_add_notetype(),
            Op::RemoveNotetype => tr.undo_remove_notetype(),
            Op::UpdateNotetype => tr.undo_update_notetype(),
            Op::Custom(name) => name.into(),
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
    pub config: bool,
    pub deck_config: bool,
}

#[derive(Debug, Clone)]
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

impl OpChanges {
    #[cfg(test)]
    pub fn had_change(&self) -> bool {
        let c = &self.changes;
        c.card || c.config || c.deck || c.deck_config || c.note || c.notetype || c.tag
    }
    // These routines should return true even if the GUI may have
    // special handling for an action, since we need to do the right
    // thing when undoing, and if multiple windows of the same type are
    // open. For example, while toggling the expand/collapse state
    // in the sidebar will not normally trigger a full sidebar refresh,
    // requires_browser_sidebar_redraw() should still return true.

    pub fn requires_browser_table_redraw(&self) -> bool {
        let c = &self.changes;
        c.card
            || c.notetype
            || (c.note && self.op != Op::AddNote)
            || (c.deck && self.op != Op::ExpandCollapse)
    }

    pub fn requires_browser_sidebar_redraw(&self) -> bool {
        let c = &self.changes;
        c.tag || c.deck || c.notetype
    }

    pub fn requires_editor_redraw(&self) -> bool {
        let c = &self.changes;
        c.note || c.notetype
    }

    pub fn requires_study_queue_rebuild(&self) -> bool {
        let c = &self.changes;
        if self.op == Op::AnswerCard {
            return false;
        }

        c.card
            || (c.deck && self.op != Op::ExpandCollapse)
            || (c.config && matches!(self.op, Op::SetCurrentDeck))
            || c.deck_config
    }
}
