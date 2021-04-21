// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, prelude::*};
use std::convert::*;

impl Collection {
    pub(crate) fn editor_data(&mut self, _input: pb::EditorIn) -> Result<pb::EditorOut> {
        Ok(pb::EditorOut {})
    }

    pub(crate) fn get_editor_preferences(&self) -> pb::EditorPreferences {
        pb::EditorPreferences {
            favorite_colors: self.get_favorite_colors().to_vec(),
        }
    }

    pub(crate) fn set_editor_preferences(&mut self, prefs: pb::EditorPreferences) -> Result<()> {
        let colors = <[u32; 8]>::from(FavoriteColors(prefs.favorite_colors));
        self.set_favorite_colors(colors)?;
        Ok(())
    }
}

struct FavoriteColors(Vec<u32>);

impl From<FavoriteColors> for [u32; 8] {
    fn from(v: FavoriteColors) -> Self {
        v.0.try_into().unwrap_or_else(|_| [0xffffffff; 8])
    }
}
