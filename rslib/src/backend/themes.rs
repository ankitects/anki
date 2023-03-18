// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::pb;
use crate::pb::generic::Empty;
pub(super) use crate::pb::themes::themes_service::Service as ThemesService;
use crate::prelude::*;
use crate::themes::Theme;
use crate::themes::UpdateThemesRequest;

impl ThemesService for Backend {
    fn get_theme(&self, input: pb::themes::ThemeId) -> Result<pb::themes::Theme> {
        self.with_col(|col| Ok(col.get_theme(input.into(), true)?.unwrap().into()))
    }

    fn remove_theme(&self, input: pb::themes::ThemeId) -> Result<pb::generic::Empty> {
        self.with_col(|col| col.transact_no_undo(|col| col.remove_theme_vars(input.into())))
            .map(Into::into)
    }

    fn get_themes_for_update(&self, _input: Empty) -> Result<pb::themes::ThemesForUpdate> {
        self.with_col(|col| col.get_themes_for_update())
    }

    fn update_themes(
        &self,
        input: pb::themes::UpdateThemesRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.update_themes(input.into()))
            .map(Into::into)
    }
}

impl From<Theme> for pb::themes::Theme {
    fn from(c: Theme) -> Self {
        pb::themes::Theme {
            id: c.id.0,
            name: c.name,
            mtime_secs: c.mtime_secs.0,
            usn: c.usn.0,
            vars: Some(c.vars),
        }
    }
}

impl From<pb::themes::UpdateThemesRequest> for UpdateThemesRequest {
    fn from(c: pb::themes::UpdateThemesRequest) -> Self {
        UpdateThemesRequest {
            themes: c.themes.into_iter().map(Into::into).collect(),
            removed_theme_ids: c.removed_theme_ids.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<pb::themes::Theme> for Theme {
    fn from(c: pb::themes::Theme) -> Self {
        Theme {
            id: c.id.into(),
            name: c.name,
            mtime_secs: c.mtime_secs.into(),
            usn: c.usn.into(),
            vars: c.vars.unwrap_or_default(),
        }
    }
}
