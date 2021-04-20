// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::editor_service::Service as EditorService;
use crate::{backend_proto as pb, prelude::*};

impl EditorService for Backend {
    fn editor_data(&self, input: pb::EditorIn) -> Result<pb::EditorOut> {
        self.with_col(|col| col.editor_data(input))
    }

    fn get_editor_preferences(&self, _input: pb::Empty) -> Result<pb::EditorPreferences> {
        self.with_col(|col| Ok(col.get_editor_preferences()))
    }

    fn set_editor_preferences(&self, input: pb::EditorPreferences) -> Result<pb::Empty> {
        self.with_col(|col| col.set_editor_preferences(input))
            .map(Into::into)
    }
}
