// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]
#![allow(dead_code)]

use itertools::Itertools;
use tempfile::tempdir;
use tempfile::TempDir;

use crate::collection::open_test_collection;
use crate::collection::CollectionBuilder;
use crate::deckconfig::DeckConfigInner;
use crate::io::create_dir;
use crate::media::MediaManager;
use crate::prelude::*;

pub(crate) fn open_fs_test_collection(name: &str) -> (Collection, TempDir) {
    let tempdir = tempdir().unwrap();
    let dir = tempdir.path();
    let media_folder = dir.join(format!("{name}.media"));
    create_dir(&media_folder).unwrap();
    let col = CollectionBuilder::new(dir.join(format!("{name}.anki2")))
        .set_media_paths(media_folder, dir.join(format!("{name}.mdb")))
        .build()
        .unwrap();
    (col, tempdir)
}

pub(crate) fn open_test_collection_with_learning_card() -> Collection {
    let mut col = open_test_collection();
    NoteAdder::basic(&mut col).add(&mut col);
    col.answer_again();
    col
}

pub(crate) fn open_test_collection_with_relearning_card() -> Collection {
    let mut col = open_test_collection();
    NoteAdder::basic(&mut col).add(&mut col);
    col.answer_easy();
    col.storage
        .db
        .execute_batch("UPDATE cards SET due = 0")
        .unwrap();
    col.clear_study_queues();
    col.answer_again();
    col
}

impl Collection {
    pub(crate) fn new_v3() -> Collection {
        let mut col = open_test_collection();
        col.set_config_bool(BoolKey::Sched2021, true, false)
            .unwrap();
        col
    }

    pub(crate) fn add_media(&self, media: &[(&str, &[u8])]) {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db).unwrap();
        for (name, data) in media {
            mgr.add_file(name, data).unwrap();
        }
    }

    pub(crate) fn get_all_notes(&mut self) -> Vec<Note> {
        self.storage.get_all_notes()
    }

    pub(crate) fn get_first_card(&self) -> Card {
        self.storage.get_all_cards().pop().unwrap()
    }

    pub(crate) fn set_default_learn_steps(&mut self, steps: Vec<f32>) {
        self.update_default_deck_config(|config| config.learn_steps = steps);
    }

    pub(crate) fn set_default_relearn_steps(&mut self, steps: Vec<f32>) {
        self.update_default_deck_config(|config| config.relearn_steps = steps);
    }

    /// Updates with the modified config, then resorts and adjusts remaining
    /// steps in the default deck.
    pub(crate) fn update_default_deck_config(
        &mut self,
        modifier: impl FnOnce(&mut DeckConfigInner),
    ) {
        let config = self
            .get_deck_config(DeckConfigId(1), false)
            .unwrap()
            .unwrap();
        let mut new_config = config.clone();

        modifier(&mut new_config.inner);

        self.update_deck_config_inner(&mut new_config, config.clone(), None)
            .unwrap();
        self.sort_deck(DeckId(1), config.inner.new_card_insert_order(), Usn(0))
            .unwrap();
        self.adjust_remaining_steps_in_deck(DeckId(1), Some(&config), Some(&new_config), Usn(0))
            .unwrap();
    }
}

#[derive(Debug, Default, Clone)]
pub(crate) struct DeckAdder {
    name: NativeDeckName,
    filtered: bool,
    config: Option<DeckConfig>,
}

impl DeckAdder {
    pub(crate) fn new(machine_name: impl Into<String>) -> Self {
        Self {
            name: NativeDeckName::from_native_str(machine_name),
            ..Default::default()
        }
    }

    pub(crate) fn filtered(mut self, filtered: bool) -> Self {
        self.filtered = filtered;
        self
    }

    pub(crate) fn with_config(mut self, modifier: impl FnOnce(&mut DeckConfig)) -> Self {
        let mut config = DeckConfig::default();
        modifier(&mut config);
        self.config = Some(config);
        self
    }

    pub(crate) fn add(mut self, col: &mut Collection) -> Deck {
        let config_opt = self.config.take();
        let mut deck = self.deck();
        if let Some(mut config) = config_opt {
            col.add_or_update_deck_config(&mut config).unwrap();
            deck.normal_mut()
                .expect("can't set config for filtered deck")
                .config_id = config.id.0;
        }
        col.add_or_update_deck(&mut deck).unwrap();
        deck
    }

    pub(crate) fn deck(self) -> Deck {
        let mut deck = if self.filtered {
            Deck::new_filtered()
        } else {
            Deck::new_normal()
        };
        deck.name = self.name;
        deck
    }
}

#[derive(Debug, Clone)]
pub(crate) struct NoteAdder {
    note: Note,
    deck: DeckId,
}

impl NoteAdder {
    pub(crate) fn new(col: &mut Collection, notetype: &str) -> Self {
        Self {
            note: col
                .get_notetype_by_name(notetype)
                .unwrap()
                .unwrap()
                .new_note(),
            deck: DeckId(1),
        }
    }

    pub(crate) fn basic(col: &mut Collection) -> Self {
        Self::new(col, "basic")
    }

    pub(crate) fn cloze(col: &mut Collection) -> Self {
        Self::new(col, "cloze")
    }

    pub(crate) fn fields(mut self, fields: &[&str]) -> Self {
        *self.note.fields_mut() = fields.iter().map(ToString::to_string).collect();
        self
    }

    pub(crate) fn deck(mut self, deck: DeckId) -> Self {
        self.deck = deck;
        self
    }

    pub(crate) fn add(mut self, col: &mut Collection) -> Note {
        col.add_note(&mut self.note, self.deck).unwrap();
        self.note
    }

    pub(crate) fn note(self) -> Note {
        self.note
    }
}

#[derive(Debug, Clone)]
pub(crate) struct CardAdder {
    siblings: usize,
    deck: DeckId,
    due_dates: Vec<&'static str>,
}

impl CardAdder {
    pub(crate) fn new() -> Self {
        Self {
            siblings: 1,
            deck: DeckId(1),
            due_dates: Vec::new(),
        }
    }

    pub(crate) fn siblings(mut self, siblings: usize) -> Self {
        self.siblings = siblings;
        self
    }

    pub(crate) fn deck(mut self, deck: DeckId) -> Self {
        self.deck = deck;
        self
    }

    /// Takes an array of strs and sets the due date of the first siblings
    /// accordingly, skipping siblings if a str is empty.
    pub(crate) fn due_dates(mut self, due_dates: impl Into<Vec<&'static str>>) -> Self {
        self.due_dates = due_dates.into();
        self
    }

    pub(crate) fn add(&self, col: &mut Collection) -> Vec<Card> {
        let field = (1..self.siblings + 1)
            .map(|n| format!("{{{{c{n}::}}}}"))
            .join("");
        let note = NoteAdder::cloze(col)
            .fields(&[&field, ""])
            .deck(self.deck)
            .add(col);

        if !self.due_dates.is_empty() {
            let cids = col.storage.card_ids_of_notes(&[note.id]).unwrap();
            for (ord, due_date) in self.due_dates.iter().enumerate() {
                if !due_date.is_empty() {
                    col.set_due_date(&cids[ord..ord + 1], due_date, None)
                        .unwrap();
                }
            }
        }

        col.storage.all_cards_of_note(note.id).unwrap()
    }
}
