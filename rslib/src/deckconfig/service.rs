// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;

use crate::collection::Collection;
use crate::deckconfig::DeckConfSchema11;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::deckconfig::UpdateDeckConfigsRequest;
use crate::error::Result;
use crate::scheduler::fsrs::params::ignore_revlogs_before_date_to_ms;

impl crate::services::DeckConfigService for Collection {
    fn add_or_update_deck_config_legacy(
        &mut self,
        input: generic::Json,
    ) -> Result<anki_proto::deck_config::DeckConfigId> {
        let conf: DeckConfSchema11 = serde_json::from_slice(&input.json)?;
        let mut conf: DeckConfig = conf.into();

        self.transact_no_undo(|col| {
            col.add_or_update_deck_config_legacy(&mut conf)?;
            Ok(anki_proto::deck_config::DeckConfigId { dcid: conf.id.0 })
        })
    }

    fn all_deck_config_legacy(&mut self) -> Result<generic::Json> {
        let conf: Vec<DeckConfSchema11> = self
            .storage
            .all_deck_config()?
            .into_iter()
            .map(Into::into)
            .collect();
        serde_json::to_vec(&conf)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_deck_config(
        &mut self,
        input: anki_proto::deck_config::DeckConfigId,
    ) -> Result<anki_proto::deck_config::DeckConfig> {
        Ok(Collection::get_deck_config(self, input.into(), true)?
            .unwrap()
            .into())
    }

    fn get_deck_config_legacy(
        &mut self,
        input: anki_proto::deck_config::DeckConfigId,
    ) -> Result<generic::Json> {
        let conf = Collection::get_deck_config(self, input.into(), true)?.unwrap();
        let conf: DeckConfSchema11 = conf.into();
        Ok(serde_json::to_vec(&conf)?.into())
    }

    fn new_deck_config_legacy(&mut self) -> Result<generic::Json> {
        serde_json::to_vec(&DeckConfSchema11::default())
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_deck_config(&mut self, input: anki_proto::deck_config::DeckConfigId) -> Result<()> {
        self.transact_no_undo(|col| col.remove_deck_config_inner(input.into()))
    }

    fn get_deck_configs_for_update(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::deck_config::DeckConfigsForUpdate> {
        self.get_deck_configs_for_update(input.did.into())
    }

    fn update_deck_configs(
        &mut self,
        input: anki_proto::deck_config::UpdateDeckConfigsRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.update_deck_configs(input.into()).map(Into::into)
    }

    fn get_ignored_before_count(
        &mut self,
        input: anki_proto::deck_config::GetIgnoredBeforeCountRequest,
    ) -> Result<anki_proto::deck_config::GetIgnoredBeforeCountResponse> {
        let timestamp = ignore_revlogs_before_date_to_ms(&input.ignore_revlogs_before_date)?;
        let guard = self.search_cards_into_table(
            &format!("{} -is:new", input.search),
            crate::search::SortMode::NoOrder,
        )?;

        Ok(anki_proto::deck_config::GetIgnoredBeforeCountResponse {
            included: guard
                .col
                .storage
                .get_card_count_with_ignore_before(timestamp)?,
            total: guard.cards.try_into().unwrap_or(0),
        })
    }

    fn get_retention_workload(
        &mut self,
        input: anki_proto::deck_config::GetRetentionWorkloadRequest,
    ) -> Result<anki_proto::deck_config::GetRetentionWorkloadResponse> {
        const LEARN_SPAN: usize = 100_000_000;
        const TERMINATION_PROB: f32 = 0.001;
        // the default values are from https://github.com/open-spaced-repetition/Anki-button-usage/blob/881009015c2a85ac911021d76d0aacb124849937/analysis.ipynb
        const DEFAULT_LEARN_COST: f32 = 19.4698;
        const DEFAULT_PASS_COST: f32 = 7.8454;
        const DEFAULT_FAIL_COST: f32 = 23.185;
        const DEFAULT_INITIAL_PASS_RATE: f32 = 0.7645;

        let guard =
            self.search_cards_into_table(&input.search, crate::search::SortMode::NoOrder)?;
        let costs = guard.col.storage.get_costs_for_retention()?;

        fn smoothing(obs: f32, default: f32, count: u32) -> f32 {
            let alpha = count as f32 / (50.0 + count as f32);
            obs * alpha + default * (1.0 - alpha)
        }

        let cost_success = smoothing(
            costs.average_pass_time_ms / 1000.0,
            DEFAULT_PASS_COST,
            costs.pass_count,
        );
        let cost_failure = smoothing(
            costs.average_fail_time_ms / 1000.0,
            DEFAULT_FAIL_COST,
            costs.fail_count,
        );
        let cost_learn = smoothing(
            costs.average_learn_time_ms / 1000.0,
            DEFAULT_LEARN_COST,
            costs.learn_count,
        );
        let initial_pass_rate = smoothing(
            costs.initial_pass_rate,
            DEFAULT_INITIAL_PASS_RATE,
            costs.pass_count,
        );

        let before = fsrs::expected_workload(
            &input.w,
            input.before,
            LEARN_SPAN,
            cost_success,
            cost_failure,
            cost_learn,
            initial_pass_rate,
            TERMINATION_PROB,
        )?;
        let after = fsrs::expected_workload(
            &input.w,
            input.after,
            LEARN_SPAN,
            cost_success,
            cost_failure,
            cost_learn,
            initial_pass_rate,
            TERMINATION_PROB,
        )?;

        Ok(anki_proto::deck_config::GetRetentionWorkloadResponse {
            factor: after / before,
        })
    }
}

impl From<DeckConfig> for anki_proto::deck_config::DeckConfig {
    fn from(c: DeckConfig) -> Self {
        anki_proto::deck_config::DeckConfig {
            id: c.id.0,
            name: c.name,
            mtime_secs: c.mtime_secs.0,
            usn: c.usn.0,
            config: Some(c.inner),
        }
    }
}

impl From<anki_proto::deck_config::UpdateDeckConfigsRequest> for UpdateDeckConfigsRequest {
    fn from(c: anki_proto::deck_config::UpdateDeckConfigsRequest) -> Self {
        let mode = c.mode();
        UpdateDeckConfigsRequest {
            target_deck_id: c.target_deck_id.into(),
            configs: c.configs.into_iter().map(Into::into).collect(),
            removed_config_ids: c.removed_config_ids.into_iter().map(Into::into).collect(),
            mode,
            card_state_customizer: c.card_state_customizer,
            limits: c.limits.unwrap_or_default(),
            new_cards_ignore_review_limit: c.new_cards_ignore_review_limit,
            apply_all_parent_limits: c.apply_all_parent_limits,
            fsrs: c.fsrs,
            fsrs_reschedule: c.fsrs_reschedule,
        }
    }
}

impl From<anki_proto::deck_config::DeckConfig> for DeckConfig {
    fn from(c: anki_proto::deck_config::DeckConfig) -> Self {
        DeckConfig {
            id: c.id.into(),
            name: c.name,
            mtime_secs: c.mtime_secs.into(),
            usn: c.usn.into(),
            inner: c.config.unwrap_or_default(),
        }
    }
}

impl From<anki_proto::deck_config::DeckConfigId> for DeckConfigId {
    fn from(dcid: anki_proto::deck_config::DeckConfigId) -> Self {
        DeckConfigId(dcid.dcid)
    }
}
