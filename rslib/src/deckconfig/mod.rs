// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod fork_fields;
mod schema11;
mod service;
pub(crate) mod undo;
mod update;

pub use anki_proto::deck_config::deck_config::config::AnswerAction;
pub use anki_proto::deck_config::deck_config::config::FsrsVersion;
pub use anki_proto::deck_config::deck_config::config::LeechAction;
pub use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
pub use anki_proto::deck_config::deck_config::config::NewCardInsertOrder;
pub use anki_proto::deck_config::deck_config::config::NewCardSortOrder;
pub use anki_proto::deck_config::deck_config::config::QuestionAction;
pub use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
pub use anki_proto::deck_config::deck_config::config::ReviewMix;
pub use anki_proto::deck_config::deck_config::Config as DeckConfigInner;
pub(crate) use fork_fields::deck_config_inner_for_storage;
pub(crate) use fork_fields::restore_fork_fields_from_other;
pub use schema11::DeckConfSchema11;
pub use schema11::NewCardOrderSchema11;
pub(crate) use update::fsrs7_enable_scheduling_penalties;
pub(crate) use update::fsrs7_optimize_include_same_day_reviews;
pub use update::UpdateDeckConfigsRequest;

/// Old deck config and cards table store 250% as 2500.
pub(crate) const INITIAL_EASE_FACTOR_THOUSANDS: u16 = (INITIAL_EASE_FACTOR * 1000.0) as u16;

use crate::define_newtype;
use crate::prelude::*;
use crate::scheduler::states::review::INITIAL_EASE_FACTOR;

define_newtype!(DeckConfigId, i64);

pub const DEFAULT_REVIEW_FUZZ_BASE: f32 = 1.0;
pub const DEFAULT_REVIEW_FUZZ_FACTOR_SHORT: f32 = 0.15;
pub const DEFAULT_REVIEW_FUZZ_FACTOR_MID: f32 = 0.10;
pub const DEFAULT_REVIEW_FUZZ_FACTOR_LONG: f32 = 0.05;
pub const DEFAULT_REVIEW_FUZZ_ENABLED: bool = true;
pub(crate) const DEFAULT_RWKV_REVIEW_BATCH_SIZE: u32 = 512;
pub(crate) const DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL: u32 = 1;
pub(crate) const DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD: bool = true;
pub(crate) const DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW: bool = true;
pub(crate) const DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS: u32 = 5;
pub(crate) const DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS: u32 = 30;
pub(crate) const DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION: bool = true;
pub(crate) const DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER: bool = true;

#[derive(Debug, PartialEq, Clone)]
pub struct DeckConfig {
    pub id: DeckConfigId,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub inner: DeckConfigInner,
}

