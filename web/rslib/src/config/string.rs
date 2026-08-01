// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use strum::IntoStaticStr;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum StringKey {
    SetDueBrowser,
    SetDueReviewer,
    DefaultSearchText,
    CardStateCustomizer,
}

impl Collection {
    pub fn get_config_string(&self, key: StringKey) -> String {
        let default = match key {
            StringKey::SetDueBrowser => "0",
            StringKey::SetDueReviewer => "1",
            _other => "",
        };
        self.get_config_optional(key)
            .unwrap_or_else(|| default.to_string())
    }

    pub fn set_config_string(
        &mut self,
        key: StringKey,
        val: &str,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        let op = if undoable {
            Op::UpdateConfig
        } else {
            Op::SkipUndo
        };
        self.transact(op, |col| {
            col.set_config_string_inner(key, val)?;
            Ok(())
        })
    }
}

impl Collection {
    pub(crate) fn set_config_string_inner(&mut self, key: StringKey, val: &str) -> Result<bool> {
        self.set_config(key, &val)
    }
}
