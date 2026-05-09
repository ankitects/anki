// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;

use anki_io::filename_is_safe;
use itertools::Itertools;

use super::ExportProgress;
use crate::decks::immediate_parent_name;
use crate::decks::NormalDeck;
use crate::latex::extract_latex;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::revlog::RevlogEntry;
use crate::search::CardTableGuard;
use crate::search::NoteTableGuard;
use crate::text::extract_media_refs;

#[derive(Debug, Default)]
pub(super) struct ExchangeData {
    pub(super) decks: Vec<Deck>,
    pub(super) notes: Vec<Note>,
    pub(super) cards: Vec<Card>,
    pub(super) notetypes: Vec<Notetype>,
    pub(super) revlog: Vec<RevlogEntry>,
    pub(super) deck_configs: Vec<DeckConfig>,
    pub(super) media_filenames: HashSet<String>,
    pub(super) days_elapsed: u32,
    pub(super) creation_utc_offset: Option<i32>,
}

impl ExchangeData {
    pub(super) fn gather_data(
        &mut self,
        col: &mut Collection,
        search: impl TryIntoSearch,
        with_scheduling: bool,
        with_deck_configs: bool,
    ) -> Result<()> {
        self.days_elapsed = col.timing_today()?.days_elapsed;
        self.creation_utc_offset = col.get_creation_utc_offset();
        let (notes, guard) = col.gather_notes(search)?;
        self.notes = notes;
        let (cards, guard) = guard.col.gather_cards()?;
        self.cards = cards;
        self.decks = guard.col.gather_decks(with_scheduling, !with_scheduling)?;
        self.notetypes = guard.col.gather_notetypes()?;

        let allow_filtered = self.enables_filtered_decks();

        if with_scheduling {
            self.revlog = guard.col.gather_revlog()?;
            if !allow_filtered {
                self.restore_cards_from_filtered_decks();
            }
        } else {
            self.reset_cards_and_notes(guard.col);
        };

        if with_deck_configs {
            self.deck_configs = guard.col.gather_deck_configs(&self.decks)?;
        }
        self.reset_decks(!with_deck_configs, !with_scheduling, allow_filtered);

        self.check_ids()
    }

    pub(super) fn gather_media_names(
        &mut self,
        progress: &mut ThrottlingProgressHandler<ExportProgress>,
    ) -> Result<()> {
        let mut inserter = |name: String| {
            if filename_is_safe(&name) {
                self.media_filenames.insert(name);
            }
        };
        let mut progress = progress.incrementor(ExportProgress::Notes);
        let svg_getter = svg_getter(&self.notetypes);
        for note in self.notes.iter() {
            progress.increment()?;
            gather_media_names_from_note(note, &mut inserter, &svg_getter);
        }
        for notetype in self.notetypes.iter() {
            notetype.gather_media_names(&mut inserter);
        }
        Ok(())
    }

    fn reset_cards_and_notes(&mut self, col: &Collection) {
        self.remove_system_tags();
        self.reset_cards(col);
    }

    fn remove_system_tags(&mut self) {
        const SYSTEM_TAGS: [&str; 2] = ["marked", "leech"];
        for note in self.notes.iter_mut() {
            note.tags = std::mem::take(&mut note.tags)
                .into_iter()
                .filter(|tag| !SYSTEM_TAGS.iter().any(|s| tag.eq_ignore_ascii_case(s)))
                .collect();
        }
    }

    fn reset_decks(
        &mut self,
        reset_config_ids: bool,
        reset_study_info: bool,
        allow_filtered: bool,
    ) {
        for deck in self.decks.iter_mut() {
            if reset_study_info {
                deck.common = Default::default();
            }
            match &mut deck.kind {
                DeckKind::Normal(normal) => {
                    if reset_config_ids {
                        normal.config_id = 1;
                    }
                    if reset_study_info {
                        normal.extend_new = 0;
                        normal.extend_review = 0;
                        normal.review_limit = None;
                        normal.review_limit_today = None;
                        normal.new_limit = None;
                        normal.new_limit_today = None;
                    }
                }
                DeckKind::Filtered(_) if reset_study_info || !allow_filtered => {
                    deck.kind = DeckKind::Normal(NormalDeck {
                        config_id: 1,
                        ..Default::default()
                    })
                }
                DeckKind::Filtered(_) => (),
            }
        }
    }

    /// Because the legacy exporter relied on the importer handling filtered
    /// decks by converting them into regular ones, there are two scenarios to
    /// watch out for:
    /// 1. If exported without scheduling, cards have been reset, but their deck
    ///    ids may point to filtered decks.
    /// 2. If exported with scheduling, cards have not been reset, but their
    ///    original deck ids may point to missing decks.
    fn enables_filtered_decks(&self) -> bool {
        self.cards
            .iter()
            .all(|c| self.card_and_its_deck_are_normal(c) || self.original_deck_exists(c))
    }

    fn card_and_its_deck_are_normal(&self, card: &Card) -> bool {
        card.original_deck_id.0 == 0
            && self
                .decks
                .iter()
                .find(|d| d.id == card.deck_id)
                .map(|d| !d.is_filtered())
                .unwrap_or_default()
    }

    fn original_deck_exists(&self, card: &Card) -> bool {
        card.original_deck_id.0 == 1 || self.decks.iter().any(|d| d.id == card.original_deck_id)
    }

