// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use rusqlite::params;
use serde::de::DeserializeOwned;
use serde_json::Value;

use super::SqliteStorage;
use crate::config::ConfigEntry;
use crate::error::Result;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

impl SqliteStorage {
    pub(crate) fn set_config_entry(&self, entry: &ConfigEntry) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![&entry.key, entry.usn, entry.mtime, &entry.value])?;
        Ok(())
    }

    pub(crate) fn remove_config(&self, key: &str) -> Result<()> {
        self.db
            .prepare_cached("delete from config where key=?")?
            .execute([key])?;
        Ok(())
    }

    pub(crate) fn get_config_value<T: DeserializeOwned>(&self, key: &str) -> Result<Option<T>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([key], |row| {
                let blob = row.get_ref_unwrap(0).as_blob()?;
                serde_json::from_slice(blob).map_err(Into::into)
            })?
            .next()
            .transpose()
    }

    /// Return the raw bytes and other metadata, for undoing.
    pub(crate) fn get_config_entry(&self, key: &str) -> Result<Option<Box<ConfigEntry>>> {
        self.db
            .prepare_cached(include_str!("get_entry.sql"))?
            .query_and_then([key], |row| {
                Ok(ConfigEntry::boxed(
                    key,
                    row.get(0)?,
                    row.get(1)?,
                    row.get(2)?,
                ))
            })?
            .next()
            .transpose()
    }

    /// Prefix is expected to end with '_'.
    pub(crate) fn get_config_prefix(&self, prefix: &str) -> Result<Vec<(String, Vec<u8>)>> {
        let mut end = prefix.to_string();
        assert_eq!(end.pop(), Some('_'));
        end.push(std::char::from_u32('_' as u32 + 1).unwrap());
        self.db
            .prepare("select key, val from config where key > ? and key < ?")?
            .query_and_then(params![prefix, &end], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect()
    }

    pub(crate) fn get_all_config(&self) -> Result<HashMap<String, Value>> {
        self.db
            .prepare("select key, val from config")?
            .query_and_then([], |row| {
                let val: Value = serde_json::from_slice(row.get_ref_unwrap(1).as_blob()?)?;
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
        self.db.execute("delete from config", [])?;
        for (key, val) in conf.iter() {
            self.set_config_entry(&ConfigEntry::boxed(
                key,
                serde_json::to_vec(&val)?,
                usn,
                mtime,
            ))?;
        }
        Ok(())
    }

    pub(crate) fn clear_config_usns(&self) -> Result<()> {
        self.db
            .prepare("update config set usn = 0 where usn != 0")?
            .execute([])?;
        Ok(())
    }

    // Upgrading/downgrading

    pub(super) fn upgrade_config_to_schema14(&self) -> Result<()> {
        let conf = self
            .db
            .query_row_and_then("select conf from col", [], |row| {
                let conf: Result<HashMap<String, Value>> =
                    serde_json::from_str(row.get_ref_unwrap(0).as_str()?).map_err(Into::into);
                conf
            })?;
        self.set_all_config(conf, Usn(0), TimestampSecs(0))?;
        self.db.execute_batch("update col set conf=''")?;

        Ok(())
    }

    pub(super) fn downgrade_config_from_schema14(&self) -> Result<()> {
        let allconf = self.get_all_config()?;
        self.db
            .execute("update col set conf=?", [serde_json::to_string(&allconf)?])?;
        Ok(())
    }
}
