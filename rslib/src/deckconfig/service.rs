// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use anki_proto::generic;
use rayon::iter::IntoParallelIterator;
use rayon::iter::ParallelIterator;

use crate::collection::Collection;
use crate::deckconfig::DeckConfSchema11;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::deckconfig::UpdateDeckConfigsRequest;
use crate::error::Result;
use crate::scheduler::fsrs::params::ignore_revlogs_before_date_to_ms;
use crate::scheduler::fsrs::simulator::is_included_card;

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
        let days_elapsed = self.timing_today().unwrap().days_elapsed as i32;
        let guard =
            self.search_cards_into_table(&input.search, crate::search::SortMode::NoOrder)?;

        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;

        let mut config = guard.col.get_optimal_retention_parameters(revlogs)?;
        let cards = guard
            .col
            .storage
            .all_searched_cards()?
            .into_iter()
            .filter(is_included_card)
            .filter_map(|c| crate::card::Card::convert(c.clone(), days_elapsed, c.memory_state?))
            .collect::<Vec<fsrs::Card>>();

        config.deck_size = guard.cards;

        let costs = (10u32..=99u32)
            .into_par_iter()
            .map(|dr| {
                Ok((
                    dr,
                    fsrs::expected_workload_with_existing_cards(
                        &input.w,
                        dr as f32 / 100.,
                        &config,
                        &cards,
                    )?,
                ))
            })
            .collect::<Result<HashMap<_, _>>>()?;

        Ok(anki_proto::deck_config::GetRetentionWorkloadResponse { costs })
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
            fsrs_health_check: c.fsrs_health_check,
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
