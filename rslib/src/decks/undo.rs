// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug)]

pub(crate) enum UndoableDeckChange {
    Updated(Box<Deck>),
}

impl Collection {
    pub(crate) fn undo_deck_change(&mut self, change: UndoableDeckChange) -> Result<()> {
        match change {
            UndoableDeckChange::Updated(mut deck) => {
                let current = self
                    .storage
                    .get_deck(deck.id)?
                    .ok_or_else(|| AnkiError::invalid_input("deck disappeared"))?;
                self.update_single_deck_undoable(&mut *deck, &current)
            }
        }
    }

    /// Update an individual, existing deck. Caller is responsible for ensuring deck
    /// is normalized, matches parents, is not a duplicate name, and bumping mtime.
    /// Clears deck cache.
    pub(super) fn update_single_deck_undoable(
        &mut self,
        deck: &mut Deck,
        original: &Deck,
    ) -> Result<()> {
        self.state.deck_cache.clear();
        self.save_undo(UndoableDeckChange::Updated(Box::new(original.clone())));
        self.storage.update_deck(deck)
    }
}
