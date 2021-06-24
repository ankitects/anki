// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::deckconfig_service::Service as DeckConfigService;
use crate::{
    backend_proto as pb,
    deckconfig::{DeckConfSchema11, DeckConfig, UpdateDeckConfigsRequest},
    prelude::*,
};

impl DeckConfigService for Backend {
    fn add_or_update_deck_config_legacy(&self, input: pb::Json) -> Result<pb::DeckConfigId> {
        let conf: DeckConfSchema11 = serde_json::from_slice(&input.json)?;
        let mut conf: DeckConfig = conf.into();
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                col.add_or_update_deck_config_legacy(&mut conf)?;
                Ok(pb::DeckConfigId { dcid: conf.id.0 })
            })
        })
        .map(Into::into)
    }

    fn all_deck_config_legacy(&self, _input: pb::Empty) -> Result<pb::Json> {
        self.with_col(|col| {
            let conf: Vec<DeckConfSchema11> = col
                .storage
                .all_deck_config()?
                .into_iter()
                .map(Into::into)
                .collect();
            serde_json::to_vec(&conf).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_config(&self, input: pb::DeckConfigId) -> Result<pb::DeckConfig> {
        self.with_col(|col| Ok(col.get_deck_config(input.into(), true)?.unwrap().into()))
    }

    fn get_deck_config_legacy(&self, input: pb::DeckConfigId) -> Result<pb::Json> {
        self.with_col(|col| {
            let conf = col.get_deck_config(input.into(), true)?.unwrap();
            let conf: DeckConfSchema11 = conf.into();
            Ok(serde_json::to_vec(&conf)?)
        })
        .map(Into::into)
    }

    fn new_deck_config_legacy(&self, _input: pb::Empty) -> Result<pb::Json> {
        serde_json::to_vec(&DeckConfSchema11::default())
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_deck_config(&self, input: pb::DeckConfigId) -> Result<pb::Empty> {
        self.with_col(|col| col.transact_no_undo(|col| col.remove_deck_config_inner(input.into())))
            .map(Into::into)
    }

    fn get_deck_configs_for_update(&self, input: pb::DeckId) -> Result<pb::DeckConfigsForUpdate> {
        self.with_col(|col| col.get_deck_configs_for_update(input.into()))
    }

    fn update_deck_configs(&self, input: pb::UpdateDeckConfigsRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| col.update_deck_configs(input.into()))
            .map(Into::into)
    }
}

impl From<DeckConfig> for pb::DeckConfig {
    fn from(c: DeckConfig) -> Self {
        pb::DeckConfig {
            id: c.id.0,
            name: c.name,
            mtime_secs: c.mtime_secs.0,
            usn: c.usn.0,
            config: Some(c.inner),
        }
    }
}

impl From<pb::UpdateDeckConfigsRequest> for UpdateDeckConfigsRequest {
    fn from(c: pb::UpdateDeckConfigsRequest) -> Self {
        UpdateDeckConfigsRequest {
            target_deck_id: c.target_deck_id.into(),
            configs: c.configs.into_iter().map(Into::into).collect(),
            removed_config_ids: c.removed_config_ids.into_iter().map(Into::into).collect(),
            apply_to_children: c.apply_to_children,
            card_state_customizer: c.card_state_customizer,
        }
    }
}

impl From<pb::DeckConfig> for DeckConfig {
    fn from(c: pb::DeckConfig) -> Self {
        DeckConfig {
            id: c.id.into(),
            name: c.name,
            mtime_secs: c.mtime_secs.into(),
            usn: c.usn.into(),
            inner: c.config.unwrap_or_default(),
        }
    }
}
