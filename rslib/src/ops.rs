// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Op {
    Custom(String),
    AddDeck,
    AddNote,
    AddNotetype,
    AnswerCard,
    BuildFilteredDeck,
    Bury,
    ChangeNotetype,
    ClearUnusedTags,
    CreateCustomStudy,
    EmptyCards,
    EmptyFilteredDeck,
    FindAndReplace,
    ImageOcclusion,
    Import,
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
    GradeNow,
    SetFlag,
    SortCards,
    Suspend,
    UnburyUnsuspend,
    UpdateCard,
    UpdateConfig,
    UpdateDeck,
    UpdateDeckConfig,
    UpdateNote,
    UpdatePreferences,
    UpdateTag,
    UpdateNotetype,
    SetCurrentDeck,
    /// Does not register changes in undo queue, but does not clear the current
    /// queue either.
    SkipUndo,
}

impl Op {
    pub fn describe(&self, tr: &I18n) -> String {
        match self {
            Op::AddDeck => tr.actions_add_deck(),
            Op::AddNote => tr.actions_add_note(),
            Op::AnswerCard => tr.actions_answer_card(),
            Op::Bury => tr.studying_bury(),
            Op::CreateCustomStudy => tr.actions_custom_study(),
            Op::EmptyCards => tr.actions_empty_cards(),
            Op::Import => tr.actions_import(),
            Op::RemoveDeck => tr.decks_delete_deck(),
            Op::RemoveNote => tr.studying_delete_note(),
            Op::RenameDeck => tr.actions_rename_deck(),
            Op::ScheduleAsNew => tr.actions_forget_card(),
            Op::SetDueDate => tr.actions_set_due_date(),
            Op::GradeNow => tr.actions_grade_now(),
            Op::Suspend => tr.studying_suspend(),
            Op::UnburyUnsuspend => tr.actions_unbury_unsuspend(),
            Op::UpdateCard => tr.actions_update_card(),
            Op::UpdateDeck => tr.actions_update_deck(),
            Op::UpdateNote => tr.actions_update_note(),
            Op::UpdatePreferences => tr.preferences_preferences(),
            Op::UpdateTag => tr.actions_update_tag(),
            Op::SetCardDeck => tr.browsing_change_deck(),
            Op::SetFlag => tr.actions_set_flag(),
            Op::FindAndReplace => tr.browsing_find_and_replace(),
            Op::ClearUnusedTags => tr.browsing_clear_unused_tags(),
            Op::SortCards => tr.actions_reposition(),
            Op::RenameTag => tr.actions_rename_tag(),
            Op::RemoveTag => tr.actions_remove_tag(),
            Op::ReparentTag => tr.actions_rename_tag(),
            Op::ReparentDeck => tr.actions_rename_deck(),
            Op::BuildFilteredDeck => tr.actions_build_filtered_deck(),
            Op::RebuildFilteredDeck => tr.actions_build_filtered_deck(),
            Op::EmptyFilteredDeck => tr.studying_empty(),
            Op::SetCurrentDeck => tr.browsing_select_deck(),
            Op::UpdateDeckConfig => tr.deck_config_title(),
            Op::AddNotetype => tr.actions_add_notetype(),
            Op::RemoveNotetype => tr.actions_remove_notetype(),
            Op::UpdateNotetype => tr.actions_update_notetype(),
            Op::UpdateConfig => tr.actions_update_config(),
            Op::Custom(name) => name.into(),
            Op::ChangeNotetype => tr.browsing_change_notetype(),
            Op::SkipUndo => return "".to_string(),
            Op::ImageOcclusion => tr.notetypes_image_occlusion_name(),
        }
        .into()
    }
}

#[derive(Debug, PartialEq, Eq, Default, Clone, Copy)]
pub struct StateChanges {
    pub card: bool,
    pub note: bool,
    pub deck: bool,
    pub tag: bool,
    pub notetype: bool,
    pub config: bool,
    pub deck_config: bool,
    pub mtime: bool,
}

#[derive(Debug, PartialEq, Eq, Clone)]
pub struct OpChanges {
    pub op: Op,
    pub changes: StateChanges,
}

#[derive(Debug, PartialEq, Eq)]
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
        c.card || c.config || c.deck || c.deck_config || c.note || c.notetype || c.tag || c.mtime
    }
    // These routines should return true even if the GUI may have
    // special handling for an action, since we need to do the right
    // thing when undoing, and if multiple windows of the same type are
    // open.

    pub fn requires_browser_table_redraw(&self) -> bool {
        let c = &self.changes;
        c.card || c.notetype || c.config || (c.note && self.op != Op::AddNote) || c.deck
    }

    pub fn requires_browser_sidebar_redraw(&self) -> bool {
        let c = &self.changes;
        c.tag || c.deck || c.notetype || c.config
    }

    pub fn requires_note_text_redraw(&self) -> bool {
        let c = &self.changes;
        c.note || c.notetype
    }

    pub fn requires_study_queue_rebuild(&self) -> bool {
        let c = &self.changes;
        (c.card && self.op != Op::SetFlag)
            || c.deck
            || (c.config
                && matches!(
                    self.op,
                    Op::SetCurrentDeck | Op::UpdatePreferences | Op::UpdateDeckConfig
                ))
            || c.deck_config
    }
}
