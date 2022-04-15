// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    path::PathBuf,
};

use itertools::Itertools;

use crate::{
    decks::{immediate_parent_name, NormalDeck},
    io::filename_is_safe,
    latex::extract_latex,
    prelude::*,
    revlog::RevlogEntry,
    text::{extract_media_refs, extract_underscored_css_imports, extract_underscored_references},
};

#[derive(Debug, Default)]
pub(super) struct ExchangeData {
    pub(super) decks: Vec<Deck>,
    pub(super) notes: Vec<Note>,
    pub(super) cards: Vec<Card>,
    pub(super) notetypes: Vec<Notetype>,
    pub(super) revlog: Vec<RevlogEntry>,
    pub(super) deck_configs: Vec<DeckConfig>,
    pub(super) media_paths: HashSet<PathBuf>,
    pub(super) days_elapsed: u32,
}

impl ExchangeData {
    pub(super) fn gather_data(
        &mut self,
        col: &mut Collection,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<()> {
        self.days_elapsed = col.timing_today()?.days_elapsed;
        self.notes = col.gather_notes(search)?;
        self.cards = col.gather_cards()?;
        self.decks = col.gather_decks()?;
        self.notetypes = col.gather_notetypes()?;

        if with_scheduling {
            self.revlog = col.gather_revlog()?;
            self.deck_configs = col.gather_deck_configs(&self.decks)?;
        } else {
            self.remove_scheduling_information(col);
        };

        col.storage.clear_searched_notes_table()?;
        col.storage.clear_searched_cards_table()
    }

    pub(super) fn gather_media_paths(&mut self) {
        let mut inserter = |name: &str| {
            if filename_is_safe(name) {
                self.media_paths.insert(PathBuf::from(name));
            }
        };
        let svg_getter = svg_getter(&self.notetypes);
        for note in self.notes.iter() {
            gather_media_paths_from_note(note, &mut inserter, &svg_getter);
        }
        for notetype in self.notetypes.iter() {
            gather_media_paths_from_notetype(notetype, &mut inserter);
        }
    }

    fn remove_scheduling_information(&mut self, col: &Collection) {
        self.remove_system_tags();
        self.reset_deck_config_ids();
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

    fn reset_deck_config_ids(&mut self) {
        for deck in self.decks.iter_mut() {
            if let Ok(normal_mut) = deck.normal_mut() {
                normal_mut.config_id = 1;
            } else {
                // TODO: scheduling case
                deck.kind = DeckKind::Normal(NormalDeck {
                    config_id: 1,
                    ..Default::default()
                })
            }
        }
    }

    fn reset_cards(&mut self, col: &Collection) {
        let mut position = col.get_next_card_position();
        for card in self.cards.iter_mut() {
            // schedule_as_new() removes cards from filtered decks, but we want to
            // leave cards in their current deck, and export filtered as regular decks
            let deck_id = card.deck_id;
            if card.schedule_as_new(position, true, true) {
                position += 1;
            }
            card.flags = 0;
            card.deck_id = deck_id;
        }
    }
}

fn gather_media_paths_from_note(
    note: &Note,
    inserter: &mut impl FnMut(&str),
    svg_getter: &impl Fn(NotetypeId) -> bool,
) {
    for field in note.fields() {
        for media_ref in extract_media_refs(field) {
            inserter(&media_ref.fname_decoded);
        }

        for latex in extract_latex(field, svg_getter(note.notetype_id)).1 {
            inserter(&latex.fname);
        }
    }
}

fn gather_media_paths_from_notetype(notetype: &Notetype, inserter: &mut impl FnMut(&str)) {
    for name in extract_underscored_css_imports(&notetype.config.css) {
        inserter(name);
    }
    for template in &notetype.templates {
        for template_side in [&template.config.q_format, &template.config.a_format] {
            for name in extract_underscored_references(template_side) {
                inserter(name);
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
    fn gather_notes(&mut self, search: impl TryIntoSearch) -> Result<Vec<Note>> {
        self.search_notes_into_table(search)?;
        self.storage.all_searched_notes()
    }

    fn gather_cards(&mut self) -> Result<Vec<Card>> {
        self.storage.search_cards_of_notes_into_table()?;
        self.storage.all_searched_cards()
    }

    fn gather_decks(&mut self) -> Result<Vec<Deck>> {
        let decks = self.storage.get_decks_for_search_cards()?;
        let parents = self.get_parent_decks(&decks)?;
        Ok(decks
            .into_iter()
            .filter(|deck| deck.id != DeckId(1))
            .chain(parents)
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
                parents.push(
                    self.storage
                        .get_deck_by_name(parent_name)?
                        .ok_or(AnkiError::DatabaseCheckRequired)?,
                );
                self.add_parent_decks(parent_name, parent_names, parents)?;
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
            .filter(|config_id| *config_id != DeckConfigId(1))
            .map(|config_id| {
                self.storage
                    .get_deck_config(config_id)?
                    .ok_or(AnkiError::NotFound)
            })
            .collect()
    }
}
