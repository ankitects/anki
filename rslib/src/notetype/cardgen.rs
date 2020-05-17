// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NoteType;
use crate::{
    card::{Card, CardID},
    cloze::add_cloze_numbers_in_string,
    collection::Collection,
    deckconf::{DeckConf, DeckConfID},
    decks::DeckID,
    err::{AnkiError, Result},
    notes::{Note, NoteID},
    notetype::NoteTypeKind,
    template::ParsedTemplate,
    types::Usn,
};
use itertools::Itertools;
use rand::{rngs::StdRng, Rng, SeedableRng};
use std::collections::{HashMap, HashSet};

/// Info about an existing card required when generating new cards
#[derive(Debug, PartialEq)]
pub(crate) struct AlreadyGeneratedCardInfo {
    pub id: CardID,
    pub nid: NoteID,
    pub ord: u32,
    pub original_deck_id: DeckID,
    pub position_if_new: Option<u32>,
}

#[derive(Debug)]
pub(crate) struct CardToGenerate {
    pub ord: u32,
    pub did: Option<DeckID>,
    pub due: Option<u32>,
}

/// Info required to determine whether a particular card ordinal should exist,
/// and which deck it should be placed in.
pub(crate) struct SingleCardGenContext {
    template: Option<ParsedTemplate>,
    target_deck_id: Option<DeckID>,
}

/// Info required to determine which cards should be generated when note added/updated,
/// and where they should be placed.
pub(crate) struct CardGenContext<'a> {
    pub usn: Usn,
    pub notetype: &'a NoteType,
    cards: Vec<SingleCardGenContext>,
}

// store for data that needs to be looked up multiple times
#[derive(Default)]
pub(crate) struct CardGenCache {
    next_position: Option<u32>,
    deck_configs: HashMap<DeckID, DeckConf>,
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
    fn is_nonempty(&self, card_ord: usize, nonempty_fields: &HashSet<&str>) -> bool {
        let card = &self.cards[card_ord];
        let template = match card.template {
            Some(ref template) => template,
            None => {
                // template failed to parse; card can not be generated
                return false;
            }
        };

        template.renders_with_fields(&nonempty_fields)
    }

    /// Returns the cards that need to be generated for the provided note.
    pub(crate) fn new_cards_required(
        &self,
        note: &Note,
        existing: &[AlreadyGeneratedCardInfo],
        ensure_not_empty: bool,
    ) -> Vec<CardToGenerate> {
        let extracted = extract_data_from_existing_cards(existing);
        let cards = match self.notetype.config.kind() {
            NoteTypeKind::Normal => self.new_cards_required_normal(note, &extracted),
            NoteTypeKind::Cloze => self.new_cards_required_cloze(note, &extracted),
        };
        if extracted.existing_ords.is_empty() && cards.is_empty() && ensure_not_empty {
            // if there are no existing cards and no cards will be generated,
            // we add card 0 to ensure the note always has at least one card
            vec![CardToGenerate {
                ord: 0,
                did: extracted.deck_id,
                due: extracted.due,
            }]
        } else {
            cards
        }
    }

    fn new_cards_required_normal(
        &self,
        note: &Note,
        extracted: &ExtractedCardInfo,
    ) -> Vec<CardToGenerate> {
        let nonempty_fields = note.nonempty_fields(&self.notetype.fields);

        self.cards
            .iter()
            .enumerate()
            .filter_map(|(ord, card)| {
                if !extracted.existing_ords.contains(&(ord as u32))
                    && self.is_nonempty(ord, &nonempty_fields)
                {
                    Some(CardToGenerate {
                        ord: ord as u32,
                        did: card.target_deck_id.or(extracted.deck_id),
                        due: extracted.due,
                    })
                } else {
                    None
                }
            })
            .collect()
    }

    fn new_cards_required_cloze(
        &self,
        note: &Note,
        extracted: &ExtractedCardInfo,
    ) -> Vec<CardToGenerate> {
        // gather all cloze numbers
        let mut set = HashSet::with_capacity(4);
        for field in note.fields() {
            add_cloze_numbers_in_string(field, &mut set);
        }
        set.into_iter()
            .filter_map(|cloze_ord| {
                let card_ord = cloze_ord.saturating_sub(1).min(499);
                if extracted.existing_ords.contains(&(card_ord as u32)) {
                    None
                } else {
                    Some(CardToGenerate {
                        ord: card_ord as u32,
                        did: extracted.deck_id,
                        due: extracted.due,
                    })
                }
            })
            .collect()
    }
}

// this could be reworked in the future to avoid the extra vec allocation
pub(super) fn group_generated_cards_by_note(
    items: Vec<AlreadyGeneratedCardInfo>,
) -> Vec<(NoteID, Vec<AlreadyGeneratedCardInfo>)> {
    let mut out = vec![];
    for (key, group) in &items.into_iter().group_by(|c| c.nid) {
        out.push((key, group.collect()));
    }
    out
}

#[derive(Debug, PartialEq, Default)]
pub(crate) struct ExtractedCardInfo {
    // if set, the due position new cards should be given
    pub due: Option<u32>,
    // if set, the deck all current cards are in
    pub deck_id: Option<DeckID>,
    pub existing_ords: HashSet<u32>,
}

