// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use rusqlite::params;
use rusqlite::Row;

use super::SqliteStorage;
use crate::themes::Theme;
use crate::themes::ThemeId;
use crate::themes::Vars;
use crate::prelude::*;

fn row_to_theme(row: &Row, fix_invalid: bool) -> Result<Theme> {
    let mut variables = Vars::decode(row.get_ref_unwrap(4).as_blob()?)?;
    if fix_invalid {
        variables.ensure_values_valid();
    }
    Ok(Theme {
        id: row.get(0)?,
        name: row.get(1)?,
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        vars: variables,
    })
}

impl SqliteStorage {
    pub(crate) fn all_theme(&self) -> Result<Vec<Theme>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_theme(row, true))?
            .collect()
    }

    /// Does not cap values to those expected by the latest schema.
    pub(crate) fn all_theme_for_schema16_upgrade(&self) -> Result<Vec<Theme>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_theme(row, false))?
            .collect()
    }

    pub(crate) fn get_theme_map(&self) -> Result<HashMap<ThemeId, Theme>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], |row| row_to_theme(row, true))?
            .map(|res| res.map(|d| (d.id, d)))
            .collect()
    }

    pub(crate) fn get_theme(&self, tid: ThemeId) -> Result<Option<Theme>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id = ?"))?
            .query_and_then(params![tid], |row| row_to_theme(row, true))?
            .next()
            .transpose()
    }

    pub(crate) fn add_theme(&self, theme: &mut Theme) -> Result<()> {
        let mut theme_bytes = vec![];
        theme.vars.encode(&mut theme_bytes)?;
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                theme.id,
                theme.name,
                theme.mtime_secs,
                theme.usn,
                theme_bytes,
            ])?;
        let id = self.db.last_insert_rowid();
        if theme.id.0 != id {
            theme.id.0 = id;
        }
        Ok(())
    }

    pub(crate) fn add_theme_if_unique(&self, theme: &Theme) -> Result<bool> {
        let mut theme_bytes = vec![];
        theme.vars.encode(&mut theme_bytes)?;
        self.db
            .prepare_cached(include_str!("add_if_unique.sql"))?
            .execute(params![
                theme.id,
                theme.name,
                theme.mtime_secs,
                theme.usn,
                theme_bytes,
            ])
            .map(|added| added == 1)
            .map_err(Into::into)
    }

    pub(crate) fn update_theme(&self, theme: &Theme) -> Result<()> {
        let mut theme_bytes = vec![];
        theme.vars.encode(&mut theme_bytes)?;
        self.db
            .prepare_cached(include_str!("update.sql"))?
            .execute(params![
                theme.name,
                theme.mtime_secs,
                theme.usn,
                theme_bytes,
                theme.id,
            ])?;
        Ok(())
    }

    /// Used for syncing&undo; will keep provided ID. Shouldn't be used to add
    /// new theme normally, since it does not allocate an id.
    pub(crate) fn add_or_update_theme_with_existing_id(
        &self,
        theme: &Theme,
    ) -> Result<()> {
        require!(theme.id.0 != 0, "deck with id 0");
        let mut theme_bytes = vec![];
        theme.vars.encode(&mut theme_bytes)?;
        self.db
            .prepare_cached(include_str!("add_or_update.sql"))?
            .execute(params![
                theme.id,
                theme.name,
                theme.mtime_secs,
                theme.usn,
                theme_bytes,
            ])?;
        Ok(())
    }

    pub(crate) fn remove_theme(&self, tid: ThemeId) -> Result<()> {
        self.db
            .prepare_cached("delete from theme where id=?")?
            .execute(params![tid])?;
        Ok(())
    }

    pub(crate) fn clear_theme_usns(&self) -> Result<()> {
        self.db
            .prepare("update theme set usn = 0 where usn != 0")?
            .execute([])?;
        Ok(())
    }

    // Creating/upgrading/downgrading

    pub(super) fn add_default_theme(&self, tr: &I18n) -> Result<()> {
        let mut theme = Theme::default();
        theme.id.0 = 1;
        theme.name = tr.theme_default_name().into();
        self.add_theme(&mut theme)
    }
}
