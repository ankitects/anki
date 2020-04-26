// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{collection::Collection, define_newtype, err::Result};

mod schema11;
pub use schema11::Deck;
use std::sync::Arc;

define_newtype!(DeckID, i64);

pub(crate) fn child_ids<'a>(decks: &'a [Deck], name: &str) -> impl Iterator<Item = DeckID> + 'a {
    let prefix = format!("{}::", name.to_ascii_lowercase());
    decks
        .iter()
        .filter(move |d| d.name().to_ascii_lowercase().starts_with(&prefix))
        .map(|d| d.id())
}

pub(crate) fn get_deck(decks: &[Deck], id: DeckID) -> Option<&Deck> {
    for d in decks {
        if d.id() == id {
            return Some(d);
        }
    }

    None
}

impl Deck {
    pub(crate) fn is_filtered(&self) -> bool {
        matches!(self, Deck::Filtered(_))
    }
}

impl Collection {
    pub(crate) fn get_deck(&mut self, did: DeckID) -> Result<Option<Arc<Deck>>> {
        if let Some(deck) = self.state.deck_cache.get(&did) {
            return Ok(Some(deck.clone()));
        }
        if let Some(deck) = self.storage.get_deck(did)? {
            let deck = Arc::new(deck);
            self.state.deck_cache.insert(did, deck.clone());
            Ok(Some(deck))
        } else {
            Ok(None)
        }
    }
}
