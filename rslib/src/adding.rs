// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use crate::prelude::*;

pub struct DeckAndNotetype {
    pub deck_id: DeckId,
    pub notetype_id: NotetypeId,
}

impl Collection {
    /// An option in the preferences screen governs the behaviour here.
    ///
    /// - When 'default to the current deck' is enabled, we use the current deck
    ///   if it's normal, the provided reviewer card's deck as a fallback, and
    ///   Default as a final fallback. We then fetch the last used notetype
    ///   stored in the deck, falling back to the global notetype, or the first
    ///   available one.
    ///
    /// - Otherwise, each note type remembers the last deck cards were added to,
    ///   and we use that, defaulting to the current deck if missing, and
    ///   Default otherwise.
    pub fn defaults_for_adding(
        &mut self,
        home_deck_of_reviewer_card: DeckId,
    ) -> Result<DeckAndNotetype> {
        let deck_id;
        let notetype_id;
        if self.get_config_bool(BoolKey::AddingDefaultsToCurrentDeck) {
            deck_id = self
                .get_current_deck_for_adding(home_deck_of_reviewer_card)?
                .id;
            notetype_id = self.default_notetype_for_deck(deck_id)?.id;
        } else {
            notetype_id = self.get_current_notetype_for_adding()?.id;
            deck_id = if let Some(deck_id) = self.default_deck_for_notetype(notetype_id)? {
                deck_id
            } else {
                // default not set in notetype; fall back to current deck
                self.get_current_deck_for_adding(home_deck_of_reviewer_card)?
                    .id
            };
        }

        Ok(DeckAndNotetype {
            deck_id,
            notetype_id,
        })
    }

    /// The currently selected deck, the home deck of the provided card if
    /// current deck is filtered, or the default deck.
    fn get_current_deck_for_adding(
        &mut self,
        home_deck_of_reviewer_card: DeckId,
    ) -> Result<Arc<Deck>> {
        // current deck, if not filtered
        if let Some(current) = self.get_deck(self.get_current_deck_id())? {
            if !current.is_filtered() {
                return Ok(current);
            }
        }
        // provided reviewer card's home deck
        if let Some(home_deck) = self.get_deck(home_deck_of_reviewer_card)? {
            return Ok(home_deck);
        }
        // default deck
        self.get_deck(DeckId(1))?.or_not_found(DeckId(1))
    }

    fn get_current_notetype_for_adding(&mut self) -> Result<Arc<Notetype>> {
        // try global 'current' notetype
        if let Some(ntid) = self.get_current_notetype_id() {
            if let Some(nt) = self.get_notetype(ntid)? {
                return Ok(nt);
            }
        }
        // try first available notetype
        if let Some((ntid, _)) = self.storage.get_all_notetype_names()?.first() {
            Ok(self.get_notetype(*ntid)?.unwrap())
        } else {
            invalid_input!("collection has no notetypes");
        }
    }

    fn default_notetype_for_deck(&mut self, deck: DeckId) -> Result<Arc<Notetype>> {
        // try last notetype used by deck
        if let Some(ntid) = self.get_last_notetype_for_deck(deck) {
            if let Some(nt) = self.get_notetype(ntid)? {
                return Ok(nt);
            }
        }

        // fall back
        self.get_current_notetype_for_adding()
    }

    /// Returns the last deck added to with this notetype, provided it is valid.
    /// This is optional due to the inconsistent handling, where changes in
    /// notetype may need to update the current deck, but not vice versa. If
    /// a previous deck is not set, we want to keep the current selection,
    /// instead of resetting it.
    pub(crate) fn default_deck_for_notetype(&mut self, ntid: NotetypeId) -> Result<Option<DeckId>> {
        if let Some(last_deck_id) = self.get_last_deck_added_to_for_notetype(ntid) {
            if let Some(deck) = self.get_deck(last_deck_id)? {
                if !deck.is_filtered() {
                    return Ok(Some(deck.id));
                }
            }
        }

        Ok(None)
    }
}
