// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    deckconf::{DeckConf, DeckConfID},
    err::Result,
    i18n::{I18n, TR},
};
use rusqlite::{params, NO_PARAMS};
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn all_deck_config(&self) -> Result<Vec<DeckConf>> {
        self.db
            .prepare_cached("select config from deck_config")?
            .query_and_then(NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })?
            .collect()
    }

    pub(crate) fn get_deck_config(&self, dcid: DeckConfID) -> Result<Option<DeckConf>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then(params![dcid], |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })?
            .next()
            .transpose()
    }

    pub(crate) fn add_deck_conf(&self, conf: &mut DeckConf) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                conf.id,
                conf.name,
                conf.mtime,
                conf.usn,
                &serde_json::to_string(conf)?,
            ])?;
        let id = self.db.last_insert_rowid();
        if conf.id.0 != id {
            // if the initial ID conflicted, make sure the json is up to date
            // as well
            conf.id.0 = id;
            self.update_deck_conf(conf)?;
        }
        Ok(())
    }

    pub(crate) fn update_deck_conf(&self, conf: &DeckConf) -> Result<()> {
        self.db
            .prepare_cached(include_str!("update.sql"))?
            .execute(params![
                conf.name,
                conf.mtime,
                conf.usn,
                &serde_json::to_string(conf)?,
                conf.id,
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
        for mut conf in self.all_deck_config()? {
            if conf.usn.0 != 0 {
                conf.usn.0 = 0;
                self.update_deck_conf(&conf)?;
            }
        }

        Ok(())
    }

    // Creating/upgrading/downgrading

    pub(super) fn add_default_deck_config(&self, i18n: &I18n) -> Result<()> {
        let mut conf = DeckConf::default();
        conf.id.0 = 1;
        conf.name = i18n.tr(TR::DeckConfigDefaultName).into();
        self.add_deck_conf(&mut conf)
    }

    pub(super) fn upgrade_deck_conf_to_schema12(&self) -> Result<()> {
        let conf = self
            .db
            .query_row_and_then("select dconf from col", NO_PARAMS, |row| {
                let conf: Result<HashMap<DeckConfID, DeckConf>> =
                    serde_json::from_str(row.get_raw(0).as_str()?).map_err(Into::into);
                conf
            })?;
        for (_, mut conf) in conf.into_iter() {
            self.add_deck_conf(&mut conf)?;
        }
        self.db.execute_batch("update col set dconf=''")?;

        Ok(())
    }

    pub(super) fn downgrade_deck_conf_from_schema12(&self) -> Result<()> {
        let allconf = self.all_deck_config()?;
        let confmap: HashMap<DeckConfID, DeckConf> =
            allconf.into_iter().map(|c| (c.id, c)).collect();
        self.db.execute(
            "update col set dconf=?",
            params![serde_json::to_string(&confmap)?],
        )?;
        Ok(())
    }
}
