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
    /// Used internally to decide whether the study queues need to be invalidated.
    pub(crate) fn needs_study_queue_reset(self) -> bool {
        let changes = self.state_changes();
        self != Op::AnswerCard
            && (changes.card_added
                || changes.card_modified
                || changes.deck_modified
                || changes.preference_modified)
    }

    pub fn state_changes(self) -> StateChanges {
        let default = Default::default;
        match self {
            Op::ScheduleAsNew
            | Op::SetDueDate
            | Op::Suspend
            | Op::UnburyUnsuspend
            | Op::UpdateCard
            | Op::SetDeck
            | Op::Bury => StateChanges {
                card_modified: true,
                ..default()
            },
            Op::AnswerCard => StateChanges {
                card_modified: true,
                // this also modifies the daily counts stored in the
                // deck, but the UI does not care about that
                ..default()
            },
            Op::AddDeck => StateChanges {
                deck_added: true,
                ..default()
            },
            Op::AddNote => StateChanges {
                card_added: true,
                note_added: true,
                tag_modified: true,
                ..default()
            },
            Op::RemoveDeck => StateChanges {
                card_modified: true,
                note_modified: true,
                deck_modified: true,
                ..default()
            },
            Op::RemoveNote => StateChanges {
                card_modified: true,
                note_modified: true,
                ..default()
            },
            Op::RenameDeck => StateChanges {
                deck_modified: true,
                ..default()
            },
            Op::UpdateDeck => StateChanges {
                deck_modified: true,
                ..default()
            },
            Op::UpdateNote => StateChanges {
                note_modified: true,
                // edits may result in new cards being generated
                card_added: true,
                // and may result in new tags being added
                tag_modified: true,
                ..default()
            },
            Op::UpdatePreferences => StateChanges {
                preference_modified: true,
                ..default()
            },
            Op::UpdateTag => StateChanges {
                tag_modified: true,
                ..default()
            },
        }
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

#[derive(Debug, Default, Clone, Copy)]
pub struct StateChanges {
    pub card_added: bool,
    pub card_modified: bool,
    pub note_added: bool,
    pub note_modified: bool,
    pub deck_added: bool,
    pub deck_modified: bool,
    pub tag_modified: bool,
    pub notetype_modified: bool,
    pub preference_modified: bool,
}
