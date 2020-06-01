// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{err::Result, timestamp::TimestampSecs, types::Usn};
use rusqlite::{params, NO_PARAMS};
use serde::{de::DeserializeOwned, Serialize};
use serde_json::Value;
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn set_config_value<T: Serialize>(
        &self,
        key: &str,
        val: &T,
        usn: Usn,
        mtime: TimestampSecs,
    ) -> Result<()> {
        let json = serde_json::to_vec(val)?;
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![key, usn, mtime, &json])?;
        Ok(())
    }

    pub(crate) fn remove_config(&self, key: &str) -> Result<()> {
        self.db
            .prepare_cached("delete from config where key=?")?
            .execute(&[key])?;
        Ok(())
    }

    pub(crate) fn get_config_value<T: DeserializeOwned>(&self, key: &str) -> Result<Option<T>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then(&[key], |row| {
                let blob = row.get_raw(0).as_blob()?;
                serde_json::from_slice(blob).map_err(Into::into)
            })?
            .next()
            .transpose()
    }

    pub(crate) fn get_all_config(&self) -> Result<HashMap<String, Value>> {
        self.db
            .prepare("select key, val from config")?
            .query_and_then(NO_PARAMS, |row| {
                let val: Value = serde_json::from_slice(row.get_raw(1).as_blob()?)?;
                Ok((row.get::<usize, String>(0)?, val))
            })?
            .collect()
    }

    pub(crate) fn set_all_config(
        &self,
        conf: HashMap<String, Value>,
        usn: Usn,
        mtime: TimestampSecs,
    ) -> Result<()> {
        self.db.execute("delete from config", NO_PARAMS)?;
        for (key, val) in conf.iter() {
            self.set_config_value(key, val, usn, mtime)?;
        }
        Ok(())
    }

    pub(crate) fn clear_config_usns(&self) -> Result<()> {
        self.db
            .prepare("update config set usn = 0 where usn != 0")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    // Upgrading/downgrading

    pub(super) fn upgrade_config_to_schema14(&self) -> Result<()> {
        let conf = self
            .db
            .query_row_and_then("select conf from col", NO_PARAMS, |row| {
                let conf: Result<HashMap<String, Value>> =
                    serde_json::from_str(row.get_raw(0).as_str()?).map_err(Into::into);
                conf
            })?;
        self.set_all_config(conf, Usn(0), TimestampSecs(0))?;
        self.db.execute_batch("update col set conf=''")?;

        Ok(())
    }

    pub(super) fn downgrade_config_from_schema14(&self) -> Result<()> {
        let allconf = self.get_all_config()?;
        self.db
            .execute("update col set conf=?", &[serde_json::to_string(&allconf)?])?;
        Ok(())
    }
}
