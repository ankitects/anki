// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod default;
mod update;

pub use default::DEFAULT_VARS;
pub use update::UpdateThemesRequest;

pub use crate::pb::themes::Vars;

use crate::define_newtype;
use crate::prelude::*;

define_newtype!(ThemeId, i64);

#[derive(Debug, PartialEq, Clone)]
pub struct Theme {
    pub id: ThemeId,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub vars: Vars,
}

impl Default for Theme {
    fn default() -> Self {
        Theme {
            id: ThemeId(0),
            name: "".to_string(),
            mtime_secs: Default::default(),
            usn: Default::default(),
            vars: DEFAULT_VARS,
        }
    }
}

impl Theme {
    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
    }
}

impl Collection {
    /// If fallback is true, guaranteed to return a theme.
    pub fn get_theme(&self, tid: ThemeId, fallback: bool) -> Result<Option<Theme>> {
        if let Some(theme) = self.storage.get_theme(tid)? {
            return Ok(Some(theme));
        }
        if fallback {
            if let Some(theme) = self.storage.get_theme(ThemeId(1))? {
                return Ok(Some(theme));
            }
            // if even the default theme is missing, just return the defaults
            Ok(Some(Theme::default()))
        } else {
            Ok(None)
        }
    }
}

impl Collection {
    pub(crate) fn add_or_update_theme(&mut self, theme: &mut Theme) -> Result<()> {
        let usn = Some(self.usn()?);

        if theme.id.0 == 0 {
            self.add_theme_vars(theme, usn)
        } else {
            let original = self
                .storage
                .get_theme(theme.id)?
                .or_not_found(theme.id)?;
            self.update_theme_vars(theme, original, usn)
        }
    }

    /// Assigns an id and adds to DB. If usn is provided, modification time and
    /// usn will be updated.
    pub(crate) fn add_theme_vars(&mut self, theme: &mut Theme, usn: Option<Usn>) -> Result<()> {
        if let Some(usn) = usn {
            theme.set_modified(usn);
        }
        theme.id.0 = TimestampMillis::now().0;
        self.storage.add_theme(theme)
    }

    /// Update an existing theme. If usn is provided, modification time
    /// and usn will be updated.
    pub(crate) fn update_theme_vars(
        &mut self,
        theme: &mut Theme,
        original: Theme,
        usn: Option<Usn>,
    ) -> Result<()> {
        if theme == &original {
            return Ok(());
        }
        if let Some(usn) = usn {
            theme.set_modified(usn);
        }
        self.storage.update_theme(theme)
    }

    /// Remove a theme
    pub(crate) fn remove_theme_vars(&mut self, tid: ThemeId) -> Result<()> {
        require!(tid.0 != 1, "can't delete default theme");
        let theme = self.storage.get_theme(tid)?.or_not_found(tid)?;
        self.set_schema_modified()?;
        self.storage.remove_theme(tid)
    }
}
