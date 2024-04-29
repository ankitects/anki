// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

impl Collection {
    pub fn hide_deck(&mut self, did: DeckId) -> Result<OpOutput<()>> {
        self.transact(Op::HideDeck, |col| {
            let existing_deck = col.storage.get_deck(did)?.or_not_found(did)?;
            let mut deck = existing_deck.clone();
            deck.set_hidden(true);
            col.update_deck_inner(&mut deck, existing_deck, col.usn()?)
        })
    }

    pub fn unhide_deck(&mut self, did: DeckId) -> Result<OpOutput<()>> {
        self.transact(Op::UnhideDeck, |col| {
            let existing_deck = col.storage.get_deck(did)?.or_not_found(did)?;
            let mut deck = existing_deck.clone();
            deck.set_hidden(false);
            col.update_deck_inner(&mut deck, existing_deck, col.usn()?)
        })
    }
}