/// NOTE: this does not set the default steps
const DEFAULT_DECK_CONFIG_INNER: DeckConfigInner = DeckConfigInner {
    learn_steps: Vec::new(),
    relearn_steps: Vec::new(),
    new_per_day: 20,
    reviews_per_day: 200,
    new_per_day_minimum: 0,
    initial_ease: 2.5,
    easy_multiplier: 1.3,
    hard_multiplier: 1.2,
    lapse_multiplier: 0.0,
    interval_multiplier: 1.0,
    maximum_review_interval: 36_500,
    minimum_lapse_interval: 1,
    graduating_interval_good: 1,
    graduating_interval_easy: 4,
    new_card_insert_order: NewCardInsertOrder::Due as i32,
    new_card_gather_priority: NewCardGatherPriority::Deck as i32,
    new_card_sort_order: NewCardSortOrder::Template as i32,
    review_order: ReviewCardOrder::Day as i32,
    new_mix: ReviewMix::MixWithReviews as i32,
    interday_learning_mix: ReviewMix::MixWithReviews as i32,
    leech_action: LeechAction::TagOnly as i32,
    leech_threshold: 8,
    leech_only_if_young: false,
    rwkv_review_enabled: false,
    rwkv_review_batch_size: DEFAULT_RWKV_REVIEW_BATCH_SIZE,
    rwkv_review_refresh_interval: DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL,
    rwkv_review_refresh_on_exit: false,
    rwkv_review_allow_same_day_review: DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW,
    same_day_reviews_ignore_review_limit: false,
    rwkv_review_instant_order_enabled: false,
    rwkv_review_dynamic_preset_replay: false,
    rwkv_review_candidate_refresh_enabled: false,
    rwkv_review_precompute_next_card: DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD,
    rwkv_review_min_intervening_reviews: DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS,
    rwkv_review_min_elapsed_secs: DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS,
    rwkv_review_first_review_elapsed_from_card_creation:
        DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION,
    rwkv_review_enforce_grade_order: DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER,
    rwkv_review_minimum_reviews_per_day: 0,
    disable_autoplay: false,
    cap_answer_time_to_secs: 60,
    show_timer: false,
    stop_timer_on_answer: false,
    seconds_to_show_question: 0.0,
    seconds_to_show_answer: 0.0,
    question_action: QuestionAction::ShowAnswer as i32,
    answer_action: AnswerAction::BuryCard as i32,
    wait_for_audio: true,
    skip_question_when_replaying_answer: false,
    bury_new: false,
    bury_reviews: false,
    bury_interday_learning: false,
    fsrs_params_4: vec![],
    fsrs_params_5: vec![],
    fsrs_params_6: vec![],
    fsrs_params_7: vec![],
    fsrs_minimum_interval_secs: 1,
    fsrs_dynamic_desired_retention_enabled: false,
    fsrs_dynamic_desired_retention_params: Vec::new(),
    fsrs_dynamic_desired_retention_weights: Vec::new(),
    fsrs_dynamic_desired_retention_avg_drs: Vec::new(),
    fsrs_dynamic_desired_retention_min: 0.30,
    fsrs_dynamic_desired_retention_max: 0.995,
    fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec::new(),
    fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec::new(),
    fsrs_dynamic_desired_retention_fixed_target_weights: Vec::new(),
    fsrs_dynamic_desired_retention_fixed_target_drs: Vec::new(),
    fsrs_dynamic_desired_retention_clamp: false,
    fsrs_version: FsrsVersion::Seven as i32,
    desired_retention: 0.9,
    other: Vec::new(),
    historical_retention: 0.9,
    param_search: String::new(),
    ignore_revlogs_before_date: String::new(),
    easy_days_percentages: Vec::new(),
    review_fuzz_base: None,
    review_fuzz_factor_short: None,
    review_fuzz_factor_mid: None,
    review_fuzz_factor_long: None,
    review_fuzz_enabled: None,
};

impl Default for DeckConfig {
    fn default() -> Self {
        DeckConfig {
            id: DeckConfigId(0),
            name: "".to_string(),
            mtime_secs: Default::default(),
            usn: Default::default(),
            inner: DeckConfigInner {
                learn_steps: vec![1.0, 10.0],
                relearn_steps: vec![10.0],
                easy_days_percentages: vec![1.0; 7],
                ..DEFAULT_DECK_CONFIG_INNER
            },
        }
    }
}

impl DeckConfig {
    pub(crate) fn intraday_learning_mix(&self) -> ReviewMix {
        let Ok(other) = serde_json::from_slice::<serde_json::Value>(&self.inner.other) else {
            return ReviewMix::MixWithReviews;
        };
        let Some(metadata) = other.get("jschoreels.deckoptions") else {
            return ReviewMix::MixWithReviews;
        };
        if let Some(value) = metadata.get("intraday_learning_mix") {
            return value
                .as_i64()
                .and_then(|value| i32::try_from(value).ok())
                .and_then(|value| ReviewMix::try_from(value).ok())
                .unwrap_or(ReviewMix::MixWithReviews);
        }
        if metadata
            .get("intraday_learning_before_reviews")
            .and_then(serde_json::Value::as_bool)
            == Some(true)
        {
            ReviewMix::BeforeReviews
        } else {
            ReviewMix::MixWithReviews
        }
    }

    /// kuma3: with new cards mixed into reviews, one new card after this many
    /// reviews; None spreads them evenly (Anki's mix).
    pub(crate) fn reviews_per_new_card(&self) -> Option<u32> {
        serde_json::from_slice::<serde_json::Value>(&self.inner.other)
            .ok()?
            .get("kuma3.deckoptions")?
            .get("reviews_per_new_card")?
            .as_u64()
            .filter(|&n| n > 0)
            .map(|n| n.min(9999) as u32)
    }

