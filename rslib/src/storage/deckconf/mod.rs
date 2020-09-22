// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    deckconf::{DeckConf, DeckConfID, DeckConfSchema11, DeckConfigInner},
    err::Result,
    i18n::{I18n, TR},
};
use prost::Message;
use rusqlite::{params, Row, NO_PARAMS};
use std::collections::HashMap;

fn row_to_deckconf(row: &Row) -> Result<DeckConf> {
    let config = DeckConfigInner::decode(row.get_raw(4).as_blob()?)?;
    Ok(DeckConf {
        id: row.get(0)?,
        name: row.get(1)?,
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        inner: config,
    })
}

impl SqliteStorage {
    pub(crate) fn all_deck_config(&self) -> Result<Vec<DeckConf>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then(NO_PARAMS, row_to_deckconf)?
            .collect()
    }

    pub(crate) fn get_deck_config(&self, dcid: DeckConfID) -> Result<Option<DeckConf>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id = ?"))?
            .query_and_then(params![dcid], row_to_deckconf)?
            .next()
            .transpose()
    }

    pub(crate) fn add_deck_conf(&self, conf: &mut DeckConf) -> Result<()> {
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

    pub(crate) fn update_deck_conf(&self, conf: &DeckConf) -> Result<()> {
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

    /// Used for syncing.
    pub(crate) fn add_or_update_deck_config(&self, conf: &DeckConf) -> Result<()> {
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

    pub(crate) fn remove_deck_conf(&self, dcid: DeckConfID) -> Result<()> {
        self.db
            .prepare_cached("delete from deck_config where id=?")?
            .execute(params![dcid])?;
        Ok(())
    }

    pub(crate) fn clear_deck_conf_usns(&self) -> Result<()> {
        self.db
            .prepare("update deck_config set usn = 0 where usn != 0")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    // Creating/upgrading/downgrading

    pub(super) fn add_default_deck_config(&self, i18n: &I18n) -> Result<()> {
        let mut conf = DeckConf::default();
        conf.id.0 = 1;
        conf.name = i18n.tr(TR::DeckConfigDefaultName).into();
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
        let conf = self
            .db
            .query_row_and_then("select dconf from col", NO_PARAMS, |row| {
                let conf: Result<HashMap<DeckConfID, DeckConfSchema11>> =
                    serde_json::from_str(row.get_raw(0).as_str()?).map_err(Into::into);
                conf
            })?;
        for (_, mut conf) in conf.into_iter() {
            self.add_deck_conf_schema14(&mut conf)?;
        }
        self.db.execute_batch("update col set dconf=''")?;

        Ok(())
    }

    // schema 14->15

    fn all_deck_config_schema14(&self) -> Result<Vec<DeckConfSchema11>> {
        self.db
            .prepare_cached("select config from deck_config")?
            .query_and_then(NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_slice(row.get_raw(0).as_blob()?)?)
            })?
            .collect()
    }

    pub(super) fn upgrade_deck_conf_to_schema15(&self) -> Result<()> {
        for conf in self.all_deck_config_schema14()? {
            let mut conf: DeckConf = conf.into();
            // schema 15 stored starting ease of 2.5 as 250
            conf.inner.initial_ease *= 100.0;
            self.update_deck_conf(&conf)?;
        }

        Ok(())
    }

    // schema 15->16

    pub(super) fn upgrade_deck_conf_to_schema16(&self, server: bool) -> Result<()> {
        let mut invalid_configs = vec![];
        for mut conf in self.all_deck_config()? {
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
        let confmap: HashMap<DeckConfID, DeckConfSchema11> = allconf
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
