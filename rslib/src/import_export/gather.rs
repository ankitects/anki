// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    path::PathBuf,
};

use itertools::Itertools;

use crate::{
    card::{CardQueue, CardType},
    decks::NormalDeck,
    io::filename_is_safe,
    latex::extract_latex,
    prelude::*,
    revlog::RevlogEntry,
    text::{extract_media_refs, extract_underscored_css_imports, extract_underscored_references},
};

#[derive(Debug, Default)]
pub(super) struct ExportData {
    pub(super) decks: Vec<Deck>,
    pub(super) notes: Vec<Note>,
    pub(super) cards: Vec<Card>,
    pub(super) notetypes: Vec<Notetype>,
    pub(super) revlog: Vec<RevlogEntry>,
    pub(super) decks_configs: Vec<DeckConfig>,
    pub(super) media_paths: HashSet<PathBuf>,
}

impl ExportData {
    pub(super) fn gather_data(
        &mut self,
        col: &mut Collection,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<()> {
        self.notes = col.gather_notes(search)?;
        self.cards = col.gather_cards()?;
        self.decks = col.gather_decks()?;
        self.notetypes = col.gather_notetypes()?;

        if with_scheduling {
            self.revlog = col.gather_revlog()?;
            self.decks_configs = col.gather_deck_configs(&self.decks)?;
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
        // TODO: case folding? child tags?
        for note in self.notes.iter_mut() {
            note.tags = std::mem::take(&mut note.tags)
                .into_iter()
                .filter(|tag| !matches!(tag.as_str(), "marked" | "leech"))
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
            if card.ctype != CardType::New || card.queue != CardQueue::New {
                card.due = card.original_position.unwrap_or_else(|| {
                    position += 1;
                    position - 1
                }) as i32;
            }
            card.interval = 0;
            card.ease_factor = 0;
            card.reps = 0;
            card.lapses = 0;
            card.original_deck_id = DeckId(0);
            card.original_due = 0;
            card.original_position = None;
            card.queue = CardQueue::New;
            card.ctype = CardType::New;
            card.flags = 0;
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
        self.storage.search_notes_cards_into_table()?;
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
        let mut parent_names: HashSet<&str> =
            decks.iter().map(|deck| deck.name.as_native_str()).collect();
        let mut parents = Vec::new();
        for deck in decks {
            while let Some(parent_name) = deck.name.immediate_parent_name() {
                if parent_names.insert(parent_name) {
                    parents.push(
                        self.storage
                            .get_deck_by_name(parent_name)?
                            .ok_or(AnkiError::DatabaseCheckRequired)?,
                    )
                }
            }
        }
        Ok(parents)
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
