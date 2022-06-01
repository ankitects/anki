// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use tempfile::{tempdir, TempDir};

use crate::{collection::CollectionBuilder, media::MediaManager, prelude::*};

pub(crate) fn open_fs_test_collection(name: &str) -> (Collection, TempDir) {
    let tempdir = tempdir().unwrap();
    let dir = tempdir.path();
    let media_folder = dir.join(format!("{name}.media"));
    std::fs::create_dir(&media_folder).unwrap();
    let col = CollectionBuilder::new(dir.join(format!("{name}.anki2")))
        .set_media_paths(media_folder, dir.join(format!("{name}.mdb")))
        .build()
        .unwrap();
    (col, tempdir)
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
