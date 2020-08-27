// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    card::CardID,
    card::CardQueue,
    config::SchedulerVersion,
    decks::immediate_parent_name,
    decks::{Deck, DeckCommon, DeckID, DeckKindProto, DeckSchema11, DueCounts},
    err::{AnkiError, DBErrorKind, Result},
    i18n::{I18n, TR},
    timestamp::TimestampMillis,
};
use prost::Message;
use rusqlite::{named_params, params, Row, NO_PARAMS};
use std::collections::{HashMap, HashSet};
use unicase::UniCase;

fn row_to_deck(row: &Row) -> Result<Deck> {
    let common = DeckCommon::decode(row.get_raw(4).as_blob()?)?;
    let kind = DeckKindProto::decode(row.get_raw(5).as_blob()?)?;
    let id = row.get(0)?;
    Ok(Deck {
        id,
        name: row.get(1)?,
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        common,
        kind: kind.kind.ok_or_else(|| AnkiError::DBError {
            kind: DBErrorKind::MissingEntity,
            info: format!("invalid deck kind: {}", id),
        })?,
    })
}

fn row_to_due_counts(row: &Row) -> Result<(DeckID, DueCounts)> {
    Ok((
        row.get(0)?,
        DueCounts {
            new: row.get(1)?,
            review: row.get(2)?,
            learning: row.get(3)?,
        },
    ))
}

impl SqliteStorage {
    pub(crate) fn get_all_decks_as_schema11(&self) -> Result<HashMap<DeckID, DeckSchema11>> {
        self.get_all_decks()
            .map(|r| r.into_iter().map(|d| (d.id, d.into())).collect())
    }

    pub(crate) fn get_deck(&self, did: DeckID) -> Result<Option<Deck>> {
        self.db
            .prepare_cached(concat!(include_str!("get_deck.sql"), " where id = ?"))?
            .query_and_then(&[did], row_to_deck)?
            .next()
            .transpose()
    }

    pub(crate) fn get_all_decks(&self) -> Result<Vec<Deck>> {
        self.db
            .prepare(include_str!("get_deck.sql"))?
            .query_and_then(NO_PARAMS, row_to_deck)?
            .collect()
    }

    /// Get all deck names in sorted, human-readable form (::)
    pub(crate) fn get_all_deck_names(&self) -> Result<Vec<(DeckID, String)>> {
        self.db
            .prepare("select id, name from decks order by name")?
            .query_and_then(NO_PARAMS, |row| {
                Ok((row.get(0)?, row.get_raw(1).as_str()?.replace('\x1f', "::")))
            })?
            .collect()
    }

    pub(crate) fn get_deck_id(&self, machine_name: &str) -> Result<Option<DeckID>> {
        self.db
            .prepare("select id from decks where name = ?")?
            .query_and_then(&[machine_name], |row| row.get(0))?
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
            .query_row(&[TimestampMillis::now()], |r| r.get(0))?;
        self.update_deck(deck).map_err(|err| {
            // restore id of 0
            deck.id.0 = 0;
            err
        })
    }

    pub(crate) fn update_deck(&self, deck: &Deck) -> Result<()> {
        if deck.id.0 == 0 {
            return Err(AnkiError::invalid_input("deck with id 0"));
        }
        self.add_or_update_deck(deck)
    }

