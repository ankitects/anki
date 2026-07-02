// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;
use std::mem;

use super::Context;
use super::TemplateMap;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::SchedulerVersion;
use crate::prelude::*;
use crate::revlog::RevlogEntry;

type CardAsNidAndOrd = (NoteId, u16);

struct CardContext<'a> {
    target_col: &'a mut Collection,
    usn: Usn,

    imported_notes: &'a HashMap<NoteId, NoteId>,
    notetype_map: &'a HashMap<NoteId, NotetypeId>,
    remapped_templates: &'a HashMap<NotetypeId, TemplateMap>,
    remapped_decks: &'a HashMap<DeckId, DeckId>,

    /// The number of days the source collection is ahead of the target
    /// collection
    collection_delta: i32,
    scheduler_version: SchedulerVersion,
    existing_cards: HashSet<CardAsNidAndOrd>,
    existing_card_ids: HashSet<CardId>,

    imported_cards: HashMap<CardId, CardId>,
}

impl<'c> CardContext<'c> {
    fn new<'a: 'c>(
        usn: Usn,
        days_elapsed: u32,
        target_col: &'a mut Collection,
        imported_notes: &'a HashMap<NoteId, NoteId>,
        notetype_map: &'a HashMap<NoteId, NotetypeId>,
        remapped_templates: &'a HashMap<NotetypeId, TemplateMap>,
        imported_decks: &'a HashMap<DeckId, DeckId>,
    ) -> Result<Self> {
        let existing_cards = target_col.storage.all_cards_as_nid_and_ord()?;
        let collection_delta = target_col.collection_delta(days_elapsed)?;
        let scheduler_version = target_col.scheduler_info()?.version;
        let existing_card_ids = target_col.storage.get_all_card_ids()?;
        Ok(Self {
            target_col,
            usn,
            imported_notes,
            notetype_map,
            remapped_templates,
            remapped_decks: imported_decks,
            existing_cards,
            collection_delta,
            scheduler_version,
            existing_card_ids,
            imported_cards: HashMap::new(),
        })
    }
}

impl Collection {
    /// How much `days_elapsed` is ahead of this collection.
    fn collection_delta(&mut self, days_elapsed: u32) -> Result<i32> {
        Ok(days_elapsed as i32 - self.timing_today()?.days_elapsed as i32)
    }
}

impl Context<'_> {
    pub(super) fn import_cards_and_revlog(
        &mut self,
        imported_notes: &HashMap<NoteId, NoteId>,
        notetype_map: &HashMap<NoteId, NotetypeId>,
        remapped_templates: &HashMap<NotetypeId, TemplateMap>,
        imported_decks: &HashMap<DeckId, DeckId>,
    ) -> Result<()> {
        let mut ctx = CardContext::new(
            self.usn,
            self.data.days_elapsed,
            self.target_col,
            imported_notes,
            notetype_map,
            remapped_templates,
            imported_decks,
        )?;
        if ctx.scheduler_version == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }
        ctx.import_cards(mem::take(&mut self.data.cards))?;
        ctx.import_revlog(mem::take(&mut self.data.revlog))
    }
}

impl CardContext<'_> {
    fn import_cards(&mut self, mut cards: Vec<Card>) -> Result<()> {
        for card in &mut cards {
            if self.map_to_imported_note(card) && !self.card_ordinal_already_exists(card) {
                self.add_card(card)?;
            }
            // TODO: could update existing card
        }
        Ok(())
    }

    fn import_revlog(&mut self, revlog: Vec<RevlogEntry>) -> Result<()> {
        for mut entry in revlog {
            if let Some(cid) = self.imported_cards.get(&entry.cid) {
                entry.cid = *cid;
                entry.usn = self.usn;
                self.target_col.add_revlog_entry_if_unique_undoable(entry)?;
            }
        }
        Ok(())
    }

    fn map_to_imported_note(&self, card: &mut Card) -> bool {
        if let Some(nid) = self.imported_notes.get(&card.note_id) {
            card.note_id = *nid;
            true
        } else {
            false
        }
    }

    fn card_ordinal_already_exists(&self, card: &Card) -> bool {
        self.existing_cards
            .contains(&(card.note_id, card.template_idx))
    }

    fn add_card(&mut self, card: &mut Card) -> Result<()> {
        card.usn = self.usn;
        self.remap_deck_ids(card);
        self.remap_template_index(card);
        card.shift_collection_relative_dates(self.collection_delta);
        let old_id = self.uniquify_card_id(card);

        self.target_col.add_card_if_unique_undoable(card)?;
        self.existing_card_ids.insert(card.id);
        self.imported_cards.insert(old_id, card.id);

        Ok(())
    }

    fn uniquify_card_id(&mut self, card: &mut Card) -> CardId {
        let original = card.id;
        while self.existing_card_ids.contains(&card.id) {
            card.id.0 += 999;
        }
        original
    }

    fn remap_deck_ids(&self, card: &mut Card) {
        if let Some(did) = self.remapped_decks.get(&card.deck_id) {
            card.deck_id = *did;
        }
        if let Some(did) = self.remapped_decks.get(&card.original_deck_id) {
            card.original_deck_id = *did;
        }
    }

    fn remap_template_index(&self, card: &mut Card) {
        card.template_idx = self
            .notetype_map
            .get(&card.note_id)
            .and_then(|ntid| self.remapped_templates.get(ntid))
            .and_then(|map| map.get(&card.template_idx))
            .copied()
            .unwrap_or(card.template_idx);
    }
}

impl Card {
    /// `delta` is the number days the card's source collection is ahead of the
    /// target collection.
    fn shift_collection_relative_dates(&mut self, delta: i32) {
        if self.due_in_days_since_collection_creation() {
            self.due -= delta;
        }
        if self.original_due_in_days_since_collection_creation() && self.original_due != 0 {
            self.original_due -= delta;
        }
    }

    fn due_in_days_since_collection_creation(&self) -> bool {
        matches!(self.queue, CardQueue::Review | CardQueue::DayLearn)
            || self.ctype == CardType::Review
    }

    fn original_due_in_days_since_collection_creation(&self) -> bool {
        self.ctype == CardType::Review
    }
}
