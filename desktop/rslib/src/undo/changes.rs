// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::undo::UndoableCardChange;
use crate::collection::undo::UndoableCollectionChange;
use crate::config::undo::UndoableConfigChange;
use crate::deckconfig::undo::UndoableDeckConfigChange;
use crate::decks::undo::UndoableDeckChange;
use crate::notes::undo::UndoableNoteChange;
use crate::notetype::undo::UndoableNotetypeChange;
use crate::prelude::*;
use crate::revlog::undo::UndoableRevlogChange;
use crate::scheduler::queue::undo::UndoableQueueChange;
use crate::tags::undo::UndoableTagChange;

#[derive(Debug)]
pub(crate) enum UndoableChange {
    Card(UndoableCardChange),
    Note(UndoableNoteChange),
    Deck(UndoableDeckChange),
    DeckConfig(UndoableDeckConfigChange),
    Tag(UndoableTagChange),
    Revlog(UndoableRevlogChange),
    Queue(UndoableQueueChange),
    Config(UndoableConfigChange),
    Collection(UndoableCollectionChange),
    Notetype(UndoableNotetypeChange),
}

impl UndoableChange {
    pub(super) fn undo(self, col: &mut Collection) -> Result<()> {
        match self {
            UndoableChange::Card(c) => col.undo_card_change(c),
            UndoableChange::Note(c) => col.undo_note_change(c),
            UndoableChange::Deck(c) => col.undo_deck_change(c),
            UndoableChange::Tag(c) => col.undo_tag_change(c),
            UndoableChange::Revlog(c) => col.undo_revlog_change(c),
            UndoableChange::Queue(c) => col.undo_queue_change(c),
            UndoableChange::Config(c) => col.undo_config_change(c),
            UndoableChange::DeckConfig(c) => col.undo_deck_config_change(c),
            UndoableChange::Collection(c) => col.undo_collection_change(c),
            UndoableChange::Notetype(c) => col.undo_notetype_change(c),
        }
    }
}

impl From<UndoableCardChange> for UndoableChange {
    fn from(c: UndoableCardChange) -> Self {
        UndoableChange::Card(c)
    }
}

impl From<UndoableNoteChange> for UndoableChange {
    fn from(c: UndoableNoteChange) -> Self {
        UndoableChange::Note(c)
    }
}

impl From<UndoableDeckChange> for UndoableChange {
    fn from(c: UndoableDeckChange) -> Self {
        UndoableChange::Deck(c)
    }
}

impl From<UndoableDeckConfigChange> for UndoableChange {
    fn from(c: UndoableDeckConfigChange) -> Self {
        UndoableChange::DeckConfig(c)
    }
}

impl From<UndoableTagChange> for UndoableChange {
    fn from(c: UndoableTagChange) -> Self {
        UndoableChange::Tag(c)
    }
}

impl From<UndoableRevlogChange> for UndoableChange {
    fn from(c: UndoableRevlogChange) -> Self {
        UndoableChange::Revlog(c)
    }
}

impl From<UndoableQueueChange> for UndoableChange {
    fn from(c: UndoableQueueChange) -> Self {
        UndoableChange::Queue(c)
    }
}

impl From<UndoableConfigChange> for UndoableChange {
    fn from(c: UndoableConfigChange) -> Self {
        UndoableChange::Config(c)
    }
}

impl From<UndoableCollectionChange> for UndoableChange {
    fn from(c: UndoableCollectionChange) -> Self {
        UndoableChange::Collection(c)
    }
}

impl From<UndoableNotetypeChange> for UndoableChange {
    fn from(c: UndoableNotetypeChange) -> Self {
        UndoableChange::Notetype(c)
    }
}
