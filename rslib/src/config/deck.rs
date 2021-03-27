// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::ConfigKey;
use crate::prelude::*;

use strum::IntoStaticStr;

/// Auxillary deck state, stored in the config table.
#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
enum DeckConfigKey {
    LastNotetype,
}

impl DeckConfigKey {
    fn for_deck(self, did: DeckId) -> String {
        build_aux_deck_key(did, <&'static str>::from(self))
    }
}

impl Collection {
    pub(crate) fn get_current_deck_id(&self) -> DeckId {
        self.get_config_optional(ConfigKey::CurrentDeckId)
            .unwrap_or(DeckId(1))
    }

    pub(crate) fn clear_aux_config_for_deck(&self, ntid: DeckId) -> Result<()> {
        self.remove_config_prefix(&build_aux_deck_key(ntid, ""))
    }

    pub(crate) fn get_last_notetype_for_deck(&self, id: DeckId) -> Option<NoteTypeId> {
        let key = DeckConfigKey::LastNotetype.for_deck(id);
        self.get_config_optional(key.as_str())
    }

    pub(crate) fn set_last_notetype_for_deck(
        &mut self,
        did: DeckId,
        ntid: NoteTypeId,
    ) -> Result<()> {
        let key = DeckConfigKey::LastNotetype.for_deck(did);
        self.set_config(key.as_str(), &ntid)
    }
}

fn build_aux_deck_key(deck: DeckId, key: &str) -> String {
    format!("_deck_{deck}_{key}", deck = deck, key = key)
}
