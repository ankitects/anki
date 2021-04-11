// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::ConfigKey;
use crate::prelude::*;

use strum::IntoStaticStr;

/// Notetype config packed into a collection config key. This may change
/// frequently, and we want to avoid the potentially expensive notetype
/// write/sync.
#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
enum NotetypeConfigKey {
    #[strum(to_string = "lastDeck")]
    LastDeckAddedTo,
}

impl NotetypeConfigKey {
    fn for_notetype(self, ntid: NotetypeId) -> String {
        build_aux_notetype_key(ntid, <&'static str>::from(self))
    }
}

impl Collection {
    #[allow(dead_code)]
    pub(crate) fn get_current_notetype_id(&self) -> Option<NotetypeId> {
        self.get_config_optional(ConfigKey::CurrentNotetypeId)
    }

    pub(crate) fn set_current_notetype_id(&mut self, ntid: NotetypeId) -> Result<()> {
        self.set_config(ConfigKey::CurrentNotetypeId, &ntid)
            .map(|_| ())
    }

    pub(crate) fn clear_aux_config_for_notetype(&self, ntid: NotetypeId) -> Result<()> {
        self.remove_config_prefix(&build_aux_notetype_key(ntid, ""))
    }

    pub(crate) fn get_last_deck_added_to_for_notetype(&self, id: NotetypeId) -> Option<DeckId> {
        let key = NotetypeConfigKey::LastDeckAddedTo.for_notetype(id);
        self.get_config_optional(key.as_str())
    }

    pub(crate) fn set_last_deck_for_notetype(&mut self, id: NotetypeId, did: DeckId) -> Result<()> {
        let key = NotetypeConfigKey::LastDeckAddedTo.for_notetype(id);
        self.set_config(key.as_str(), &did).map(|_| ())
    }
}

fn build_aux_notetype_key(ntid: NotetypeId, key: &str) -> String {
    format!("_nt_{ntid}_{key}", ntid = ntid, key = key)
}
