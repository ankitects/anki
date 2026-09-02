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
            // Remap the template index using the card's *source* note id, before
            // map_to_imported_note rewrites note_id to the target's. notetype_map
            // and remapped_templates are keyed by source ids, so doing this
            // afterwards would miss the lookup and leave the card pointing at the
            // wrong template whenever a note's id changes on import. Doing it here
            // also means card_ordinal_already_exists dedupes on the final ordinal.
            // The common import has no remapped templates, so skip the per-card
            // lookup entirely in that case.
            if !self.remapped_templates.is_empty() {
                card.template_idx = remapped_template_index(
                    self.notetype_map,
                    self.remapped_templates,
                    card.note_id,
                    card.template_idx,
                );
            }
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
}

/// Map a card's `template_idx` through `remapped_templates`, looked up by the
/// card's *source* note id (the key space of `notetype_map`). Returns the
/// original index when the note's notetype wasn't remapped or the ordinal isn't
/// in the map.
fn remapped_template_index(
    notetype_map: &HashMap<NoteId, NotetypeId>,
    remapped_templates: &HashMap<NotetypeId, TemplateMap>,
    source_note_id: NoteId,
    template_idx: u16,
) -> u16 {
    notetype_map
        .get(&source_note_id)
        .and_then(|ntid| remapped_templates.get(ntid))
        .and_then(|map| map.get(&template_idx))
        .copied()
        .unwrap_or(template_idx)
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

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn template_index_is_remapped_via_source_notetype() {
        let notetype_map = HashMap::from([(NoteId(10), NotetypeId(100))]);
        let remapped_templates = HashMap::from([(NotetypeId(100), TemplateMap::from([(0, 1)]))]);
        // source note 10 -> notetype 100 -> template ordinal 0 remaps to 1
        assert_eq!(
            remapped_template_index(&notetype_map, &remapped_templates, NoteId(10), 0),
            1
        );
        // an ordinal that isn't in the map is left unchanged
        assert_eq!(
            remapped_template_index(&notetype_map, &remapped_templates, NoteId(10), 5),
            5
        );
        // a note whose notetype wasn't remapped is left unchanged
        assert_eq!(
            remapped_template_index(&notetype_map, &remapped_templates, NoteId(999), 0),
            0
        );
    }
}