    fn params_usable_in_current_fsrs(params: &[f32]) -> bool {
        matches!(params.len(), 17 | 19 | 21 | 34) && params.iter().all(|w| w.is_finite())
    }

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
    }

    pub fn selected_fsrs_params(&self) -> &[f32] {
        match FsrsVersion::try_from(self.inner.fsrs_version).unwrap_or(FsrsVersion::Seven) {
            FsrsVersion::Seven => &self.inner.fsrs_params_7,
            FsrsVersion::Six => &self.inner.fsrs_params_6,
            FsrsVersion::Five => &self.inner.fsrs_params_5,
            FsrsVersion::Four => &self.inner.fsrs_params_4,
        }
    }

    /// Retrieve FSRS params according to the selected version. FSRS-7 uses
    /// its own defaults when it has not been optimized; older versions retain
    /// the legacy stored-parameter fallback behavior.
    pub fn fsrs_params(&self) -> &[f32] {
        let version = FsrsVersion::try_from(self.inner.fsrs_version).unwrap_or(FsrsVersion::Seven);
        if version == FsrsVersion::Seven
            && (self.inner.fsrs_params_7.len() != fsrs::DEFAULT_PARAMETERS.len()
                || !self.inner.fsrs_params_7.iter().all(|w| w.is_finite()))
        {
            &fsrs::DEFAULT_PARAMETERS
        } else if Self::params_usable_in_current_fsrs(self.selected_fsrs_params()) {
            self.selected_fsrs_params()
        } else if Self::params_usable_in_current_fsrs(&self.inner.fsrs_params_7) {
            &self.inner.fsrs_params_7
        } else if Self::params_usable_in_current_fsrs(&self.inner.fsrs_params_6) {
            &self.inner.fsrs_params_6
        } else if Self::params_usable_in_current_fsrs(&self.inner.fsrs_params_5) {
            &self.inner.fsrs_params_5
        } else if Self::params_usable_in_current_fsrs(&self.inner.fsrs_params_4) {
            &self.inner.fsrs_params_4
        } else {
            &fsrs::FSRS6_DEFAULT_PARAMETERS
        }
    }

    /// Clear the FSRS 6.0 params, along with the 5.0 and 4.x fallbacks.
    pub(crate) fn clear_fsrs_params(&mut self) {
        self.inner.fsrs_params_4.clear();
        self.inner.fsrs_params_5.clear();
        self.inner.fsrs_params_6.clear();
    }
}

impl Collection {
    /// If fallback is true, guaranteed to return a deck config.
    pub fn get_deck_config(
        &self,
        dcid: DeckConfigId,
        fallback: bool,
    ) -> Result<Option<DeckConfig>> {
        if let Some(conf) = self.storage.get_deck_config(dcid)? {
            return Ok(Some(conf));
        }
        if fallback {
            if let Some(conf) = self.storage.get_deck_config(DeckConfigId(1))? {
                return Ok(Some(conf));
            }
            // if even the default deck config is missing, just return the defaults
            Ok(Some(DeckConfig::default()))
        } else {
            Ok(None)
        }
    }
}

impl Collection {
    pub(crate) fn add_or_update_deck_config(&mut self, config: &mut DeckConfig) -> Result<()> {
        let usn = Some(self.usn()?);

        if config.id.0 == 0 {
            self.add_deck_config_inner(config, usn)
        } else {
            let original = self
                .storage
                .get_deck_config(config.id)?
                .or_not_found(config.id)?;
            self.update_deck_config_inner(config, original, usn)
        }
    }

    /// Used by the old import code; if provided id is non-zero, will add
    /// instead of ignoring. Does not support undo.
    pub(crate) fn add_or_update_deck_config_legacy(
        &mut self,
        config: &mut DeckConfig,
    ) -> Result<()> {
        let usn = self.usn()?;

        if config.id.0 == 0 {
            self.add_deck_config_inner(config, Some(usn))
        } else {
            config.set_modified(usn);
            self.storage
                .add_or_update_deck_config_with_existing_id(config)
        }
    }

