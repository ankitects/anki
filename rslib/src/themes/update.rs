// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updating themes in bulk, from the preferences screen.

use crate::pb;
use crate::pb::themes::Theme;
use crate::pb::themes::ThemeId;
use crate::prelude::*;


#[derive(Debug, Clone)]
pub struct UpdateThemesRequest {
    pub themes: Vec<Theme>,
    pub removed_theme_ids: Vec<ThemeId>,
}

impl Collection {
    /// Information required for the preferences screen.
    pub fn get_themes_for_update(
        &mut self,
    ) -> Result<pb::themes::ThemesForUpdate> {
        Ok(pb::themes::ThemesForUpdate {
            all_themes: self.get_theme_for_update()?,
            defaults: Some(Theme::default().into()),
            schema_modified: self
                .storage
                .get_collection_timestamps()?
                .schema_changed_since_sync(),
        })
    }

    pub fn update_themes(&mut self, input: UpdateThemesRequest) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateTheme, |col| {
            col.update_themes_inner(input)
        })
    }
}

impl Collection {
    fn get_theme_for_update(&self) -> Result<Vec<Theme>> {
        // grab the theme and sort it
        let mut theme = self.storage.all_theme()?;
        theme.sort_unstable_by(|a, b| a.name.cmp(&b.name));

        Ok(theme.into_iter().map(|theme| theme.into()).collect())
    }

    fn update_themes_inner(&mut self, mut input: UpdateThemesRequest) -> Result<()> {
        require!(!input.themes.is_empty(), "theme not provided");
        let themes_before_update = self.storage.get_theme_map()?;
        let mut themes_after_update = themes_before_update.clone();

        // handle removals first
        for tid in &input.removed_theme_ids {
            self.remove_theme_inner(*tid)?;
            themes_after_update.remove(tid);
        }

        // add/update provided themes
        for theme in &mut input.themes {
            self.add_or_update_theme(theme)?;
            themes_after_update.insert(theme.id, theme.clone());
        }
        Ok(())
    }

}
