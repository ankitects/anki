// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use rusqlite::params;
use rusqlite::types::FromSql;
use rusqlite::types::FromSqlError;
use rusqlite::types::ValueRef;
use rusqlite::OptionalExtension;
use rusqlite::Row;

use super::SqliteStorage;
use crate::error::Result;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;

pub(crate) struct StudiedToday {
    pub cards: u32,
    pub seconds: f64,
}

impl FromSql for RevlogReviewKind {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_revlog_entry(row: &Row) -> Result<RevlogEntry> {
    Ok(RevlogEntry {
        id: row.get(0)?,
        cid: row.get(1)?,
        usn: row.get(2)?,
        button_chosen: row.get(3)?,
        interval: row.get(4)?,
        last_interval: row.get(5)?,
        ease_factor: row.get(6)?,
        taken_millis: row.get(7).unwrap_or_default(),
        review_kind: row.get(8).unwrap_or_default(),
    })
}

impl SqliteStorage {
    pub(crate) fn fix_revlog_properties(&self) -> Result<usize> {
        self.db
            .prepare(include_str!("fix_props.sql"))?
            .execute([])
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_revlog_usns(&self) -> Result<()> {
        self.db
            .prepare("update revlog set usn = 0 where usn = -1")?
            .execute([])?;
        Ok(())
    }

    /// Adds the entry, if its id is unique. If it is not, and `uniquify` is
    /// true, adds it with a new id. Returns the added id.
    /// (I.e., the option is safe to unwrap, if `uniquify` is true.)
    pub(crate) fn add_revlog_entry(
        &self,
        entry: &RevlogEntry,
        uniquify: bool,
    ) -> Result<Option<RevlogId>> {
        let added = self
            .db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                uniquify,
                entry.id,
                entry.cid,
                entry.usn,
                entry.button_chosen,
                entry.interval,
                entry.last_interval,
                entry.ease_factor,
                entry.taken_millis,
                entry.review_kind as u8
            ])?;
        Ok((added > 0).then(|| RevlogId(self.db.last_insert_rowid())))
    }

    pub(crate) fn get_revlog_entry(&self, id: RevlogId) -> Result<Option<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id=?"))?
            .query_and_then([id], row_to_revlog_entry)?
            .next()
            .transpose()
    }

    /// Determine the the last review time based on the revlog.
    pub(crate) fn time_of_last_review(&self, card_id: CardId) -> Result<Option<TimestampSecs>> {
        self.db
            .prepare_cached(include_str!("time_of_last_review.sql"))?
            .query_row([card_id], |row| row.get(0))
            .optional()
            .map_err(Into::into)
    }

    /// Only intended to be used by the undo code, as Anki can not sync revlog
    /// deletions.
    pub(crate) fn remove_revlog_entry(&self, id: RevlogId) -> Result<()> {
        self.db
            .prepare_cached("delete from revlog where id = ?")?
            .execute([id])?;
        Ok(())
    }

    pub(crate) fn get_revlog_entries_for_card(&self, cid: CardId) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where cid=?"))?
            .query_and_then([cid], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards_after_stamp(
        &self,
        after: TimestampSecs,
    ) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids) and id >= ?"
            ))?
            .query_and_then([after.0 * 1000], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids)"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards_in_card_order(
        &self,
    ) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids) order by cid, id"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_export_dataset(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where (ease between 1 and 4) or (ease = 0 and factor = 0)",
                " order by cid, id"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_all_revlog_entries_in_card_order(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " order by cid, id"))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_all_revlog_entries(&self, after: TimestampSecs) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id >= ?"))?
            .query_and_then([after.0 * 1000], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn studied_today(&self, day_cutoff: TimestampSecs) -> Result<StudiedToday> {
        let start = day_cutoff.adding_secs(-86_400).as_millis();
        self.db
            .prepare_cached(include_str!("studied_today.sql"))?
            .query_map(
                [
                    start.0,
                    RevlogReviewKind::Manual as i64,
                    RevlogReviewKind::Rescheduled as i64,
                ],
                |row| {
                    Ok(StudiedToday {
                        cards: row.get(0)?,
                        seconds: row.get(1)?,
                    })
                },
            )?
            .next()
            .unwrap()
            .map_err(Into::into)
    }

    pub(crate) fn studied_today_by_deck(
        &self,
        day_cutoff: TimestampSecs,
    ) -> Result<Vec<(DeckId, usize)>> {
        let start = day_cutoff.adding_secs(-86_400).as_millis();
        self.db
            .prepare_cached(include_str!("studied_today_by_deck.sql"))?
            .query_and_then([start.0], |row| -> Result<_> {
                Ok((DeckId(row.get(0)?), row.get(1)?))
            })?
            .collect()
    }
    pub(crate) fn upgrade_revlog_to_v2(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("v2_upgrade.sql"))
            .map_err(Into::into)
    }
}