    /// Assigns an id and adds to DB. If usn is provided, modification time and
    /// usn will be updated.
    pub(crate) fn add_deck_config_inner(
        &mut self,
        config: &mut DeckConfig,
        usn: Option<Usn>,
    ) -> Result<()> {
        if let Some(usn) = usn {
            config.set_modified(usn);
        }
        config.id.0 = TimestampMillis::now().0;
        self.add_deck_config_undoable(config)
    }

    /// Update an existing deck config. If usn is provided, modification time
    /// and usn will be updated.
    pub(crate) fn update_deck_config_inner(
        &mut self,
        config: &mut DeckConfig,
        original: DeckConfig,
        usn: Option<Usn>,
    ) -> Result<()> {
        if config == &original {
            return Ok(());
        }
        if let Some(usn) = usn {
            config.set_modified(usn);
        }
        self.update_deck_config_undoable(config, original)
    }

    /// Remove a deck configuration. This will force a full sync.
    pub(crate) fn remove_deck_config_inner(&mut self, dcid: DeckConfigId) -> Result<()> {
        require!(dcid.0 != 1, "can't delete default conf");
        let conf = self.storage.get_deck_config(dcid)?.or_not_found(dcid)?;
        self.set_schema_modified()?;
        self.remove_deck_config_undoable(conf)
    }
}

/// There was a period of time when the deck options screen was allowing
/// 0/NaN to be persisted, so we need to check the values are within
/// valid bounds when reading from the DB.
pub(crate) fn ensure_deck_config_values_valid(config: &mut DeckConfigInner) {
    let default = DEFAULT_DECK_CONFIG_INNER;
    ensure_u32_valid(&mut config.new_per_day, default.new_per_day, 0, 9999);
    ensure_u32_valid(
        &mut config.reviews_per_day,
        default.reviews_per_day,
        0,
        9999,
    );
    ensure_u32_valid(
        &mut config.new_per_day_minimum,
        default.new_per_day_minimum,
        0,
        9999,
    );
    ensure_f32_valid(&mut config.initial_ease, default.initial_ease, 1.31, 5.0);
    ensure_f32_valid(
        &mut config.easy_multiplier,
        default.easy_multiplier,
        1.0,
        5.0,
    );
    ensure_f32_valid(
        &mut config.hard_multiplier,
        default.hard_multiplier,
        0.5,
        1.3,
    );
    ensure_f32_valid(
        &mut config.lapse_multiplier,
        default.lapse_multiplier,
        0.0,
        1.0,
    );
    ensure_f32_valid(
        &mut config.interval_multiplier,
        default.interval_multiplier,
        0.5,
        2.0,
    );
    ensure_u32_valid(
        &mut config.maximum_review_interval,
        default.maximum_review_interval,
        1,
        36_500,
    );
    ensure_u32_valid(
        &mut config.fsrs_minimum_interval_secs,
        default.fsrs_minimum_interval_secs,
        1,
        36_500 * 86_400,
    );
    ensure_u32_valid(
        &mut config.rwkv_review_batch_size,
        default.rwkv_review_batch_size,
        64,
        8192,
    );
    ensure_u32_valid(
        &mut config.rwkv_review_refresh_interval,
        default.rwkv_review_refresh_interval,
        1,
        10_000,
    );
    ensure_u32_valid(
        &mut config.rwkv_review_min_intervening_reviews,
        default.rwkv_review_min_intervening_reviews,
        0,
        10_000,
    );
    ensure_u32_valid(
        &mut config.rwkv_review_min_elapsed_secs,
        default.rwkv_review_min_elapsed_secs,
        0,
        86_400,
    );
    ensure_u32_valid(
        &mut config.rwkv_review_minimum_reviews_per_day,
        default.rwkv_review_minimum_reviews_per_day,
        0,
        9999,
    );
    ensure_u32_valid(
        &mut config.minimum_lapse_interval,
        default.minimum_lapse_interval,
        1,
        36_500,
    );
    ensure_u32_valid(
        &mut config.graduating_interval_good,
        default.graduating_interval_good,
        1,
        36_500,
    );
    ensure_u32_valid(
        &mut config.graduating_interval_easy,
        default.graduating_interval_easy,
        1,
        36_500,
    );
    ensure_u32_valid(
        &mut config.leech_threshold,
        default.leech_threshold,
        1,
        9999,
    );
    ensure_u32_valid(
        &mut config.cap_answer_time_to_secs,
        default.cap_answer_time_to_secs,
        1,
        9999,
    );
    ensure_f32_valid(
        &mut config.desired_retention,
        default.desired_retention,
        0.1,
        0.99,
    );
    ensure_f32_valid(
        &mut config.historical_retention,
        default.historical_retention,
        0.7,
        0.97,
    )
}