    /// Used for syncing; will keep existing ID.
    pub(crate) fn add_or_update_deck(&self, deck: &Deck) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("update_deck.sql"))?;
        let mut common = vec![];
        deck.common.encode(&mut common)?;
        let kind_enum = DeckKindProto {
            kind: Some(deck.kind.clone()),
        };
        let mut kind = vec![];
        kind_enum.encode(&mut kind)?;
        stmt.execute(params![
            deck.id,
            deck.name,
            deck.mtime_secs,
            deck.usn,
            common,
            kind
        ])?;

        Ok(())
    }

    pub(crate) fn remove_deck(&self, did: DeckID) -> Result<()> {
        self.db
            .prepare_cached("delete from decks where id = ?")?
            .execute(&[did])?;
        Ok(())
    }

    pub(crate) fn all_cards_in_single_deck(&self, did: DeckID) -> Result<Vec<CardID>> {
        self.db
            .prepare_cached(include_str!("cards_for_deck.sql"))?
            .query_and_then(&[did], |r| r.get(0).map_err(Into::into))?
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
            .query_and_then(&[prefix_start, prefix_end], row_to_deck)?
            .collect()
    }

    pub(crate) fn parent_decks(&self, child: &Deck) -> Result<Vec<Deck>> {
        let mut decks: Vec<Deck> = vec![];
        while let Some(parent_name) =
            immediate_parent_name(decks.last().map(|d| &d.name).unwrap_or_else(|| &child.name))
        {
            if let Some(parent_did) = self.get_deck_id(parent_name)? {
                let parent = self.get_deck(parent_did)?.unwrap();
                decks.push(parent);
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
    ) -> Result<HashMap<DeckID, DueCounts>> {
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
            .query_and_then_named(&params, row_to_due_counts)?
            .collect()
    }

    /// Decks referenced by cards but missing.
    pub(crate) fn missing_decks(&self) -> Result<Vec<DeckID>> {
        self.db
            .prepare(include_str!("missing-decks.sql"))?
            .query_and_then(NO_PARAMS, |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn deck_is_empty(&self, did: DeckID) -> Result<bool> {
        self.db
            .prepare_cached("select null from cards where did=?")?
            .query(&[did])?
            .next()
            .map(|o| o.is_none())
            .map_err(Into::into)
    }

    pub(crate) fn clear_deck_usns(&self) -> Result<()> {
        self.db
            .prepare("update decks set usn = 0 where usn != 0")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    /// Write active decks into temporary active_decks table.
    pub(crate) fn update_active_decks(&self, current: &Deck) -> Result<()> {
        self.db.execute_batch(concat!(
            "drop table if exists temp.active_decks;",
            "create temporary table active_decks (id integer primary key not null);"
        ))?;

        let top = &current.name;
        let prefix_start = &format!("{}\x1f", top);
        let prefix_end = &format!("{}\x20", top);

        self.db
            .prepare_cached(include_str!("update_active.sql"))?
            .execute(&[top, prefix_start, prefix_end])?;

        Ok(())
    }

    // Upgrading/downgrading/legacy

    pub(super) fn add_default_deck(&self, i18n: &I18n) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.id.0 = 1;
        // fixme: separate key
        deck.name = i18n.tr(TR::DeckConfigDefaultName).into();
        self.update_deck(&deck)
    }

    pub(crate) fn upgrade_decks_to_schema15(&self, server: bool) -> Result<()> {
        let usn = self.usn(server)?;
        let decks = self.get_schema11_decks()?;
        let mut names = HashSet::new();
        for (_id, deck) in decks {
            let oldname = deck.name().to_string();
            let mut deck = Deck::from(deck);
            if deck.human_name() != oldname {
                deck.set_modified(usn);
            }
            loop {
                let name = UniCase::new(deck.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                deck.name.push('_');
                deck.set_modified(usn);
            }
            self.update_deck(&deck)?;
        }
        self.db.execute("update col set decks = ''", NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn downgrade_decks_from_schema15(&self) -> Result<()> {
        let decks = self.get_all_decks_as_schema11()?;
        self.set_schema11_decks(decks)
    }

    fn get_schema11_decks(&self) -> Result<HashMap<DeckID, DeckSchema11>> {
        let mut stmt = self.db.prepare("select decks from col")?;
        let decks = stmt
            .query_and_then(NO_PARAMS, |row| -> Result<HashMap<DeckID, DeckSchema11>> {
                let v: HashMap<DeckID, DeckSchema11> =
                    serde_json::from_str(row.get_raw(0).as_str()?)?;
                Ok(v)
            })?
            .next()
            .ok_or_else(|| AnkiError::DBError {
                info: "col table empty".to_string(),
                kind: DBErrorKind::MissingEntity,
            })??;
        Ok(decks)
    }

    pub(crate) fn set_schema11_decks(&self, decks: HashMap<DeckID, DeckSchema11>) -> Result<()> {
        let json = serde_json::to_string(&decks)?;
        self.db.execute("update col set decks = ?", &[json])?;
        Ok(())
    }
}
