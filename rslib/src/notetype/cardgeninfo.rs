// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NoteType;
use crate::{decks::DeckID, template::ParsedTemplate, types::Usn};
use std::collections::HashSet;

/// Info required to determine whether a particular card ordinal should exist,
/// and which deck it should be placed in.
pub(crate) struct SingleCardGenContext<'a> {
    template: Option<ParsedTemplate<'a>>,
    target_deck_id: Option<DeckID>,
}

/// Info required to determine which cards should be generated when note added/updated,
/// and where they should be placed.
pub(crate) struct CardGenContext<'a> {
    pub usn: Usn,
    pub notetype: &'a NoteType,
    cards: Vec<SingleCardGenContext<'a>>,
}

impl CardGenContext<'_> {
    pub(crate) fn new(nt: &NoteType, usn: Usn) -> CardGenContext<'_> {
        CardGenContext {
            usn,
            notetype: &nt,
            cards: nt
                .templates
                .iter()
                .map(|tmpl| SingleCardGenContext {
                    template: tmpl.parsed_question(),
                    target_deck_id: tmpl.target_deck_id(),
                })
                .collect(),
        }
    }

    /// If template[ord] generates a non-empty question given nonempty_fields, return the provided
    /// deck id, or an overriden one. If question is empty, return None.
    pub fn deck_id_if_nonempty(
        &self,
        card_ord: usize,
        nonempty_fields: &HashSet<&str>,
        target_deck_id: DeckID,
    ) -> Option<DeckID> {
        let card = &self.cards[card_ord];
        let template = match card.template {
            Some(ref template) => template,
            None => {
                // template failed to parse; card can not be generated
                return None;
            }
        };

        if template.renders_with_fields(&nonempty_fields) {
            Some(card.target_deck_id.unwrap_or(target_deck_id))
        } else {
            None
        }
    }

    /// Return a list of (ordinal, deck id) for any new cards not in existing_ords
    /// that are non-empty, and thus need to be added.
    pub fn new_cards_required(
        &self,
        nonempty_fields: &HashSet<&str>,
        target_deck_id: DeckID,
        existing_ords: &HashSet<u16>,
    ) -> Vec<(usize, DeckID)> {
        self.cards
            .iter()
            .enumerate()
            .filter_map(|(ord, card)| {
                let deck_id = card.target_deck_id.unwrap_or(target_deck_id);
                if existing_ords.contains(&(ord as u16)) {
                    None
                } else {
                    self.deck_id_if_nonempty(ord, nonempty_fields, deck_id)
                        .map(|did| (ord, did))
                }
            })
            .collect()
    }
}
