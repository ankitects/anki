// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    iter,
};

use prost::Message;
use rusqlite::{named_params, params, Row};
use unicase::UniCase;

use super::SqliteStorage;
use crate::{
    card::CardQueue,
    config::SchedulerVersion,
    decks::{immediate_parent_name, DeckCommon, DeckKindContainer, DeckSchema11, DueCounts},
    error::DbErrorKind,
    prelude::*,
};

fn row_to_deck(row: &Row) -> Result<Deck> {
    let common = DeckCommon::decode(row.get_ref_unwrap(4).as_blob()?)?;
    let kind = DeckKindContainer::decode(row.get_ref_unwrap(5).as_blob()?)?;
    let id = row.get(0)?;
    Ok(Deck {
        id,
        name: NativeDeckName::from_native_str(row.get_ref_unwrap(1).as_str()?),
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        common,
        kind: kind.kind.ok_or_else(|| {
            AnkiError::db_error(
                format!("invalid deck kind: {}", id),
                DbErrorKind::MissingEntity,
            )
        })?,
    })
}

fn row_to_due_counts(row: &Row, v3: bool) -> Result<(DeckId, DueCounts)> {
    let deck_id = row.get(0)?;
    let new = row.get(1)?;
    let mut review = row.get(2)?;
    let interday: u32 = row.get(3)?;
    let intraday: u32 = row.get(4)?;
    let learning = if v3 {
        review += interday;
        intraday
    } else {
        intraday + interday
    };
    Ok((
        deck_id,
        DueCounts {
            new,
            review,
            learning,
        },
    ))
}

impl SqliteStorage {
    pub(crate) fn get_all_decks_as_schema11(&self) -> Result<HashMap<DeckId, DeckSchema11>> {
        self.get_all_decks()
            .map(|r| r.into_iter().map(|d| (d.id, d.into())).collect())
    }

    pub(crate) fn get_deck(&self, did: DeckId) -> Result<Option<Deck>> {
        self.db
            .prepare_cached(concat!(include_str!("get_deck.sql"), " where id = ?"))?
            .query_and_then([did], row_to_deck)?
            .next()
            .transpose()
    }

    pub(crate) fn get_all_decks(&self) -> Result<Vec<Deck>> {
        self.db
            .prepare(include_str!("get_deck.sql"))?
            .query_and_then([], row_to_deck)?
            .collect()
    }

    pub(crate) fn get_decks_map(&self) -> Result<HashMap<DeckId, Deck>> {
        self.db
            .prepare(include_str!("get_deck.sql"))?
            .query_and_then([], row_to_deck)?
            .map(|res| res.map(|d| (d.id, d)))
            .collect()
    }

    /// Get all deck names in sorted, human-readable form (::)
    pub(crate) fn get_all_deck_names(&self) -> Result<Vec<(DeckId, String)>> {
        self.db
            .prepare("select id, name from decks order by name")?
            .query_and_then([], |row| {
                Ok((
                    row.get(0)?,
                    row.get_ref_unwrap(1).as_str()?.replace('\x1f', "::"),
                ))
            })?
            .collect()
    }

    pub(crate) fn get_deck_id(&self, machine_name: &str) -> Result<Option<DeckId>> {
        self.db
            .prepare("select id from decks where name = ?")?
            .query_and_then([machine_name], |row| row.get(0))?
            .next()
            .transpose()
            .map_err(Into::into)
    }

    // caller should ensure name unique
    pub(crate) fn add_deck(&self, deck: &mut Deck) -> Result<()> {
        assert!(deck.id.0 == 0);
        deck.id.0 = self
            .db
            .prepare(include_str!("alloc_id.sql"))?
            .query_row([TimestampMillis::now()], |r| r.get(0))?;
        self.add_or_update_deck_with_existing_id(deck)
            .map_err(|err| {
                // restore id of 0
                deck.id.0 = 0;
                err
            })
    }

    pub(crate) fn update_deck(&self, deck: &Deck) -> Result<()> {
        if deck.id.0 == 0 {
            return Err(AnkiError::invalid_input("deck with id 0"));
        }
        let mut stmt = self.db.prepare_cached(include_str!("update_deck.sql"))?;
        let mut common = vec![];
        deck.common.encode(&mut common)?;
        let kind_enum = DeckKindContainer {
            kind: Some(deck.kind.clone()),
        };
        let mut kind = vec![];
        kind_enum.encode(&mut kind)?;
        let count = stmt.execute(params![
            deck.name.as_native_str(),
            deck.mtime_secs,
            deck.usn,
            common,
            kind,
            deck.id
        ])?;

        if count == 0 {
            Err(AnkiError::invalid_input(
                "update_deck() called with non-existent deck",
            ))
        } else {
            Ok(())
        }
    }

