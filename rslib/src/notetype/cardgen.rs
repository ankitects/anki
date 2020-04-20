// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NoteType;
use crate::{
    card::{Card, CardID},
    collection::Collection,
    decks::DeckID,
    err::Result,
    notes::{Note, NoteID},
    template::ParsedTemplate,
    types::Usn,
};
use itertools::Itertools;
use std::collections::HashSet;

/// Info about an existing card required when generating new cards
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
    ) -> Vec<CardToGenerate> {
        let nonempty_fields = note.nonempty_fields(&self.notetype.fields);
        let extracted = extract_data_from_existing_cards(existing);
        self.new_cards_required_inner(&nonempty_fields, &extracted)
    }

    fn new_cards_required_inner(
        &self,
        nonempty_fields: &HashSet<&str>,
        extracted: &ExtractedCardInfo,
    ) -> Vec<CardToGenerate> {
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
}

// this could be reworked in the future to avoid the extra vec allocation
fn group_generated_cards_by_note(
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
    pub(crate) fn generate_cards_for_existing_note(
        &mut self,
        ctx: &CardGenContext,
        note: &Note,
    ) -> Result<()> {
        let existing = self.storage.existing_cards_for_note(note.id)?;
        let cards = ctx.new_cards_required(note, &existing);
        if cards.is_empty() {
            return Ok(());
        }
        self.add_generated_cards(ctx, note.id, &cards)
    }

    pub(crate) fn generate_cards_for_notetype(&mut self, ctx: &CardGenContext) -> Result<()> {
        let existing_cards = self.storage.existing_cards_for_notetype(ctx.notetype.id)?;
        let by_note = group_generated_cards_by_note(existing_cards);
        for (nid, existing_cards) in by_note {
            if existing_cards.len() == ctx.notetype.templates.len() {
                // nothing to do
                continue;
            }
            let note = self.storage.get_note(nid)?.unwrap();
            self.generate_cards_for_existing_note(ctx, &note)?;
        }

        Ok(())
    }

    pub(crate) fn add_generated_cards(
        &mut self,
        ctx: &CardGenContext,
        nid: NoteID,
        cards: &[CardToGenerate],
    ) -> Result<()> {
        let mut next_pos = None;
        for c in cards {
            let did = c.did.unwrap_or_else(|| ctx.notetype.target_deck_id());
            let due = c.due.unwrap_or_else(|| {
                if next_pos.is_none() {
                    next_pos = Some(self.get_and_update_next_card_position().unwrap_or(0));
                }
                next_pos.unwrap()
            });
            let mut card = Card::new(nid, c.ord as u16, did, due as i32);
            self.add_card(&mut card)?;
        }

        Ok(())
    }
}

// fixme: deal with case where invalid deck pointed to
// fixme: cloze cards, & avoid template count comparison for cloze