pub(crate) fn extract_data_from_existing_cards(
    cards: &[AlreadyGeneratedCardInfo],
) -> ExtractedCardInfo {
    let mut due = None;
    let mut deck_ids = HashSet::new();
    for card in cards {
        if due.is_none() && card.position_if_new.is_some() {
            due = card.position_if_new;
        }
        deck_ids.insert(card.original_deck_id);
    }
    let existing_ords: HashSet<_> = cards.iter().map(|c| c.ord).collect();
    ExtractedCardInfo {
        due,
        deck_id: if deck_ids.len() == 1 {
            deck_ids.into_iter().next()
        } else {
            None
        },
        existing_ords,
    }
}

impl Collection {
    pub(crate) fn generate_cards_for_new_note(
        &mut self,
        ctx: &CardGenContext,
        note: &Note,
        target_deck_id: DeckID,
    ) -> Result<()> {
        self.generate_cards_for_note(
            ctx,
            note,
            &[],
            Some(target_deck_id),
            &mut Default::default(),
        )
    }

    pub(crate) fn generate_cards_for_existing_note(
        &mut self,
        ctx: &CardGenContext,
        note: &Note,
    ) -> Result<()> {
        let existing = self.storage.existing_cards_for_note(note.id)?;
        self.generate_cards_for_note(
            ctx,
            note,
            &existing,
            Some(ctx.notetype.target_deck_id()),
            &mut Default::default(),
        )
    }

    fn generate_cards_for_note(
        &mut self,
        ctx: &CardGenContext,
        note: &Note,
        existing: &[AlreadyGeneratedCardInfo],
        target_deck_id: Option<DeckID>,
        cache: &mut CardGenCache,
    ) -> Result<()> {
        let cards = ctx.new_cards_required(note, &existing, true);
        if cards.is_empty() {
            return Ok(());
        }
        self.add_generated_cards(note.id, &cards, target_deck_id, cache)
    }

    pub(crate) fn generate_cards_for_notetype(&mut self, ctx: &CardGenContext) -> Result<()> {
        let existing_cards = self.storage.existing_cards_for_notetype(ctx.notetype.id)?;
        let by_note = group_generated_cards_by_note(existing_cards);
        let mut cache = CardGenCache::default();
        for (nid, existing_cards) in by_note {
            if ctx.notetype.config.kind() == NoteTypeKind::Normal
                && existing_cards.len() == ctx.notetype.templates.len()
            {
                // in a normal note type, if card count matches template count, we don't need
                // to load the note contents to know if all cards have been generated
                continue;
            }
            cache.next_position = None;
            let note = self.storage.get_note(nid)?.unwrap();
            self.generate_cards_for_note(ctx, &note, &existing_cards, None, &mut cache)?;
        }

        Ok(())
    }

    pub(crate) fn add_generated_cards(
        &mut self,
        nid: NoteID,
        cards: &[CardToGenerate],
        target_deck_id: Option<DeckID>,
        cache: &mut CardGenCache,
    ) -> Result<()> {
        for c in cards {
            let (did, dcid) = self.deck_for_adding(c.did.or(target_deck_id))?;
            let due = if let Some(due) = c.due {
                // use existing due number if provided
                due
            } else {
                self.due_for_deck(did, dcid, cache)?
            };
            let mut card = Card::new(nid, c.ord as u16, did, due as i32);
            self.add_card(&mut card)?;
        }

        Ok(())
    }

    // not sure if entry() can be used due to get_deck_config() returning a result
    #[allow(clippy::map_entry)]
    fn due_for_deck(&self, did: DeckID, dcid: DeckConfID, cache: &mut CardGenCache) -> Result<u32> {
        if !cache.deck_configs.contains_key(&did) {
            let conf = self.get_deck_config(dcid, true)?.unwrap();
            cache.deck_configs.insert(did, conf);
        }
        // set if not yet set
        if cache.next_position.is_none() {
            cache.next_position = Some(self.get_and_update_next_card_position().unwrap_or(0));
        }
        let next_pos = cache.next_position.unwrap();

        match cache.deck_configs.get(&did).unwrap().inner.new_card_order() {
            crate::deckconf::NewCardOrder::Random => Ok(random_position(next_pos)),
            crate::deckconf::NewCardOrder::Due => Ok(next_pos),
        }
    }

    /// If deck ID does not exist or points to a filtered deck, fall back on default.
    fn deck_for_adding(&mut self, did: Option<DeckID>) -> Result<(DeckID, DeckConfID)> {
        if let Some(did) = did {
            if let Some(deck) = self.deck_conf_if_normal(did)? {
                return Ok(deck);
            }
        }

        self.default_deck_conf()
    }

    fn default_deck_conf(&mut self) -> Result<(DeckID, DeckConfID)> {
        // currently hard-coded to 1, we could create this as needed in the future
        Ok(self
            .deck_conf_if_normal(DeckID(1))?
            .ok_or_else(|| AnkiError::invalid_input("invalid default deck"))?)
    }

    /// If deck exists and and is a normal deck, return its ID and config
    fn deck_conf_if_normal(&mut self, did: DeckID) -> Result<Option<(DeckID, DeckConfID)>> {
        Ok(self.get_deck(did)?.and_then(|d| {
            if let Some(conf_id) = d.config_id() {
                Some((did, conf_id))
            } else {
                None
            }
        }))
    }
}

fn random_position(highest_position: u32) -> u32 {
    let mut rng = StdRng::seed_from_u64(highest_position as u64);
    rng.gen_range(0, highest_position.max(1000))
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn random() {
        // predictable output and a minimum range of 1000
        assert_eq!(random_position(5), 626);
        assert_eq!(random_position(500), 898);
        assert_eq!(random_position(5001), 2282);
    }
}