    /// Used for syncing&undo; will keep existing ID. Shouldn't be used to add
    /// new decks locally, since it does not allocate an id.
    pub(crate) fn add_or_update_deck_with_existing_id(&self, deck: &Deck) -> Result<()> {
        if deck.id.0 == 0 {
            return Err(AnkiError::invalid_input("deck with id 0"));
        }
        let mut stmt = self
            .db
            .prepare_cached(include_str!("add_or_update_deck.sql"))?;
        let mut common = vec![];
        deck.common.encode(&mut common)?;
        let kind_enum = DeckKindContainer {
            kind: Some(deck.kind.clone()),
        };
        let mut kind = vec![];
        kind_enum.encode(&mut kind)?;
        stmt.execute(params![
            deck.id,
            deck.name.as_native_str(),
            deck.mtime_secs,
            deck.usn,
            common,
            kind
        ])?;

        Ok(())
    }

    pub(crate) fn remove_deck(&self, did: DeckId) -> Result<()> {
        self.db
            .prepare_cached("delete from decks where id = ?")?
            .execute([did])?;
        Ok(())
    }

    pub(crate) fn all_cards_in_single_deck(&self, did: DeckId) -> Result<Vec<CardId>> {
        self.db
            .prepare_cached(include_str!("cards_for_deck.sql"))?
            .query_and_then([did], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn child_decks(&self, parent: &Deck) -> Result<Vec<Deck>> {
        let prefix_start = format!("{}\x1f", parent.name);
        let prefix_end = format!("{}\x20", parent.name);
        self.db
            .prepare_cached(concat!(
                include_str!("get_deck.sql"),
                " where name >= ? and name < ?"
            ))?
            .query_and_then([prefix_start, prefix_end], row_to_deck)?
            .collect()
    }

    pub(crate) fn deck_id_with_children(&self, parent: &Deck) -> Result<Vec<DeckId>> {
        let prefix_start = format!("{}\x1f", parent.name);
        let prefix_end = format!("{}\x20", parent.name);
        self.db
            .prepare_cached("select id from decks where id = ? or (name >= ? and name < ?)")?
            .query_and_then(params![parent.id, prefix_start, prefix_end], |row| {
                row.get(0).map_err(Into::into)
            })?
            .collect()
    }

    pub(crate) fn deck_with_children(&self, deck_id: DeckId) -> Result<Vec<Deck>> {
        let deck = self.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?;
        let prefix_start = format!("{}\x1f", deck.name);
        let prefix_end = format!("{}\x20", deck.name);
        iter::once(Ok(deck))
            .chain(
                self.db
                    .prepare_cached(concat!(
                        include_str!("get_deck.sql"),
                        " where name > ? and name < ?"
                    ))?
                    .query_and_then([prefix_start, prefix_end], row_to_deck)?,
            )
            .collect()
    }

    /// Return the parents of `child`, with the most immediate parent coming first.
    pub(crate) fn parent_decks(&self, child: &Deck) -> Result<Vec<Deck>> {
        let mut decks: Vec<Deck> = vec![];
        while let Some(parent_name) = immediate_parent_name(
            decks
                .last()
                .map(|d| &d.name)
                .unwrap_or_else(|| &child.name)
                .as_native_str(),
        ) {
            if let Some(parent_did) = self.get_deck_id(parent_name)? {
                let parent = self.get_deck(parent_did)?.unwrap();
                decks.push(parent);
            } else {
                // missing parent
                break;
            }
        }

        Ok(decks)
    }

    pub(crate) fn due_counts(
        &self,
        sched: SchedulerVersion,
        day_cutoff: u32,
        learn_cutoff: u32,
        top_deck: Option<&str>,
        v3: bool,
    ) -> Result<HashMap<DeckId, DueCounts>> {
        let sched_ver = sched as u8;
        let mut params = named_params! {
            ":new_queue": CardQueue::New as u8,
            ":review_queue": CardQueue::Review as u8,
            ":day_cutoff": day_cutoff,
            ":sched_ver": sched_ver,
            ":learn_queue": CardQueue::Learn as u8,
            ":learn_cutoff": learn_cutoff,
            ":daylearn_queue": CardQueue::DayLearn as u8,
            ":preview_queue": CardQueue::PreviewRepeat as u8,
        }
        .to_vec();

        let sql;
        let prefix_start;
        let prefix_end;
        let top;
        if let Some(top_inner) = top_deck {
            // limited to deck node
            top = top_inner;
            prefix_start = format!("{}\x1f", top);
            prefix_end = format!("{}\x20", top);
            params.extend(named_params! {
                ":top_deck": top,
                ":prefix_start": prefix_start,
                ":prefix_end": prefix_end,
            });
            sql = concat!(
                include_str!("due_counts.sql"),
                " and did in (select id from decks where name = :top_deck ",
                "or (name >= :prefix_start and name < :prefix_end)) group by did "
            );
        } else {
            // entire tree
            sql = concat!(include_str!("due_counts.sql"), " group by did");
        }

        self.db
            .prepare_cached(sql)?
            .query_and_then(&*params, |row| row_to_due_counts(row, v3))?
            .collect()
    }

    /// Decks referenced by cards but missing.
    pub(crate) fn missing_decks(&self) -> Result<Vec<DeckId>> {
        self.db
            .prepare(include_str!("missing-decks.sql"))?
            .query_and_then([], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn deck_is_empty(&self, did: DeckId) -> Result<bool> {
        self.db
            .prepare_cached("select null from cards where did=?")?
            .query([did])?
            .next()
            .map(|o| o.is_none())
            .map_err(Into::into)
    }

    pub(crate) fn clear_deck_usns(&self) -> Result<()> {
        self.db
            .prepare("update decks set usn = 0 where usn != 0")?
            .execute([])?;
        Ok(())
    }

    /// Write active decks into temporary active_decks table.
    pub(crate) fn update_active_decks(&self, current: &Deck) -> Result<()> {
        self.db.execute_batch(concat!(
            "drop table if exists active_decks;",
            "create temporary table active_decks (id integer not null unique);"
        ))?;

        let top = current.name.as_native_str();
        let prefix_start = &format!("{}\x1f", top);
        let prefix_end = &format!("{}\x20", top);

        self.db
            .prepare_cached(include_str!("update_active.sql"))?
            .execute([top, prefix_start, prefix_end])?;

        Ok(())
    }

    // Upgrading/downgrading/legacy

    pub(super) fn add_default_deck(&self, tr: &I18n) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.id.0 = 1;
        // fixme: separate key
        deck.name = NativeDeckName::from_native_str(tr.deck_config_default_name());
        self.add_or_update_deck_with_existing_id(&deck)
    }

    pub(crate) fn upgrade_decks_to_schema15(&self, server: bool) -> Result<()> {
        let usn = self.usn(server)?;
        let decks = self
            .get_schema11_decks()
            .map_err(|e| AnkiError::JsonError(format!("decoding decks: {}", e)))?;
        let mut names = HashSet::new();
        for (_id, deck) in decks {
            let oldname = deck.name().to_string();
            let mut deck = Deck::from(deck);
            if deck.human_name() != oldname {
                deck.set_modified(usn);
            }
            loop {
                let name = UniCase::new(deck.name.as_native_str().to_string());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                deck.name.add_suffix("_");
                deck.set_modified(usn);
            }
            self.add_or_update_deck_with_existing_id(&deck)?;
        }
        self.db.execute("update col set decks = ''", [])?;
        Ok(())
    }

    pub(crate) fn downgrade_decks_from_schema15(&self) -> Result<()> {
        let decks = self.get_all_decks_as_schema11()?;
        self.set_schema11_decks(decks)
    }

    fn get_schema11_decks(&self) -> Result<HashMap<DeckId, DeckSchema11>> {
        let mut stmt = self.db.prepare("select decks from col")?;
        let decks = stmt
            .query_and_then([], |row| -> Result<HashMap<DeckId, DeckSchema11>> {
                let v: HashMap<DeckId, DeckSchema11> =
                    serde_json::from_str(row.get_ref_unwrap(0).as_str()?)?;
                Ok(v)
            })?
            .next()
            .ok_or_else(|| AnkiError::db_error("col table empty", DbErrorKind::MissingEntity))??;
        Ok(decks)
    }

    pub(crate) fn set_schema11_decks(&self, decks: HashMap<DeckId, DeckSchema11>) -> Result<()> {
        let json = serde_json::to_string(&decks)?;
        self.db.execute("update col set decks = ?", [json])?;
        Ok(())
    }
}
