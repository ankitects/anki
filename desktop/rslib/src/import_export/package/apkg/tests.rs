// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::collections::HashSet;
use std::fs::File;
use std::io::Write;

use anki_io::read_file;
use anki_proto::import_export::ImportAnkiPackageOptions;

use crate::import_export::package::ExportAnkiPackageOptions;
use crate::media::files::sha1_of_data;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::search::SearchNode;
use crate::tests::open_fs_test_collection;

const SAMPLE_JPG: &str = "sample.jpg";
const SAMPLE_MP3: &str = "sample.mp3";
const SAMPLE_JS: &str = "_sample.js";
const JPG_DATA: &[u8] = b"1";
const MP3_DATA: &[u8] = b"2";
const JS_DATA: &[u8] = b"3";
const EXISTING_MP3_DATA: &[u8] = b"4";

#[test]
fn roundtrip() {
    roundtrip_inner(true);
    roundtrip_inner(false);
}

fn roundtrip_inner(legacy: bool) {
    let (mut src_col, src_tempdir) = open_fs_test_collection("src");
    let (mut target_col, _target_tempdir) = open_fs_test_collection("target");
    let apkg_path = src_tempdir.path().join("test.apkg");

    let (main_deck, sibling_deck) = src_col.add_sample_decks();
    let notetype = src_col.add_sample_notetype();
    let note = src_col.add_sample_note(&main_deck, &sibling_deck, &notetype);
    src_col.add_sample_media();
    target_col.add_conflicting_media();

    src_col
        .export_apkg(
            &apkg_path,
            ExportAnkiPackageOptions {
                with_scheduling: true,
                with_deck_configs: true,
                with_media: true,
                legacy,
            },
            SearchNode::from_deck_name("parent::sample"),
            None,
        )
        .unwrap();
    target_col
        .import_apkg(&apkg_path, ImportAnkiPackageOptions::default())
        .unwrap();

    target_col.assert_decks();
    target_col.assert_notetype(&notetype);
    target_col.assert_note_and_media(&note);

    target_col.undo().unwrap();
    target_col.assert_empty();
}

impl Collection {
    fn add_sample_decks(&mut self) -> (Deck, Deck) {
        let sample = self.add_named_deck("parent\x1fsample");
        self.add_named_deck("parent\x1fsample\x1fchild");
        let siblings = self.add_named_deck("siblings");

        (sample, siblings)
    }

    fn add_named_deck(&mut self, name: &str) -> Deck {
        let mut deck = Deck::new_normal();
        deck.name = NativeDeckName::from_native_str(name);
        self.add_deck(&mut deck).unwrap();
        deck
    }

    fn add_sample_notetype(&mut self) -> Notetype {
        let mut nt = Notetype {
            name: "sample".into(),
            ..Default::default()
        };
        nt.add_field("sample");
        nt.add_template("sample1", "{{sample}}", "<script src=_sample.js></script>");
        nt.add_template("sample2", "{{sample}}2", "");
        self.add_notetype(&mut nt, true).unwrap();
        nt
    }

    fn add_sample_note(
        &mut self,
        main_deck: &Deck,
        sibling_decks: &Deck,
        notetype: &Notetype,
    ) -> Note {
        let mut sample = notetype.new_note();
        sample.fields_mut()[0] = format!("<img src='{SAMPLE_JPG}'> [sound:{SAMPLE_MP3}]");
        sample.tags = vec!["sample".into()];
        self.add_note(&mut sample, main_deck.id).unwrap();

        let card = self
            .storage
            .get_card_by_ordinal(sample.id, 1)
            .unwrap()
            .unwrap();
        self.set_deck(&[card.id], sibling_decks.id).unwrap();

        sample
    }

    fn add_sample_media(&self) {
        self.add_media(&[
            (SAMPLE_JPG, JPG_DATA),
            (SAMPLE_MP3, MP3_DATA),
            (SAMPLE_JS, JS_DATA),
        ]);
    }

    fn add_conflicting_media(&mut self) {
        let mut file = File::create(self.media_folder.join(SAMPLE_MP3)).unwrap();
        file.write_all(EXISTING_MP3_DATA).unwrap();
    }

    fn assert_decks(&mut self) {
        let existing_decks: HashSet<_> = self
            .get_all_deck_names(true)
            .unwrap()
            .into_iter()
            .map(|(_, name)| name)
            .collect();
        for deck in ["parent", "parent::sample", "siblings"] {
            assert!(existing_decks.contains(deck));
        }
        assert!(!existing_decks.contains("parent::sample::child"));
    }

    fn assert_notetype(&mut self, notetype: &Notetype) {
        assert!(self.get_notetype(notetype.id).unwrap().is_some());
    }

    fn assert_note_and_media(&mut self, note: &Note) {
        let sha1 = sha1_of_data(MP3_DATA);
        let new_mp3_name = format!("sample-{}.mp3", hex::encode(sha1));
        let csums = MediaManager::new(&self.media_folder, &self.media_db)
            .unwrap()
            .all_checksums_as_is();

        for (fname, orig_data) in [
            (SAMPLE_JPG, JPG_DATA),
            (SAMPLE_MP3, EXISTING_MP3_DATA),
            (new_mp3_name.as_str(), MP3_DATA),
            (SAMPLE_JS, JS_DATA),
        ] {
            // data should have been copied correctly
            assert_eq!(read_file(self.media_folder.join(fname)).unwrap(), orig_data);
            // and checksums in media db should be valid
            assert_eq!(*csums.get(fname).unwrap(), sha1_of_data(orig_data));
        }

        let imported_note = self.storage.get_note(note.id).unwrap().unwrap();
        assert!(imported_note.fields()[0].contains(&new_mp3_name));
    }

    fn assert_empty(&self) {
        assert!(self.get_all_deck_names(true).unwrap().is_empty());
        assert!(self.storage.get_all_note_ids().unwrap().is_empty());
        assert!(self.storage.get_all_card_ids().unwrap().is_empty());
        assert!(self.storage.all_tags().unwrap().is_empty());
    }
}
