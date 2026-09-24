// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::collections::HashSet;
use std::fs::File;
use std::io::Write;

use anki_io::new_tempfile;
use anki_io::read_file;
use anki_proto::import_export::ImportAnkiPackageOptions;
use zip::ZipArchive;

use crate::collection::CollectionBuilder;
use crate::import_export::package::meta::MetaExt;
use crate::import_export::package::ExportAnkiPackageOptions;
use crate::import_export::package::Meta;
use crate::media::files::sha1_of_data;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::search::SearchNode;
use crate::tests::open_fs_test_collection;
use crate::tests::DeckAdder;
use crate::tests::NoteAdder;

const SAMPLE_JPG: &str = "sample.jpg";
const SAMPLE_MP3: &str = "sample.mp3";
const SAMPLE_JS: &str = "_sample.js";
const JPG_DATA: &[u8] = b"1";
const MP3_DATA: &[u8] = b"2";
const JS_DATA: &[u8] = b"3";
const EXISTING_MP3_DATA: &[u8] = b"4";

#[test]
fn scheduled_import_replays_state_under_retained_destination_preset() -> Result<()> {
    scheduled_import_replays_under_preset(false)?;
    scheduled_import_replays_under_preset(true)
}

fn scheduled_import_replays_under_preset(legacy_destination: bool) -> Result<()> {
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_fsrs;

    let (mut source, source_dir) = open_fs_test_collection("source");
    let (mut target, _target_dir) = open_fs_test_collection("target");
    if legacy_destination {
        let mut config = target.get_deck_config(DeckConfigId(1), false)?.unwrap();
        config.clear_fsrs_params();
        config.inner.fsrs_params_6 = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec();
        target.add_or_update_deck_config(&mut config)?;
    }
    let path = source_dir.path().join("fsrs.apkg");
    let note = NoteAdder::basic(&mut source).add(&mut source);
    let mut card = source.storage.all_cards_of_note(note.id)?.remove(0);
    let mut source_config = source.get_deck_config(DeckConfigId(1), false)?.unwrap();
    source_config.inner.fsrs_params_7[2] = 0.1;
    source.add_or_update_deck_config(&mut source_config)?;
    let source_model = fsrs::FSRS::new(source_config.fsrs_params())?;
    let review = fsrs::FSRSItem {
        reviews: vec![fsrs::FSRSReview {
            rating: 3,
            delta_t: 0.0,
        }],
    };
    card.memory_state = Some(fsrs_memory_state_for_fsrs(
        &source_model,
        source_model.memory_state(review.clone(), None)?,
    ));
    card.ctype = CardType::Review;
    card.queue = CardQueue::Review;
    card.due = source.timing_today()?.days_elapsed as i32 + 10;
    card.interval = 10;
    card.last_review_time = Some(TimestampSecs(1_700_000_000));
    source.storage.update_card(&card)?;
    source.storage.add_revlog_entry(
        &RevlogEntry {
            id: RevlogId(1_700_000_000_000),
            cid: card.id,
            button_chosen: 3,
            review_kind: RevlogReviewKind::Learning,
            interval: 10,
            ..Default::default()
        },
        false,
    )?;
    source.export_apkg(
        &path,
        ExportAnkiPackageOptions {
            with_scheduling: true,
            with_deck_configs: true,
            with_media: false,
            legacy: false,
        },
        SearchNode::WholeCollection,
        None,
    )?;
    target.import_apkg(
        &path,
        ImportAnkiPackageOptions {
            with_scheduling: true,
            with_deck_configs: false,
            ..Default::default()
        },
    )?;
    let imported = target.storage.all_cards_of_note(note.id)?.remove(0);
    let config = target.get_deck_config(DeckConfigId(1), false)?.unwrap();
    let model = fsrs::FSRS::new(config.fsrs_params())?;
    let expected = fsrs_memory_state_for_fsrs(&model, model.memory_state(review, None)?);
    let actual = imported.memory_state.unwrap();
    assert_ne!(actual, card.memory_state.unwrap());
    assert!((actual.stability_internal - expected.stability_internal).abs() < 0.001);
    match (actual.stability_fast, expected.stability_fast) {
        (Some(a), Some(e)) => assert!((a - e).abs() < 0.001),
        (None, None) => (),
        states => panic!("mismatched model state: {states:?}"),
    }
    assert!((actual.difficulty - expected.difficulty).abs() < 0.001);
    assert_eq!(imported.last_review_time, card.last_review_time);
    assert_eq!(imported.interval, 10);
    assert_eq!(
        imported.due,
        target.timing_today()?.days_elapsed as i32 + 10
    );
    target.undo()?;
    assert!(target.storage.get_card(imported.id)?.is_none());
    Ok(())
}

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

