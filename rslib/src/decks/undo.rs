// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;
use crate::undo::Undo;

#[derive(Debug)]
pub(crate) struct DeckUpdated(Deck);

impl Undo for DeckUpdated {
    fn undo(mut self: Box<Self>, col: &mut crate::collection::Collection) -> Result<()> {
        let current = col
            .storage
            .get_deck(self.0.id)?
            .ok_or_else(|| AnkiError::invalid_input("deck disappeared"))?;
        col.update_single_deck_undoable(&mut self.0, &current)
    }
}

impl Collection {
    /// Update an individual, existing deck. Caller is responsible for ensuring deck
    /// is normalized, matches parents, is not a duplicate name, and bumping mtime.
    /// Clears deck cache.
    pub(super) fn update_single_deck_undoable(
        &mut self,
        deck: &mut Deck,
        original: &Deck,
    ) -> Result<()> {
        self.state.deck_cache.clear();
        self.save_undo(Box::new(DeckUpdated(original.clone())));
        self.storage.update_deck(deck)
    }
}
