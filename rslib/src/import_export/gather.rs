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
    search::{Negated, SearchNode, SortMode},
    storage::ids_to_string,
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

fn sibling_search(notes: &[Note], cards: &[Card]) -> SearchBuilder {
    let mut nids = String::new();
    ids_to_string(&mut nids, notes.iter().map(|note| note.id));
    let mut cids = String::new();
    ids_to_string(&mut cids, cards.iter().map(|card| card.id));
    SearchBuilder::from(SearchNode::NoteIds(nids)).and(SearchNode::CardIds(cids).negated())
}

fn optional_deck_search(deck_id: Option<DeckId>) -> SearchNode {
    if let Some(did) = deck_id {
        SearchNode::from_deck_id(did, true)
    } else {
        SearchNode::WholeCollection
    }
}

impl ExportData {
    pub(super) fn gather_data(
        &mut self,
        col: &mut Collection,
        deck_id: Option<DeckId>,
        with_scheduling: bool,
    ) -> Result<()> {
        self.decks = col.gather_decks(deck_id)?;
        let search = optional_deck_search(deck_id);
        self.notes = col.gather_notes(search.clone())?;
        self.cards = col.gather_cards(search, &self.notes, deck_id)?;
        self.notetypes = col.gather_notetypes(&self.notes)?;

        if with_scheduling {
            self.revlog = col.gather_revlog(&self.cards)?;
            self.decks_configs = col.gather_deck_configs(&self.decks)?;
        } else {
            self.remove_scheduling_information(col);
        };

        Ok(())
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
    fn gather_decks(&mut self, deck_id: Option<DeckId>) -> Result<Vec<Deck>> {
        if let Some(did) = deck_id {
            let deck = self.get_deck(did)?.ok_or(AnkiError::NotFound)?;
            self.storage
                .deck_id_with_children(&deck)?
                .iter()
                .filter(|did| **did != DeckId(1))
                .map(|did| self.storage.get_deck(*did)?.ok_or(AnkiError::NotFound))
                .collect()
        } else {
            Ok(self
                .storage
                .get_all_decks()?
                .into_iter()
                .filter(|deck| deck.id != DeckId(1))
                .collect())
        }
    }

    fn gather_notes(&mut self, search: SearchNode) -> Result<Vec<Note>> {
        self.search_notes(search, SortMode::NoOrder)?
            .iter()
            .map(|nid| self.storage.get_note(*nid)?.ok_or(AnkiError::NotFound))
            .collect()
    }

    fn gather_cards(
        &mut self,
        search: SearchNode,
        notes: &[Note],
        deck_id: Option<DeckId>,
    ) -> Result<Vec<Card>> {
        let mut cards: Vec<_> = self
            .search_cards(search, SortMode::NoOrder)?
            .iter()
            .map(|cid| self.storage.get_card(*cid)?.ok_or(AnkiError::NotFound))
            .collect::<Result<_>>()?;

        if let Some(did) = deck_id {
            let mut siblings = self.gather_siblings(notes, &cards, did)?;
            cards.append(&mut siblings);
        }

        Ok(cards)
    }

    fn gather_siblings(
        &mut self,
        notes: &[Note],
        cards: &[Card],
        deck_id: DeckId,
    ) -> Result<Vec<Card>> {
        self.search_cards(sibling_search(notes, cards), SortMode::NoOrder)?
            .iter()
            .map(|cid| {
                let mut card = self.storage.get_card(*cid)?.ok_or(AnkiError::NotFound)?;
                card.deck_id = deck_id;
                Ok(card)
            })
            .collect()
    }

    fn gather_notetypes(&mut self, notes: &[Note]) -> Result<Vec<Notetype>> {
        notes
            .iter()
            .map(|note| note.notetype_id)
            .unique()
            .map(|ntid| self.storage.get_notetype(ntid)?.ok_or(AnkiError::NotFound))
            .collect()
    }

    fn gather_revlog(&mut self, cards: &[Card]) -> Result<Vec<RevlogEntry>> {
        let mut cids = String::new();
        ids_to_string(&mut cids, cards.iter().map(|card| card.id));
        self.storage.get_revlog_entries_for_card_ids(cids)
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
