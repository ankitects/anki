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
    SetDeck,
    SetDueDate,
    SetFlag,
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
        };

        i18n.tr(key).to_string()
    }

    /// Used internally to decide whether the study queues need to be invalidated.
    pub(crate) fn needs_study_queue_reset(self) -> bool {
        let changes = self.state_changes();
        self != Op::AnswerCard && (changes.card || changes.deck || changes.preference)
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
            | Op::Bury
            | Op::SetFlag => StateChanges {
                card: true,
                ..default()
            },
            Op::AnswerCard => StateChanges {
                card: true,
                // this also modifies the daily counts stored in the
                // deck, but the UI does not care about that
                ..default()
            },
            Op::AddDeck => StateChanges {
                deck: true,
                ..default()
            },
            Op::AddNote => StateChanges {
                card: true,
                note: true,
                tag: true,
                ..default()
            },
            Op::RemoveDeck => StateChanges {
                card: true,
                note: true,
                deck: true,
                ..default()
            },
            Op::RemoveNote => StateChanges {
                card: true,
                note: true,
                ..default()
            },
            Op::RenameDeck => StateChanges {
                deck: true,
                ..default()
            },
            Op::UpdateDeck => StateChanges {
                deck: true,
                ..default()
            },
            Op::UpdateNote => StateChanges {
                note: true,
                // edits may result in new cards being generated
                card: true,
                // and may result in new tags being added
                tag: true,
                ..default()
            },
            Op::UpdatePreferences => StateChanges {
                preference: true,
                ..default()
            },
            Op::UpdateTag => StateChanges {
                note: true,
                tag: true,
                ..default()
            },
        }
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
