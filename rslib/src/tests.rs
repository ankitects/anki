// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use tempfile::{tempdir, TempDir};

use crate::{
    collection::{open_test_collection, CollectionBuilder},
    deckconfig::UpdateDeckConfigsRequest,
    io::create_dir,
    media::MediaManager,
    pb::deck_configs_for_update::current_deck::Limits,
    prelude::*,
};

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
    col.add_new_note("basic");
    col.answer_again();
    col
}

pub(crate) fn open_test_collection_with_relearning_card() -> Collection {
    let mut col = open_test_collection();
    col.add_new_note("basic");
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
    pub(crate) fn add_media(&self, media: &[(&str, &[u8])]) {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db).unwrap();
        let mut ctx = mgr.dbctx();
        for (name, data) in media {
            mgr.add_file(&mut ctx, name, data).unwrap();
        }
    }

    pub(crate) fn new_note(&mut self, notetype: &str) -> Note {
        self.get_notetype_by_name(notetype)
            .unwrap()
            .unwrap()
            .new_note()
    }

    pub(crate) fn add_new_note(&mut self, notetype: &str) -> Note {
        let mut note = self.new_note(notetype);
        self.add_note(&mut note, DeckId(1)).unwrap();
        note
    }

    pub(crate) fn add_new_note_with_fields(&mut self, notetype: &str, fields: &[&str]) -> Note {
        let mut note = self.new_note(notetype);
        *note.fields_mut() = fields.iter().map(ToString::to_string).collect();
        self.add_note(&mut note, DeckId(1)).unwrap();
        note
    }

    pub(crate) fn get_all_notes(&mut self) -> Vec<Note> {
        self.storage.get_all_notes()
    }

    pub(crate) fn add_deck_with_machine_name(&mut self, name: &str, filtered: bool) -> Deck {
        let mut deck = new_deck_with_machine_name(name, filtered);
        self.add_deck_inner(&mut deck, Usn(1)).unwrap();
        deck
    }

    pub(crate) fn get_first_card(&self) -> Card {
        self.storage.get_all_cards().pop().unwrap()
    }

    pub(crate) fn get_first_deck_config(&mut self) -> DeckConfig {
        self.storage.all_deck_config().unwrap().pop().unwrap()
    }

    pub(crate) fn set_default_learn_steps(&mut self, steps: Vec<f32>) {
        let mut config = self.get_first_deck_config();
        config.inner.learn_steps = steps;
        self.update_default_deck_config(config);
    }

    pub(crate) fn set_default_relearn_steps(&mut self, steps: Vec<f32>) {
        let mut config = self.get_first_deck_config();
        config.inner.relearn_steps = steps;
        self.update_default_deck_config(config);
    }

    pub(crate) fn update_default_deck_config(&mut self, config: DeckConfig) {
        self.update_deck_configs(UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: vec![config],
            removed_config_ids: vec![],
            apply_to_children: false,
            card_state_customizer: "".to_string(),
            limits: Limits::default(),
        })
        .unwrap();
    }
}

pub(crate) fn new_deck_with_machine_name(name: &str, filtered: bool) -> Deck {
    let mut deck = if filtered {
        Deck::new_filtered()
    } else {
        Deck::new_normal()
    };
    deck.name = NativeDeckName::from_native_str(name);
    deck
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

    #[allow(dead_code)]
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

    pub(crate) fn add(self, col: &mut Collection) -> Deck {
        let mut deck = if self.filtered {
            Deck::new_filtered()
        } else {
            Deck::new_normal()
        };
        deck.name = self.name;
        if let Some(mut config) = self.config {
            col.add_or_update_deck_config(&mut config).unwrap();
            deck.normal_mut()
                .expect("can't set config for filtered deck")
                .config_id = config.id.0;
        }
        col.add_or_update_deck(&mut deck).unwrap();
        deck
    }
}
