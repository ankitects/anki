// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::error::FilteredDeckError;
use crate::prelude::*;

impl Collection {
    pub fn reparent_decks(
        &mut self,
        deck_ids: &[DeckId],
        new_parent: Option<DeckId>,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::ReparentDeck, |col| {
            col.reparent_decks_inner(deck_ids, new_parent)
        })
    }

    pub fn reparent_decks_inner(
        &mut self,
        deck_ids: &[DeckId],
        new_parent: Option<DeckId>,
    ) -> Result<usize> {
        let usn = self.usn()?;
        let mut target_deck = None;
        let mut target_name = None;
        if let Some(target) = new_parent {
            if let Some(target) = self.storage.get_deck(target)? {
                if target.is_filtered() {
                    return Err(FilteredDeckError::MustBeLeafNode.into());
                }
                target_deck = Some(target);
                target_name = Some(target_deck.as_ref().unwrap().name.clone());
            }
        }

        let mut count = 0;
        for deck in deck_ids {
            if let Some(mut deck) = self.storage.get_deck(*deck)? {
                if let Some(new_name) = deck.name.reparented_name(target_name.as_ref()) {

                    let parent_decks = self.storage.parent_decks(&deck).unwrap();
                    match target_deck {
                        Some(ref target) => if parent_decks.contains(&target) {
                            continue;
                        }
                        None => if parent_decks.is_empty() {
                            continue;
                        }
                    }

                    count += 1;
                    let orig = deck.clone();

                    // this is basically update_deck_inner(), except:
                    // - we skip the normalization in prepare_for_update()
                    // - we skip the match_or_create_parents() step
                    // - we skip the final create_missing_parents(), as we don't allow parent->child
                    //   renames

                    deck.set_modified(usn);
                    deck.name = new_name;
                    self.ensure_deck_name_unique(&mut deck, usn)?;
                    self.rename_child_decks(&orig, &deck.name, usn)?;
                    self.update_single_deck_undoable(&mut deck, orig)?;
                }
            }
        }

        Ok(count)
    }
}
