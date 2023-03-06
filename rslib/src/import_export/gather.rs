// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;

use itertools::Itertools;

use super::ExportProgress;
use super::IncrementableProgress;
use crate::decks::immediate_parent_name;
use crate::io::filename_is_safe;
use crate::latex::extract_latex;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::search::CardTableGuard;
use crate::search::NoteTableGuard;
use crate::text::extract_media_refs;
use crate::text::extract_underscored_css_imports;
use crate::text::extract_underscored_references;

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
    ) -> Result<()> {
        self.days_elapsed = col.timing_today()?.days_elapsed;
        self.creation_utc_offset = col.get_creation_utc_offset();
        let (notes, guard) = col.gather_notes(search)?;
        self.notes = notes;
        let (cards, guard) = guard.col.gather_cards()?;
        self.cards = cards;
        self.decks = guard.col.gather_decks(with_scheduling)?;
        self.notetypes = guard.col.gather_notetypes()?;
        self.check_ids()?;

        if with_scheduling {
            self.revlog = guard.col.gather_revlog()?;
            self.deck_configs = guard.col.gather_deck_configs(&self.decks)?;
        } else {
            self.remove_scheduling_information(guard.col);
        };

        Ok(())
    }

    pub(super) fn gather_media_names(
        &mut self,
        progress: &mut IncrementableProgress<ExportProgress>,
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
            gather_media_names_from_notetype(notetype, &mut inserter);
        }
        Ok(())
    }

    fn remove_scheduling_information(&mut self, col: &Collection) {
        self.remove_system_tags();
        self.reset_deck_config_ids_and_limits();
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

    fn reset_deck_config_ids_and_limits(&mut self) {
        for deck in self.decks.iter_mut() {
            if let Ok(normal_mut) = deck.normal_mut() {
                normal_mut.config_id = 1;
                normal_mut.review_limit = None;
                normal_mut.review_limit_today = None;
                normal_mut.new_limit = None;
                normal_mut.new_limit_today = None;
            } else {
                // filtered decks are reset at import time for legacy reasons
            }
        }
    }

    fn reset_cards(&mut self, col: &Collection) {
        let mut position = col.get_next_card_position();
        for card in self.cards.iter_mut() {
            // schedule_as_new() removes cards from filtered decks, but we want to
            // leave cards in their current deck, which gets converted to a regular
            // deck on import
            let deck_id = card.deck_id;
            if card.schedule_as_new(position, true, true) {
                position += 1;
            }
            card.flags = 0;
            card.deck_id = deck_id;
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

fn gather_media_names_from_notetype(notetype: &Notetype, inserter: &mut impl FnMut(String)) {
    for name in extract_underscored_css_imports(&notetype.config.css) {
        inserter(name.to_string());
    }
    for template in &notetype.templates {
        for template_side in [&template.config.q_format, &template.config.a_format] {
            for name in extract_underscored_references(template_side) {
                inserter(name.to_string());
            }
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
    fn gather_notes(&mut self, search: impl TryIntoSearch) -> Result<(Vec<Note>, NoteTableGuard)> {
        let guard = self.search_notes_into_table(search)?;
        guard
            .col
            .storage
            .all_searched_notes()
            .map(|notes| (notes, guard))
    }

    fn gather_cards(&mut self) -> Result<(Vec<Card>, CardTableGuard)> {
        let guard = self.search_cards_of_notes_into_table()?;
        guard
            .col
            .storage
            .all_searched_cards()
            .map(|cards| (cards, guard))
    }

    /// If with_scheduling, also gather all original decks of cards in filtered
    /// decks, so they don't have to be converted to regular decks on import.
    /// If not with_scheduling, skip exporting the default deck to avoid
    /// changing the importing client's defaults.
    fn gather_decks(&mut self, with_scheduling: bool) -> Result<Vec<Deck>> {
        let decks = if with_scheduling {
            self.storage.get_decks_and_original_for_search_cards()
        } else {
            self.storage.get_decks_for_search_cards()
        }?;
        let parents = self.get_parent_decks(&decks)?;
        Ok(decks
            .into_iter()
            .chain(parents)
            .filter(|deck| with_scheduling || deck.id != DeckId(1))
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
    use crate::collection::open_test_collection;
    use crate::search::SearchNode;

    #[test]
    fn should_gather_valid_notes() {
        let mut data = ExchangeData::default();
        let mut col = open_test_collection();

        let note = NoteAdder::basic(&mut col).add(&mut col);
        data.gather_data(&mut col, SearchNode::WholeCollection, true)
            .unwrap();

        assert_eq!(data.notes, [note]);
    }

    #[test]
    fn should_err_if_note_has_invalid_id() {
        let mut data = ExchangeData::default();
        let mut col = open_test_collection();
        let now_micros = TimestampMillis::now().0 * 1000;

        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.id = NoteId(now_micros);
        col.add_note_only_with_id_undoable(&mut note).unwrap();

        assert!(data
            .gather_data(&mut col, SearchNode::WholeCollection, true)
            .is_err());
    }
}
