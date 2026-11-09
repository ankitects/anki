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
    use crate::tags::Tag;

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn assert_18_is_latest_schema_version() {
        assert_eq!(
            18, SCHEMA_MAX_VERSION,
            "must implement SqliteStorage::downgrade_to(SchemaVersion::V18)"
        );
    }

    #[test]
    fn migrated_entities_survive_schema_11_roundtrip() -> Result<()> {
        let tempfile = new_tempfile()?;
        let mut col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()?;

        let mut nt = col.basic_notetype();
        nt.id = NotetypeId(0);
        nt.name = "Migration notetype".into();
        nt.add_field("Extra");
        nt.add_template("Extra card", "{{Extra}}", "{{FrontSide}}");
        col.add_notetype(&mut nt, true)?;
        let original_notetype = col.storage.get_notetype(nt.id)?.unwrap();

        let mut deck_config = DeckConfig {
            name: "Migration preset".into(),
            ..Default::default()
        };
        deck_config.inner.learn_steps = vec![2.0, 20.0];
        col.add_or_update_deck_config(&mut deck_config)?;
        let original_deck_config = col
            .storage
            .get_deck_config(deck_config.id)?
            .expect("added deck config");

        let mut deck = Deck::new_normal();
        deck.name = NativeDeckName::from_human_name("Migration deck");
        deck.normal_mut()?.config_id = deck_config.id.0;
        col.add_or_update_deck(&mut deck)?;
        let original_deck = col.storage.get_deck(deck.id)?.expect("added deck");

        let mut note = nt.new_note();
        note.set_field(0, "Migration question")?;
        note.set_field(1, "Migration answer")?;
        note.set_field(2, "Migration extra")?;
        col.add_note(&mut note, deck.id)?;
        col.storage
            .db
            .execute("update cards set factor = 1400", [])?;
        col.set_config("migrationTest", &vec![1, 2, 3])?;
        let tag = Tag::new("migration::tag".into(), Usn(7));
        col.storage.register_tag(&tag)?;
        col.storage.add_card_grave(CardId(123), Usn(-1))?;

        let original_note = col.storage.get_note(note.id)?.unwrap();
        let original_cards = col.storage.get_all_cards();

        col.close(Some(SchemaVersion::V11))?;
        let mut col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()?;

        assert_eq!(
            col.storage.db_scalar::<u8>("select ver from col")?,
            SCHEMA_MAX_VERSION
        );
        assert_eq!(
            col.get_notetype(nt.id)?.as_deref(),
            Some(&original_notetype)
        );
        assert_eq!(col.get_deck(deck.id)?.as_deref(), Some(&original_deck));
        assert_eq!(
            col.get_deck_config(deck_config.id, false)?,
            Some(original_deck_config)
        );
        assert_eq!(
            col.get_config_optional::<Vec<i32>, _>("migrationTest"),
            Some(vec![1, 2, 3])
        );
        assert_eq!(col.storage.all_tags()?, vec![tag]);
        assert_eq!(col.storage.get_note(note.id)?, Some(original_note));
        assert_eq!(col.storage.get_all_cards(), original_cards);
        assert_eq!(
            col.storage.pending_graves(Usn(-1))?.cards,
            vec![CardId(123)]
        );
        Ok(())
    }
}
