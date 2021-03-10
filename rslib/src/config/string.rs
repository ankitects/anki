// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;
use strum::IntoStaticStr;

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum StringKey {
    SetDueBrowser,
    SetDueReviewer,
}

impl Collection {
    pub(crate) fn get_string(&self, key: StringKey) -> String {
        let default = match key {
            StringKey::SetDueBrowser => "0",
            StringKey::SetDueReviewer => "1",
            // other => "",
        };
        self.get_config_optional(key)
            .unwrap_or_else(|| default.to_string())
    }

    pub(crate) fn set_string(&mut self, key: StringKey, val: &str) -> Result<()> {
        self.set_config(key, &val)
    }
}
