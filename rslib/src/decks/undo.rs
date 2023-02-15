// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug)]

pub(crate) enum UndoableDeckChange {
    Added(Box<Deck>),
    Updated(Box<Deck>),
    Removed(Box<Deck>),
    GraveAdded(Box<(DeckId, Usn)>),
    GraveRemoved(Box<(DeckId, Usn)>),
}

impl Collection {
    pub(crate) fn undo_deck_change(&mut self, change: UndoableDeckChange) -> Result<()> {
        match change {
            UndoableDeckChange::Added(deck) => self.remove_deck_undoable(*deck),
            UndoableDeckChange::Updated(mut deck) => {
                let current = self
                    .storage
                    .get_deck(deck.id)?
                    .or_invalid("deck disappeared")?;
                self.update_single_deck_undoable(&mut deck, current)
            }
            UndoableDeckChange::Removed(deck) => self.restore_deleted_deck(*deck),
            UndoableDeckChange::GraveAdded(e) => self.remove_deck_grave(e.0, e.1),
            UndoableDeckChange::GraveRemoved(e) => self.add_deck_grave_undoable(e.0, e.1),
        }
    }

    pub(crate) fn remove_deck_and_add_grave_undoable(
        &mut self,
        deck: Deck,
        usn: Usn,
    ) -> Result<()> {
        self.state.deck_cache.clear();
        self.add_deck_grave_undoable(deck.id, usn)?;
        self.storage.remove_deck(deck.id)?;
        self.save_undo(UndoableDeckChange::Removed(Box::new(deck)));
        Ok(())
    }
}

impl Collection {
    pub(super) fn add_deck_undoable(&mut self, deck: &mut Deck) -> Result<(), AnkiError> {
        self.storage.add_deck(deck)?;
        self.save_undo(UndoableDeckChange::Added(Box::new(deck.clone())));
        Ok(())
    }

    pub(super) fn add_or_update_deck_with_existing_id_undoable(
        &mut self,
        deck: &mut Deck,
    ) -> Result<(), AnkiError> {
        self.state.deck_cache.clear();
        self.storage.add_or_update_deck_with_existing_id(deck)?;
        self.save_undo(UndoableDeckChange::Added(Box::new(deck.clone())));
        Ok(())
    }

    /// Update an individual, existing deck. Caller is responsible for ensuring
    /// deck is normalized, matches parents, is not a duplicate name, and
    /// bumping mtime. Clears deck cache.
    pub(super) fn update_single_deck_undoable(
        &mut self,
        deck: &mut Deck,
        original: Deck,
    ) -> Result<()> {
        self.state.deck_cache.clear();
        self.save_undo(UndoableDeckChange::Updated(Box::new(original)));
        self.storage.update_deck(deck)
    }

    fn restore_deleted_deck(&mut self, deck: Deck) -> Result<()> {
        self.storage.add_or_update_deck_with_existing_id(&deck)?;
        self.save_undo(UndoableDeckChange::Added(Box::new(deck)));
        Ok(())
    }

    fn remove_deck_undoable(&mut self, deck: Deck) -> Result<()> {
        self.state.deck_cache.clear();
        self.storage.remove_deck(deck.id)?;
        self.save_undo(UndoableDeckChange::Removed(Box::new(deck)));
        Ok(())
    }

    fn add_deck_grave_undoable(&mut self, did: DeckId, usn: Usn) -> Result<()> {
        self.save_undo(UndoableDeckChange::GraveAdded(Box::new((did, usn))));
        self.storage.add_deck_grave(did, usn)
    }

    fn remove_deck_grave(&mut self, did: DeckId, usn: Usn) -> Result<()> {
        self.save_undo(UndoableDeckChange::GraveRemoved(Box::new((did, usn))));
        self.storage.remove_deck_grave(did)
    }
}
