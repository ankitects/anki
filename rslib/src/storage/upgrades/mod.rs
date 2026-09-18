// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// The minimum schema version we can open.
pub(super) const SCHEMA_MIN_VERSION: u8 = 11;
/// The version new files are initially created with.
pub(super) const SCHEMA_STARTING_VERSION: u8 = 11;
/// The maximum schema version we can open.
pub(super) const SCHEMA_MAX_VERSION: u8 = 18;

use super::SchemaVersion;
use super::SqliteStorage;
use crate::error::Result;

impl SqliteStorage {
    pub(super) fn upgrade_to_latest_schema(&self, ver: u8, server: bool) -> Result<()> {
        if ver < 14 {
            self.db
                .execute_batch(include_str!("schema14_upgrade.sql"))?;
            self.upgrade_deck_conf_to_schema14()?;
            self.upgrade_tags_to_schema14()?;
            self.upgrade_config_to_schema14()?;
        }
        if ver < 15 {
            self.db
                .execute_batch(include_str!("schema15_upgrade.sql"))?;
            self.upgrade_notetypes_to_schema15()?;
            self.upgrade_decks_to_schema15(server)?;
            self.upgrade_deck_conf_to_schema15()?;
        }
        if ver < 16 {
            self.upgrade_deck_conf_to_schema16(server)?;
            self.db.execute_batch("update col set ver = 16")?;
        }
        if ver < 17 {
            self.upgrade_tags_to_schema17()?;
            self.db.execute_batch("update col set ver = 17")?;
        }
        if ver < 18 {
            self.db
                .execute_batch(include_str!("schema18_upgrade.sql"))?;
        }

        // in some future schema upgrade, we may want to change
        // _collapsed to _expanded in DeckCommon and invert existing values, so
        // that we can avoid serializing the values in the default case, and use
        // DeckCommon::default() in new_normal() and new_filtered()

        Ok(())
    }

    pub(super) fn downgrade_to(&self, ver: SchemaVersion) -> Result<()> {
        match ver {
            SchemaVersion::V11 => self.downgrade_to_schema_11(),
            SchemaVersion::V18 => Ok(()),
        }
    }

    fn downgrade_to_schema_11(&self) -> Result<()> {
        self.begin_trx()?;

        self.db
            .execute_batch(include_str!("schema18_downgrade.sql"))?;
        self.downgrade_deck_conf_from_schema16()?;
        self.downgrade_decks_from_schema15()?;
        self.downgrade_notetypes_from_schema15()?;
        self.downgrade_config_from_schema14()?;
        self.downgrade_tags_from_schema14()?;
        self.db
            .execute_batch(include_str!("schema11_downgrade.sql"))?;

        self.commit_trx()?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use anki_io::new_tempfile;

    use super::*;
    use crate::collection::CollectionBuilder;
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogId;
    use crate::revlog::RevlogReviewKind;

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn assert_18_is_latest_schema_version() {
        assert_eq!(
            18, SCHEMA_MAX_VERSION,
            "must implement SqliteStorage::downgrade_to(SchemaVersion::V18)"
        );
    }

    #[test]
    fn note_card_and_revlog_survive_upgrade_roundtrip() -> Result<()> {
        let tempfile = new_tempfile()?;
        let mut col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()?;
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "Migration question")?;
        note.set_field(1, "Migration answer")?;
        note.tags = vec!["migration".into()];
        col.add_note(&mut note, DeckId(1))?;
        col.storage
            .db
            .execute("update cards set factor = 1400", [])?;
        let original_note = col.storage.get_note(note.id)?.unwrap();
        let original_card = col.get_first_card();
        let review = RevlogEntry {
            id: RevlogId(1_600_000_000_000),
            cid: original_card.id,
            button_chosen: 3,
            interval: 10,
            last_interval: 5,
            ease_factor: 1400,
            taken_millis: 1234,
            review_kind: RevlogReviewKind::Review,
            ..Default::default()
        };
        col.storage.add_revlog_entry(&review, false)?;
        col.close(Some(SchemaVersion::V11))?;
        let col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()?;
        assert_eq!(
            col.storage.db_scalar::<u8>("select ver from col")?,
            SCHEMA_MAX_VERSION
        );
        assert_eq!(col.storage.get_note(note.id)?, Some(original_note));
        assert_eq!(col.storage.get_all_cards(), vec![original_card]);
        assert_eq!(
            col.storage.get_revlog_entries_for_card(review.cid)?,
            vec![review]
        );
        Ok(())
    }
}
