// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use strum::IntoStaticStr;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum I32ConfigKey {
    CsvDuplicateResolution,
    MatchScope,
    LastFsrsOptimize,
}

impl Collection {
    pub fn get_config_i32(&self, key: I32ConfigKey) -> i32 {
        #[allow(clippy::match_single_binding)]
        self.get_config_optional(key).unwrap_or(match key {
            _other => 0,
        })
    }
}

impl Collection {
    pub(crate) fn set_config_i32_inner(&mut self, key: I32ConfigKey, value: i32) -> Result<bool> {
        self.set_config(key, &value)
    }
}