#[cfg(test)]
mod test {
    use std::path::Path;

    use anki_i18n::I18n;

    use super::*;
    use crate::card::Card;

    fn create_test_storage() -> SqliteStorage {
        SqliteStorage::open_or_create(Path::new(":memory:"), &I18n::template_only(), false, false)
            .unwrap()
    }

    /// A revlog entry with a fixed card id, used where the card is irrelevant.
    fn revlog(id: i64, kind: RevlogReviewKind, taken_millis: u32) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(id),
            cid: CardId(1),
            button_chosen: 3,
            taken_millis,
            review_kind: kind,
            ..Default::default()
        }
    }

    fn review_for_card(id: i64, cid: CardId, button_chosen: u8) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(id),
            cid,
            button_chosen,
            review_kind: RevlogReviewKind::Review,
            ..Default::default()
        }
    }

    #[test]
    fn add_revlog_entry_returns_new_id_and_persists_unique_entry() {
        let storage = create_test_storage();
        let entry = revlog(1000, RevlogReviewKind::Review, 42);

        let added = storage.add_revlog_entry(&entry, false).unwrap();

        assert_eq!(added, Some(RevlogId(1000)));
        assert_eq!(
            storage.get_revlog_entry(RevlogId(1000)).unwrap(),
            Some(entry)
        );
    }

    #[test]
    fn add_revlog_entry_reassigns_id_on_collision_when_uniquify() {
        let storage = create_test_storage();
        storage
            .add_revlog_entry(&revlog(1000, RevlogReviewKind::Review, 0), false)
            .unwrap();

        let added = storage
            .add_revlog_entry(&revlog(1000, RevlogReviewKind::Review, 0), true)
            .unwrap();

        // uniquify picks max(id)+1, so the option is safe to unwrap
        assert_eq!(added, Some(RevlogId(1001)));
        assert!(storage.get_revlog_entry(RevlogId(1000)).unwrap().is_some());
        assert!(storage.get_revlog_entry(RevlogId(1001)).unwrap().is_some());
    }

    #[test]
    fn add_revlog_entry_returns_none_on_collision_without_uniquify() {
        let storage = create_test_storage();
        let original = revlog(1000, RevlogReviewKind::Review, 5);
        storage.add_revlog_entry(&original, false).unwrap();

        let dup = revlog(1000, RevlogReviewKind::Manual, 9);
        let added = storage.add_revlog_entry(&dup, false).unwrap();

        assert_eq!(added, None);
        // the original row must be left untouched (INSERT OR IGNORE)
        assert_eq!(
            storage.get_revlog_entry(RevlogId(1000)).unwrap(),
            Some(original)
        );
    }

    #[test]
    fn get_revlog_entries_for_card_returns_only_matching_entries() {
        let storage = create_test_storage();
        storage
            .add_revlog_entry(&review_for_card(1, CardId(10), 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(2, CardId(10), 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(3, CardId(20), 3), false)
            .unwrap();

        let for_card = storage.get_revlog_entries_for_card(CardId(10)).unwrap();

        assert_eq!(for_card.len(), 2);
        assert!(for_card.iter().all(|e| e.cid == CardId(10)));
        assert!(storage
            .get_revlog_entries_for_card(CardId(999))
            .unwrap()
            .is_empty());
    }

    #[test]
    fn unknown_review_kind_falls_back_to_default() {
        let storage = create_test_storage();
        // A future/unknown review kind must not error when read back.
        storage
            .db
            .execute(
                "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) \
                 values (?,?,?,?,?,?,?,?,?)",
                params![100i64, 1i64, 0, 3, 0, 0, 0, 0, 99],
            )
            .unwrap();

        let entry = storage.get_revlog_entry(RevlogId(100)).unwrap().unwrap();

        assert_eq!(entry.review_kind, RevlogReviewKind::default());
    }

    #[test]
    fn time_of_last_review_returns_most_recent_qualifying_review() {
        let storage = create_test_storage();
        let cid = CardId(42);
        storage
            .add_revlog_entry(&review_for_card(1_000_000, cid, 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(3_000_000, cid, 2), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(2_000_000, cid, 4), false)
            .unwrap();

        let last = storage.time_of_last_review(cid).unwrap();

        // id is in millis; the stored review time is id / 1000
        assert_eq!(last, Some(TimestampSecs(3_000)));
    }

    #[test]
    fn time_of_last_review_ignores_entries_without_a_rating() {
        let storage = create_test_storage();
        let cid = CardId(7);
        // a reset entry has ease 0, so it is not a real review
        let reset = RevlogEntry {
            id: RevlogId(5_000_000),
            cid,
            button_chosen: 0,
            review_kind: RevlogReviewKind::Manual,
            ..Default::default()
        };
        storage.add_revlog_entry(&reset, false).unwrap();

        assert_eq!(storage.time_of_last_review(cid).unwrap(), None);
        assert_eq!(storage.time_of_last_review(CardId(999)).unwrap(), None);
    }

    #[test]
    fn studied_today_counts_reviews_in_window_excluding_manual_and_rescheduled() {
        let storage = create_test_storage();
        let day_cutoff = TimestampSecs(1_000_000);
        // window start (in millis) is (1_000_000 - 86_400) * 1000 = 913_600_000
        storage
            .add_revlog_entry(&revlog(950_000_000, RevlogReviewKind::Review, 5000), false)
            .unwrap();
        storage
            .add_revlog_entry(
                &revlog(950_000_001, RevlogReviewKind::Learning, 3000),
                false,
            )
            .unwrap();
        // excluded by kind
        storage
            .add_revlog_entry(&revlog(950_000_002, RevlogReviewKind::Manual, 9999), false)
            .unwrap();
        storage
            .add_revlog_entry(
                &revlog(950_000_003, RevlogReviewKind::Rescheduled, 9999),
                false,
            )
            .unwrap();
        // excluded by being before the window
        storage
            .add_revlog_entry(&revlog(900_000_000, RevlogReviewKind::Review, 9999), false)
            .unwrap();

        let studied = storage.studied_today(day_cutoff).unwrap();

        assert_eq!(studied.cards, 2);
        assert_eq!(studied.seconds, 8.0);
    }

    #[test]
    fn studied_today_returns_zero_for_empty_revlog() {
        let storage = create_test_storage();

        let studied = storage.studied_today(TimestampSecs(1_000_000)).unwrap();

        assert_eq!(studied.cards, 0);
        assert_eq!(studied.seconds, 0.0);
    }

    #[test]
    fn studied_today_by_deck_groups_by_home_deck_and_skips_unrated() {
        let mut col = Collection::new();
        let mut in_deck_100 = Card::default();
        let mut in_deck_200 = Card::default();
        let mut filtered_from_999 = Card::default();
        col.add_card(&mut in_deck_100).unwrap();
        col.add_card(&mut in_deck_200).unwrap();
        col.add_card(&mut filtered_from_999).unwrap();
        col.storage
            .db
            .execute(
                "update cards set did = 100, odid = 0 where id = ?",
                params![in_deck_100.id],
            )
            .unwrap();
        col.storage
            .db
            .execute(
                "update cards set did = 200, odid = 0 where id = ?",
                params![in_deck_200.id],
            )
            .unwrap();
        // a card currently in a filtered deck reports its original deck (odid)
        col.storage
            .db
            .execute(
                "update cards set did = 5, odid = 999 where id = ?",
                params![filtered_from_999.id],
            )
            .unwrap();

        let add = |id: i64, cid: CardId, button: u8| {
            col.storage
                .add_revlog_entry(&review_for_card(id, cid, button), false)
                .unwrap();
        };
        add(950_000_000, in_deck_100.id, 3);
        add(950_000_010, in_deck_200.id, 3);
        add(950_000_020, filtered_from_999.id, 3);
        // Repeated reviews still count as one studied card.
        add(950_000_025, in_deck_100.id, 3);
        // ease 0 is filtered out by the query
        add(950_000_030, in_deck_100.id, 0);

        let mut by_deck: Vec<(i64, usize)> = col
            .storage
            .studied_today_by_deck(TimestampSecs(1_000_000))
            .unwrap()
            .into_iter()
            .map(|(deck, count)| (deck.0, count))
            .collect();
        by_deck.sort();

        assert_eq!(by_deck, vec![(100, 1), (200, 1), (999, 1)]);
    }

    #[test]
    fn studied_today_by_deck_counts_only_cards_reviewed_after_day_start() {
        let storage = create_test_storage();
        let day_cutoff = TimestampSecs(1_000_000);
        let start = 913_600_000;
        for (id, stamp) in [(1, start - 1), (2, start), (3, start + 1)] {
            let card = Card {
                id: CardId(id),
                deck_id: DeckId(id),
                ..Default::default()
            };
            storage.add_card_if_unique(&card).unwrap();
            storage
                .add_revlog_entry(&review_for_card(stamp, card.id, 3), false)
                .unwrap();
        }

        assert_eq!(
            storage.studied_today_by_deck(day_cutoff).unwrap(),
            vec![(DeckId(3), 1)]
        );
    }

    #[test]
    fn studied_today_by_deck_requires_rating_and_review_kind_or_factor() {
        let cases = [
            (RevlogReviewKind::Learning, 3, 0, true),
            (RevlogReviewKind::Review, 3, 0, true),
            (RevlogReviewKind::Relearning, 3, 0, true),
            (RevlogReviewKind::Filtered, 3, 0, false),
            (RevlogReviewKind::Filtered, 3, 2500, true),
            (RevlogReviewKind::Manual, 3, 0, false),
            (RevlogReviewKind::Rescheduled, 3, 0, false),
            (RevlogReviewKind::Review, 0, 2500, false),
        ];
        for (kind, button, factor, counted) in cases {
            let storage = create_test_storage();
            let card = Card {
                id: CardId(1),
                ..Default::default()
            };
            storage.add_card_if_unique(&card).unwrap();
            let entry = RevlogEntry {
                review_kind: kind,
                ease_factor: factor,
                ..review_for_card(950_000_000, card.id, button)
            };
            storage.add_revlog_entry(&entry, false).unwrap();

            let expected = if counted {
                vec![(card.deck_id, 1)]
            } else {
                vec![]
            };
            assert_eq!(
                storage
                    .studied_today_by_deck(TimestampSecs(1_000_000))
                    .unwrap(),
                expected,
                "kind {kind:?}, button {button}, factor {factor}"
            );
        }
    }

    #[test]
    fn revlog_review_kind_from_sql_rejects_invalid_values() {
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Integer(1)),
            Ok(RevlogReviewKind::Review)
        ));
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Integer(99)),
            Err(FromSqlError::InvalidType)
        ));
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Text(b"x")),
            Err(FromSqlError::InvalidType)
        ));
    }
}
