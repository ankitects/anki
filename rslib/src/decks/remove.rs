// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::prelude::*;

impl Collection {
    pub fn remove_decks_and_child_decks(&mut self, dids: &[DeckId]) -> Result<OpOutput<usize>> {
        self.transact(Op::RemoveDeck, |col| {
            let mut card_count = 0;
            let usn = col.usn()?;
            for did in dids {
                if let Some(deck) = col.storage.get_deck(*did)? {
                    let child_decks = col.storage.child_decks(&deck)?;

                    // top level
                    card_count += col.remove_single_deck(&deck, usn)?;

                    // remove children
                    for deck in child_decks {
                        card_count += col.remove_single_deck(&deck, usn)?;
                    }
                }
            }
            Ok(card_count)
        })
    }

    pub(crate) fn remove_single_deck(&mut self, deck: &Deck, usn: Usn) -> Result<usize> {
        let card_count = match deck.kind {
            DeckKind::Normal(_) => self.delete_all_cards_in_normal_deck(deck.id)?,
            DeckKind::Filtered(_) => {
                self.return_all_cards_in_filtered_deck(deck.id)?;
                0
            }
        };
        self.clear_aux_config_for_deck(deck.id)?;
        if deck.id.0 == 1 {
            // if the default deck is included, just ensure it's reset to the default
            // name, as we've already removed its cards
            let mut modified_default = deck.clone();
            modified_default.name =
                NativeDeckName::from_native_str(self.tr.deck_config_default_name());
            self.prepare_deck_for_update(&mut modified_default, usn)?;
            modified_default.set_modified(usn);
            self.update_single_deck_undoable(&mut modified_default, deck.clone())?;
        } else {
            self.remove_deck_and_add_grave_undoable(deck.clone(), usn)?;
        }
        Ok(card_count)
    }
}

impl Collection {
    fn delete_all_cards_in_normal_deck(&mut self, did: DeckId) -> Result<usize> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.remove_cards_and_orphaned_notes(&cids)?;
        Ok(cids.len())
    }
}