fn export_and_reimport_with_fsrs_params(with_scheduling: bool) -> (DeckConfig, DeckConfig) {
    let (mut src_col, src_tempdir) = open_fs_test_collection("src");
    let (mut target_col, _target_tempdir) = open_fs_test_collection("target");
    let apkg_path = src_tempdir.path().join("fsrs.apkg");

    let deck = DeckAdder::new("fsrs-deck")
        .with_config(|c| {
            c.name = "fsrs-config".into();
            c.inner.fsrs_params_4 = vec![0.1; 17];
            c.inner.fsrs_params_5 = vec![0.2; 19];
            c.inner.fsrs_params_6 = vec![0.3; 21];
            c.inner.fsrs_params_7 = vec![0.4; 34];
        })
        .add(&mut src_col);
    NoteAdder::basic(&mut src_col)
        .deck(deck.id)
        .add(&mut src_col);

    src_col
        .export_apkg(
            &apkg_path,
            ExportAnkiPackageOptions {
                with_scheduling,
                with_deck_configs: true,
                with_media: false,
                legacy: false,
            },
            SearchNode::WholeCollection,
            None,
        )
        .unwrap();
    // Inspect the serialized package without applying the opening migration.
    let mut archive = ZipArchive::new(File::open(&apkg_path).unwrap()).unwrap();
    let meta = Meta::from_archive(&mut archive).unwrap();
    let mut tempfile = new_tempfile().unwrap();
    meta.copy(
        &mut archive.by_name(meta.collection_filename()).unwrap(),
        &mut tempfile,
    )
    .unwrap();
    let exported_col = CollectionBuilder::new(tempfile.path())
        .set_skip_fsrs_defaults_upgrade()
        .build()
        .unwrap();
    let exported = exported_col
        .storage
        .all_deck_config()
        .unwrap()
        .into_iter()
        .find(|c| c.name == "fsrs-config")
        .unwrap();
    target_col
        .import_apkg(
            &apkg_path,
            ImportAnkiPackageOptions {
                with_scheduling: true,
                with_deck_configs: true,
                ..Default::default()
            },
        )
        .unwrap();

    let imported = target_col
        .storage
        .all_deck_config()
        .unwrap()
        .into_iter()
        .find(|c| c.name == "fsrs-config")
        .expect("custom config should have been imported");
    (exported, imported)
}

#[test]
fn fsrs_params_preserved_on_export_with_scheduling() {
    let (exported, conf) = export_and_reimport_with_fsrs_params(true);
    assert_eq!(conf.inner.fsrs_params_4.len(), 17);
    assert_eq!(conf.inner.fsrs_params_5.len(), 19);
    assert_eq!(conf.inner.fsrs_params_6.len(), 21);
    assert_eq!(conf.inner.fsrs_params_7.len(), 34);
    assert_eq!(exported.inner, conf.inner);
}

#[test]
fn fsrs_params_stripped_on_export_without_scheduling() {
    let (exported, imported) = export_and_reimport_with_fsrs_params(false);
    assert!(exported.has_empty_fsrs_params());
    assert!(imported.inner.fsrs_params_4.is_empty());
    assert!(imported.inner.fsrs_params_5.is_empty());
    assert!(imported.inner.fsrs_params_6.is_empty());
    assert_eq!(imported.inner.fsrs_params_7, fsrs::DEFAULT_PARAMETERS);
}