    fn reset_cards(&mut self, col: &Collection) {
        let mut position = col.get_next_card_position();
        for card in self.cards.iter_mut() {
            // schedule_as_new() removes cards from filtered decks, but we want to
            // leave cards in their current deck, which gets converted to a regular one
            let deck_id = card.deck_id;
            if card.schedule_as_new(position, true, true) {
                position += 1;
            }
            card.flags = 0;
            card.deck_id = deck_id;
        }
    }

    fn restore_cards_from_filtered_decks(&mut self) {
        for card in self.cards.iter_mut() {
            if card.is_filtered() {
                // instead of moving between decks, the deck is converted to a regular one
                card.original_deck_id = card.deck_id;
                card.remove_from_filtered_deck_restoring_queue();
            }
        }
    }

    fn check_ids(&self) -> Result<()> {
        let tomorrow = TimestampMillis::now().adding_secs(86_400).0;
        if self
            .cards
            .iter()
            .map(|card| card.id.0)
            .chain(self.notes.iter().map(|note| note.id.0))
            .chain(self.revlog.iter().map(|entry| entry.id.0))
            .any(|timestamp| timestamp > tomorrow)
        {
            Err(AnkiError::InvalidId)
        } else {
            Ok(())
        }
    }
}

fn gather_media_names_from_note(
    note: &Note,
    inserter: &mut impl FnMut(String),
    svg_getter: &impl Fn(NotetypeId) -> bool,
) {
    for field in note.fields() {
        for media_ref in extract_media_refs(field) {
            inserter(media_ref.fname_decoded.to_string());
        }

        for latex in extract_latex(field, svg_getter(note.notetype_id)).1 {
            inserter(latex.fname);
        }
    }
}

fn svg_getter(notetypes: &[Notetype]) -> impl Fn(NotetypeId) -> bool {
    let svg_map: HashMap<NotetypeId, bool> = notetypes
        .iter()
        .map(|nt| (nt.id, nt.config.latex_svg))
        .collect();
    move |nt_id| svg_map.get(&nt_id).copied().unwrap_or_default()
}

impl Collection {
    fn gather_notes(
        &mut self,
        search: impl TryIntoSearch,
    ) -> Result<(Vec<Note>, NoteTableGuard<'_>)> {
        let guard = self.search_notes_into_table(search)?;
        guard
            .col
            .storage
            .all_searched_notes()
            .map(|notes| (notes, guard))
    }

    fn gather_cards(&mut self) -> Result<(Vec<Card>, CardTableGuard<'_>)> {
        let guard = self.search_cards_of_notes_into_table()?;
        guard
            .col
            .storage
            .all_searched_cards()
            .map(|cards| (cards, guard))
    }

    /// If with_original, also gather all original decks of cards in filtered
    /// decks, so they don't have to be converted to regular decks on import.
    /// If skip_default, skip exporting the default deck to avoid
    /// changing the importing client's defaults.
    fn gather_decks(&mut self, with_original: bool, skip_default: bool) -> Result<Vec<Deck>> {
        let decks = if with_original {
            self.storage.get_decks_and_original_for_search_cards()
        } else {
            self.storage.get_decks_for_search_cards()
        }?;
        let parents = self.get_parent_decks(&decks)?;
        Ok(decks
            .into_iter()
            .chain(parents)
            .filter(|deck| !(skip_default && deck.id.0 == 1))
            .collect())
    }

    fn get_parent_decks(&mut self, decks: &[Deck]) -> Result<Vec<Deck>> {
        let mut parent_names: HashSet<String> = decks
            .iter()
            .map(|deck| deck.name.as_native_str().to_owned())
            .collect();
        let mut parents = Vec::new();
        for deck in decks {
            self.add_parent_decks(deck.name.as_native_str(), &mut parent_names, &mut parents)?;
        }
        Ok(parents)
    }

    fn add_parent_decks(
        &mut self,
        name: &str,
        parent_names: &mut HashSet<String>,
        parents: &mut Vec<Deck>,
    ) -> Result<()> {
        if let Some(parent_name) = immediate_parent_name(name) {
            if parent_names.insert(parent_name.to_owned()) {
                if let Some(parent) = self.storage.get_deck_by_name(parent_name)? {
                    parents.push(parent);
                    self.add_parent_decks(parent_name, parent_names, parents)?;
                }
            }
        }
        Ok(())
    }

    fn gather_notetypes(&mut self) -> Result<Vec<Notetype>> {
        self.storage.get_notetypes_for_search_notes()
    }

    fn gather_revlog(&mut self) -> Result<Vec<RevlogEntry>> {
        self.storage.get_revlog_entries_for_searched_cards()
    }

    fn gather_deck_configs(&mut self, decks: &[Deck]) -> Result<Vec<DeckConfig>> {
        decks
            .iter()
            .filter_map(|deck| deck.config_id())
            .unique()
            .map(|config_id| {
                self.storage
                    .get_deck_config(config_id)?
                    .or_not_found(config_id)
            })
            .collect()
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::search::SearchNode;

    #[test]
    fn should_gather_valid_notes() {
        let mut data = ExchangeData::default();
        let mut col = Collection::new();

        let note = NoteAdder::basic(&mut col).add(&mut col);
        data.gather_data(&mut col, SearchNode::WholeCollection, true, true)
            .unwrap();

        assert_eq!(data.notes, [note]);
    }

    #[test]
    fn should_err_if_note_has_invalid_id() {
        let mut data = ExchangeData::default();
        let mut col = Collection::new();
        let now_micros = TimestampMillis::now().0 * 1000;

        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.id = NoteId(now_micros);
        col.add_note_only_with_id_undoable(&mut note).unwrap();

        assert!(data
            .gather_data(&mut col, SearchNode::WholeCollection, true, true)
            .is_err());
    }
}
