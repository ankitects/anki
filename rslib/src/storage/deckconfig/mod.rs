// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use prost::Message;
use rusqlite::params;
use rusqlite::Row;
use serde_json::Value;

use super::SqliteStorage;
use crate::deckconfig::ensure_deck_config_values_valid;
use crate::deckconfig::DeckConfSchema11;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::deckconfig::DeckConfigInner;
use crate::prelude::*;

fn row_to_deckconf(row: &Row, fix_invalid: bool) -> Result<DeckConfig> {
    let mut config = DeckConfigInner::decode(row.get_ref_unwrap(4).as_blob()?)?;
    if fix_invalid {
        ensure_deck_config_values_valid(&mut config);
    }
    Ok(DeckConfig {
        id: row.get(0)?,
        name: row.get(1)?,
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        inner: config,
    })
}

impl SqliteStorage {
    pub(crate) fn all_deck_config(&self) -> Result<Vec<DeckConfig>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_deckconf(row, true))?
            .collect()
    }

    /// Does not cap values to those expected by the latest schema.
    pub(crate) fn all_deck_config_for_schema16_upgrade(&self) -> Result<Vec<DeckConfig>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_deckconf(row, false))?
            .collect()
    }

    pub(crate) fn get_deck_config_map(&self) -> Result<HashMap<DeckConfigId, DeckConfig>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_deckconf(row, true))?
            .map(|res| res.map(|d| (d.id, d)))
            .collect()
    }

    pub(crate) fn get_deck_config(&self, dcid: DeckConfigId) -> Result<Option<DeckConfig>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id = ?"))?
            .query_and_then(params![dcid], |row| row_to_deckconf(row, true))?
            .next()
            .transpose()
    }

    pub(crate) fn get_deck_config_id_by_name(&self, name: &str) -> Result<Option<DeckConfigId>> {
        self.db
            .prepare_cached("select id from deck_config WHERE name = ?")?
            .query_and_then([name], |row| Ok::<_, AnkiError>(DeckConfigId(row.get(0)?)))?
            .next()
            .transpose()
    }

    pub(crate) fn add_deck_conf(&self, conf: &mut DeckConfig) -> Result<()> {
        let mut conf_bytes = vec![];
        conf.inner.encode(&mut conf_bytes)?;
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                conf.id,
                conf.name,
                conf.mtime_secs,
                conf.usn,
                conf_bytes,
            ])?;
        let id = self.db.last_insert_rowid();
        if conf.id.0 != id {
            conf.id.0 = id;
        }
        Ok(())
    }

    pub(crate) fn add_deck_conf_if_unique(&self, conf: &DeckConfig) -> Result<bool> {
        let mut conf_bytes = vec![];
        conf.inner.encode(&mut conf_bytes)?;
        self.db
            .prepare_cached(include_str!("add_if_unique.sql"))?
            .execute(params![
                conf.id,
                conf.name,
                conf.mtime_secs,
                conf.usn,
                conf_bytes,
            ])
            .map(|added| added == 1)
            .map_err(Into::into)
    }

    pub(crate) fn update_deck_conf(&self, conf: &DeckConfig) -> Result<()> {
        let mut conf_bytes = vec![];
        conf.inner.encode(&mut conf_bytes)?;
        self.db
            .prepare_cached(include_str!("update.sql"))?
            .execute(params![
                conf.name,
                conf.mtime_secs,
                conf.usn,
                conf_bytes,
                conf.id,
            ])?;
        Ok(())
    }

    /// Used for syncing&undo; will keep provided ID. Shouldn't be used to add
    /// new config normally, since it does not allocate an id.
    pub(crate) fn add_or_update_deck_config_with_existing_id(
        &self,
        conf: &DeckConfig,
    ) -> Result<()> {
        require!(conf.id.0 != 0, "deck with id 0");
        let mut conf_bytes = vec![];
        conf.inner.encode(&mut conf_bytes)?;
        self.db
            .prepare_cached(include_str!("add_or_update.sql"))?
            .execute(params![
                conf.id,
                conf.name,
                conf.mtime_secs,
                conf.usn,
                conf_bytes,
            ])?;
        Ok(())
    }

    pub(crate) fn remove_deck_conf(&self, dcid: DeckConfigId) -> Result<()> {
        self.db
            .prepare_cached("delete from deck_config where id=?")?
            .execute(params![dcid])?;
        Ok(())
    }

    pub(crate) fn clear_deck_conf_usns(&self) -> Result<()> {
        self.db
            .prepare("update deck_config set usn = 0 where usn != 0")?
            .execute([])?;
        Ok(())
    }

    // Creating/upgrading/downgrading

    pub(super) fn add_default_deck_config(&self, tr: &I18n) -> Result<()> {
        let mut conf = DeckConfig::default();
        conf.id.0 = 1;
        conf.name = tr.deck_config_default_name().into();
        self.add_deck_conf(&mut conf)
    }

    // schema 11->14

    fn add_deck_conf_schema14(&self, conf: &mut DeckConfSchema11) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                conf.id,
                conf.name,
                conf.mtime,
                conf.usn,
                &serde_json::to_vec(conf)?,
            ])?;
        let id = self.db.last_insert_rowid();
        if conf.id.0 != id {
            conf.id.0 = id;
        }
        Ok(())
    }

    pub(super) fn upgrade_deck_conf_to_schema14(&self) -> Result<()> {
        let conf: HashMap<DeckConfigId, DeckConfSchema11> =
            self.db
                .query_row_and_then("select dconf from col", [], |row| -> Result<_> {
                    let text = row.get_ref_unwrap(0).as_str()?;
                    // try direct parse
                    serde_json::from_str(text)
                        .or_else(|_| {
                            // failed, and could be caused by duplicate keys. Serialize into
                            // a value first to discard them, then try again
                            let conf: Value = serde_json::from_str(text)?;
                            serde_json::from_value(conf)
                        })
                        .map_err(|e| AnkiError::JsonError {
                            info: format!("decoding deck config: {}", e),
                        })
                })?;
        for (id, mut conf) in conf.into_iter() {
            // buggy clients may have failed to set inner id to match hash key
            conf.id = id;
            self.add_deck_conf_schema14(&mut conf)?;
        }
        self.db.execute_batch("update col set dconf=''")?;

        Ok(())
    }

    // schema 14->15

    fn all_deck_config_schema14(&self) -> Result<Vec<DeckConfSchema11>> {
        self.db
            .prepare_cached("select config from deck_config")?
            .query_and_then([], |row| -> Result<_> {
                Ok(serde_json::from_slice(row.get_ref_unwrap(0).as_blob()?)?)
            })?
            .collect()
    }

    pub(super) fn upgrade_deck_conf_to_schema15(&self) -> Result<()> {
        for conf in self.all_deck_config_schema14()? {
            let mut conf: DeckConfig = conf.into();
            // schema 15 stored starting ease of 2.5 as 250
            conf.inner.initial_ease *= 100.0;
            self.update_deck_conf(&conf)?;
        }

        Ok(())
    }

    // schema 15->16

    pub(super) fn upgrade_deck_conf_to_schema16(&self, server: bool) -> Result<()> {
        let mut invalid_configs = vec![];
        for mut conf in self.all_deck_config_for_schema16_upgrade()? {
            // schema 16 changed starting ease of 250 to 2.5
            conf.inner.initial_ease /= 100.0;
            // new deck configs created with schema 15 had the wrong
            // ease set - reset any deck configs at the minimum ease
            // to the default 250%
            if conf.inner.initial_ease <= 1.3 {
                conf.inner.initial_ease = 2.5;
                invalid_configs.push(conf.id);
            }
            self.update_deck_conf(&conf)?;
        }

        self.fix_low_card_eases_for_configs(&invalid_configs, server)
    }

    // schema 15->11

    pub(super) fn downgrade_deck_conf_from_schema16(&self) -> Result<()> {
        let allconf = self.all_deck_config()?;
        let confmap: HashMap<DeckConfigId, DeckConfSchema11> = allconf
            .into_iter()
            .map(|c| -> DeckConfSchema11 { c.into() })
            .map(|c| (c.id, c))
            .collect();
        self.db.execute(
            "update col set dconf=?",
            params![serde_json::to_string(&confmap)?],
        )?;
        Ok(())
    }
}