fn ensure_f32_valid(val: &mut f32, default: f32, min: f32, max: f32) {
    if val.is_nan() || *val < min || *val > max {
        *val = default;
    }
}

fn ensure_u32_valid(val: &mut u32, default: u32, min: u32, max: u32) {
    if *val < min || *val > max {
        *val = default;
    }
}

#[cfg(test)]
mod tests {

    use std::assert_matches;

    use super::*;

    #[test]
    fn get_deck_config_returns_created_config() -> Result<()> {
        let col = Collection::new();
        let mut config = DeckConfig::default();
        config.id.0 = TimestampMillis::now().0;
        col.storage
            .add_or_update_deck_config_with_existing_id(&config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn get_deck_config_returns_none_when_flag_is_unset() -> Result<()> {
        let col = Collection::new();
        let config = col.get_deck_config(DeckConfigId(TimestampMillis::now().0), false)?;
        assert_eq!(config, None);

        Ok(())
    }

    #[test]
    fn get_deck_config_returns_default_when_flag_is_set() -> Result<()> {
        let col = Collection::new();
        let config = col.get_deck_config(DeckConfigId(TimestampMillis::now().0), true)?;
        assert_matches!(config, Some(_));

        Ok(())
    }

    #[test]
    fn get_deck_config_returns_default_even_if_missing_when_flag_is_set() -> Result<()> {
        let col = Collection::new();
        col.storage.remove_deck_conf(DeckConfigId(1))?;
        let config = col.get_deck_config(DeckConfigId(TimestampMillis::now().0), true)?;
        assert_matches!(config, Some(_));

        Ok(())
    }

    #[test]
    fn add_deck_config_inner_uses_usn() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let mtime = config.mtime_secs;
        col.add_deck_config_inner(&mut config, Some(Usn(1)))?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(config.usn, Usn(1));
        assert_ne!(config.mtime_secs, mtime);
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn update_deck_config_inner_uses_usn() -> Result<()> {
        let mut col = Collection::new();
        let mut original = DeckConfig::default();
        col.add_deck_config_undoable(&mut original)?;
        let mtime = original.mtime_secs;
        let mut config = original.clone();
        config.name = "updated".into();
        col.update_deck_config_inner(&mut config, original, Some(Usn(1)))?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(config.usn, Usn(1));
        assert_ne!(config.mtime_secs, mtime);
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn update_deck_config_inner_ignores_usn_if_identical() -> Result<()> {
        let mut col = Collection::new();
        let mut original = DeckConfig::default();
        col.add_deck_config_undoable(&mut original)?;
        let mtime = original.mtime_secs;
        let mut config = original.clone();
        col.update_deck_config_inner(&mut config, original, Some(Usn(1)))?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_ne!(config.usn, Usn(1));
        assert_eq!(config.mtime_secs, mtime);
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_adds_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        col.add_or_update_deck_config(&mut config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_updates_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        col.add_deck_config_undoable(&mut config)?;
        config.name = "updated".into();
        col.add_or_update_deck_config(&mut config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_fails_with_not_found() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        config.id.0 = TimestampMillis::now().0;
        assert_matches!(
            col.add_or_update_deck_config(&mut config),
            Err(AnkiError::NotFound { .. })
        );

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_legacy_adds_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        col.add_or_update_deck_config_legacy(&mut config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_legacy_updates_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        col.add_deck_config_undoable(&mut config)?;
        config.name = "updated".into();
        col.add_or_update_deck_config_legacy(&mut config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_or_update_deck_config_legacy_uses_provided_id() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        config.id.0 = TimestampMillis::now().0;
        col.add_or_update_deck_config_legacy(&mut config)?;
        let returned_config = col.get_deck_config(config.id, false)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn remove_deck_config_inner_removes_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let stamps = col.storage.get_collection_timestamps()?;
        col.add_deck_config_undoable(&mut config)?;
        col.remove_deck_config_inner(config.id)?;
        assert_eq!(col.get_deck_config(config.id, false)?, None);
        // Schema should be modified
        assert_ne!(
            col.storage.get_collection_timestamps()?.schema_change,
            stamps.schema_change
        );

        Ok(())
    }

    #[test]
    fn remove_deck_config_inner_fails_on_default_config() -> Result<()> {
        let mut col = Collection::new();
        let config = col.get_deck_config(DeckConfigId(1), true)?.unwrap();

        assert_matches!(
            col.remove_deck_config_inner(config.id),
            Err(AnkiError::InvalidInput { .. })
        );

        Ok(())
    }

    #[test]
    fn intraday_learning_mix_reads_modes_and_legacy_toggle() -> Result<()> {
        for (metadata, expected) in [
            (serde_json::json!({}), ReviewMix::MixWithReviews),
            (
                serde_json::json!({"intraday_learning_before_reviews": false}),
                ReviewMix::MixWithReviews,
            ),
            (
                serde_json::json!({"intraday_learning_before_reviews": true}),
                ReviewMix::BeforeReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": 0}),
                ReviewMix::MixWithReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": 1}),
                ReviewMix::AfterReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": 2}),
                ReviewMix::BeforeReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": 99}),
                ReviewMix::MixWithReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": "before"}),
                ReviewMix::MixWithReviews,
            ),
            (
                serde_json::json!({"intraday_learning_mix": 1, "intraday_learning_before_reviews": true}),
                ReviewMix::AfterReviews,
            ),
        ] {
            let mut config = DeckConfig::default();
            config.inner.other = serde_json::to_vec(&serde_json::json!({
                "jschoreels.deckoptions": metadata,
            }))?;
            assert_eq!(config.intraday_learning_mix(), expected, "{metadata}");
        }
        Ok(())
    }

    #[test]
    fn fsrs_params_respects_selected_version_when_usable() {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Six as i32;
        config.inner.fsrs_params_6 = vec![1.0_f32; 21];
        config.inner.fsrs_params_7 = vec![2.0_f32; 34];

        assert_eq!(config.fsrs_params(), &[1.0_f32; 21]);
    }

    #[test]
    fn unoptimized_fsrs7_uses_fsrs7_defaults() {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Seven as i32;
        config.inner.fsrs_params_7.clear();
        config.inner.fsrs_params_6 = vec![2.0_f32; 21];

        assert_eq!(config.fsrs_params(), fsrs::DEFAULT_PARAMETERS);
    }

    #[test]
    fn unoptimized_legacy_versions_keep_fsrs6_defaults() {
        for version in [FsrsVersion::Six, FsrsVersion::Five, FsrsVersion::Four] {
            let mut config = DeckConfig::default();
            config.inner.fsrs_version = version as i32;

            assert_eq!(config.fsrs_params(), fsrs::FSRS6_DEFAULT_PARAMETERS);
        }
    }

    #[test]
    fn maximum_rwkv_batch_size_is_preserved() {
        let mut config = DeckConfig::default().inner;
        config.rwkv_review_batch_size = 8192;

        ensure_deck_config_values_valid(&mut config);

        assert_eq!(config.rwkv_review_batch_size, 8192);
    }

    #[test]
    fn invalid_rwkv_batch_size_uses_default() {
        let mut config = DeckConfig::default().inner;
        config.rwkv_review_batch_size = 8193;

        ensure_deck_config_values_valid(&mut config);

        assert_eq!(
            config.rwkv_review_batch_size,
            DEFAULT_RWKV_REVIEW_BATCH_SIZE
        );
    }
}
