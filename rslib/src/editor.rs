// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, prelude::*};

impl Collection {
    pub(crate) fn editor_data(&mut self, _input: pb::EditorIn) -> Result<pb::EditorOut> {
        Ok(pb::EditorOut {})
    }

    pub(crate) fn get_editor_preferences(&self) -> pb::EditorPreferences {
        pb::EditorPreferences {}
    }

    pub(crate) fn set_editor_preferences(&mut self, _prefs: pb::EditorPreferences) -> Result<()> {
        Ok(())
    }
}
