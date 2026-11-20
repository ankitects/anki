// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;
use serde_json::Map;
use serde_json::Value;

use super::DeckConfigInner;
use super::FsrsVersion;
use super::DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW;
use super::DEFAULT_RWKV_REVIEW_BATCH_SIZE;
use super::DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER;
use super::DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION;
use super::DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS;
use super::DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS;
use super::DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD;
use super::DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL;

const FSRS_FORK_FIELDS_KEY: &str = "jschoreels.fsrs";
const RWKV_FORK_FIELDS_KEY: &str = "jschoreels.rwkv";
const FSRS_MINIMUM_INTERVAL_SECS_DEFAULT: u32 = 1;
const DYNAMIC_DR_MIN_DEFAULT: f32 = 0.30;
const DYNAMIC_DR_MAX_DEFAULT: f32 = 0.995;

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
struct ForkDeckConfigFields {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    same_day_reviews_ignore_review_limit: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_params_7: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_minimum_interval_secs: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_enabled: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_params: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_weights: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_avg_drs: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_fsrs_eq_weights: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_fsrs_eq_drs: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_fixed_target_weights: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_fixed_target_drs: Option<Vec<f32>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_min: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_max: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_dynamic_desired_retention_clamp: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    fsrs_version: Option<i32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    review_fuzz_base: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    review_fuzz_factor_short: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    review_fuzz_factor_mid: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    review_fuzz_factor_long: Option<f32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    review_fuzz_enabled: Option<bool>,
}

impl ForkDeckConfigFields {
    fn from_config(config: &DeckConfigInner) -> Self {
        Self {
            same_day_reviews_ignore_review_limit: true_only(
                config.same_day_reviews_ignore_review_limit,
            ),
            fsrs_params_7: non_empty_vec(&config.fsrs_params_7),
            fsrs_minimum_interval_secs: non_default(
                config.fsrs_minimum_interval_secs,
                FSRS_MINIMUM_INTERVAL_SECS_DEFAULT,
            ),
            fsrs_dynamic_desired_retention_enabled: true_only(
                config.fsrs_dynamic_desired_retention_enabled,
            ),
            fsrs_dynamic_desired_retention_params: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_params,
            ),
            fsrs_dynamic_desired_retention_weights: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_weights,
            ),
            fsrs_dynamic_desired_retention_avg_drs: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_avg_drs,
            ),
            fsrs_dynamic_desired_retention_fsrs_eq_weights: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_fsrs_eq_weights,
            ),
            fsrs_dynamic_desired_retention_fsrs_eq_drs: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_fsrs_eq_drs,
            ),
            fsrs_dynamic_desired_retention_fixed_target_weights: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_fixed_target_weights,
            ),
            fsrs_dynamic_desired_retention_fixed_target_drs: non_empty_vec(
                &config.fsrs_dynamic_desired_retention_fixed_target_drs,
            ),
            fsrs_dynamic_desired_retention_min: non_default_f32(
                config.fsrs_dynamic_desired_retention_min,
                DYNAMIC_DR_MIN_DEFAULT,
            ),
            fsrs_dynamic_desired_retention_max: non_default_f32(
                config.fsrs_dynamic_desired_retention_max,
                DYNAMIC_DR_MAX_DEFAULT,
            ),
            fsrs_dynamic_desired_retention_clamp: true_only(
                config.fsrs_dynamic_desired_retention_clamp,
            ),
            fsrs_version: non_default(config.fsrs_version, FsrsVersion::Seven as i32),
            review_fuzz_base: config.review_fuzz_base,
            review_fuzz_factor_short: config.review_fuzz_factor_short,
            review_fuzz_factor_mid: config.review_fuzz_factor_mid,
            review_fuzz_factor_long: config.review_fuzz_factor_long,
            review_fuzz_enabled: config.review_fuzz_enabled,
        }
    }

    fn apply_to_config(self, config: &mut DeckConfigInner) {
        if let Some(value) = self.same_day_reviews_ignore_review_limit {
            config.same_day_reviews_ignore_review_limit = value;
        }
        if let Some(value) = self.fsrs_params_7 {
            config.fsrs_params_7 = value;
        }
        if let Some(value) = self.fsrs_minimum_interval_secs {
            config.fsrs_minimum_interval_secs = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_enabled {
            config.fsrs_dynamic_desired_retention_enabled = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_params {
            config.fsrs_dynamic_desired_retention_params = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_weights {
            config.fsrs_dynamic_desired_retention_weights = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_avg_drs {
            config.fsrs_dynamic_desired_retention_avg_drs = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_fsrs_eq_weights {
            config.fsrs_dynamic_desired_retention_fsrs_eq_weights = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_fsrs_eq_drs {
            config.fsrs_dynamic_desired_retention_fsrs_eq_drs = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_fixed_target_weights {
            config.fsrs_dynamic_desired_retention_fixed_target_weights = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_fixed_target_drs {
            config.fsrs_dynamic_desired_retention_fixed_target_drs = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_min {
            config.fsrs_dynamic_desired_retention_min = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_max {
            config.fsrs_dynamic_desired_retention_max = value;
        }
        if let Some(value) = self.fsrs_dynamic_desired_retention_clamp {
            config.fsrs_dynamic_desired_retention_clamp = value;
        }
        if let Some(value) = self.fsrs_version {
            config.fsrs_version = value;
        }
        if let Some(value) = self.review_fuzz_base {
            config.review_fuzz_base = Some(value);
        }
        if let Some(value) = self.review_fuzz_factor_short {
            config.review_fuzz_factor_short = Some(value);
        }
        if let Some(value) = self.review_fuzz_factor_mid {
            config.review_fuzz_factor_mid = Some(value);
        }
        if let Some(value) = self.review_fuzz_factor_long {
            config.review_fuzz_factor_long = Some(value);
        }
        if let Some(value) = self.review_fuzz_enabled {
            config.review_fuzz_enabled = Some(value);
        }
    }

    fn is_empty(&self) -> bool {
        serde_json::to_value(self)
            .ok()
            .and_then(|value| value.as_object().map(Map::is_empty))
            .unwrap_or(true)
    }
}

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
struct RwkvDeckConfigFields {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_enabled: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_batch_size: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_refresh_interval: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_refresh_on_exit: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_allow_same_day_review: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_min_intervening_reviews: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_min_elapsed_secs: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_instant_order_enabled: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_dynamic_preset_replay: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_candidate_refresh_enabled: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_precompute_next_card: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_first_review_elapsed_from_card_creation: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_enforce_grade_order: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    rwkv_review_minimum_reviews_per_day: Option<u32>,
}

impl RwkvDeckConfigFields {
    fn from_config(config: &DeckConfigInner) -> Self {
        Self {
            rwkv_review_enabled: true_only(config.rwkv_review_enabled),
            rwkv_review_batch_size: non_default(
                config.rwkv_review_batch_size,
                DEFAULT_RWKV_REVIEW_BATCH_SIZE,
            ),
            rwkv_review_refresh_interval: non_default(
                config.rwkv_review_refresh_interval,
                DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL,
            ),
            rwkv_review_refresh_on_exit: true_only(config.rwkv_review_refresh_on_exit),
            rwkv_review_allow_same_day_review: non_default(
                config.rwkv_review_allow_same_day_review,
                DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW,
            ),
            rwkv_review_min_intervening_reviews: non_default(
                config.rwkv_review_min_intervening_reviews,
                DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS,
            ),
            rwkv_review_min_elapsed_secs: non_default(
                config.rwkv_review_min_elapsed_secs,
                DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS,
            ),
            rwkv_review_instant_order_enabled: true_only(config.rwkv_review_instant_order_enabled),
            rwkv_review_dynamic_preset_replay: true_only(config.rwkv_review_dynamic_preset_replay),
            rwkv_review_candidate_refresh_enabled: true_only(
                config.rwkv_review_candidate_refresh_enabled,
            ),
            rwkv_review_precompute_next_card: non_default(
                config.rwkv_review_precompute_next_card,
                DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD,
            ),
            rwkv_review_first_review_elapsed_from_card_creation: non_default(
                config.rwkv_review_first_review_elapsed_from_card_creation,
                DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION,
            ),
            rwkv_review_enforce_grade_order: non_default(
                config.rwkv_review_enforce_grade_order,
                DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER,
            ),
            rwkv_review_minimum_reviews_per_day: non_default(
                config.rwkv_review_minimum_reviews_per_day,
                0,
            ),
        }
    }

    fn apply_to_config(self, config: &mut DeckConfigInner) {
        if let Some(value) = self.rwkv_review_enabled {
            config.rwkv_review_enabled = value;
        }
        if let Some(value) = self.rwkv_review_batch_size {
            config.rwkv_review_batch_size = value;
        }
        if let Some(value) = self.rwkv_review_refresh_interval {
            config.rwkv_review_refresh_interval = value;
        }
        if let Some(value) = self.rwkv_review_refresh_on_exit {
            config.rwkv_review_refresh_on_exit = value;
        }
        if let Some(value) = self.rwkv_review_allow_same_day_review {
            config.rwkv_review_allow_same_day_review = value;
        }
        if let Some(value) = self.rwkv_review_min_intervening_reviews {
            config.rwkv_review_min_intervening_reviews = value;
        }
        if let Some(value) = self.rwkv_review_min_elapsed_secs {
            config.rwkv_review_min_elapsed_secs = value;
        }
        if let Some(value) = self.rwkv_review_instant_order_enabled {
            config.rwkv_review_instant_order_enabled = value;
        }
        if let Some(value) = self.rwkv_review_dynamic_preset_replay {
            config.rwkv_review_dynamic_preset_replay = value;
        }
        if let Some(value) = self.rwkv_review_candidate_refresh_enabled {
            config.rwkv_review_candidate_refresh_enabled = value;
        }
        if let Some(value) = self.rwkv_review_precompute_next_card {
            config.rwkv_review_precompute_next_card = value;
        }
        if let Some(value) = self.rwkv_review_first_review_elapsed_from_card_creation {
            config.rwkv_review_first_review_elapsed_from_card_creation = value;
        }
        if let Some(value) = self.rwkv_review_enforce_grade_order {
            config.rwkv_review_enforce_grade_order = value;
        }
        if let Some(value) = self.rwkv_review_minimum_reviews_per_day {
            config.rwkv_review_minimum_reviews_per_day = value;
        }
    }

    fn is_empty(&self) -> bool {
        serde_json::to_value(self)
            .ok()
            .and_then(|value| value.as_object().map(Map::is_empty))
            .unwrap_or(true)
    }
}

pub(crate) fn restore_fork_fields_from_other(config: &mut DeckConfigInner) {
    config.rwkv_review_precompute_next_card = DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD;
    if !config.rwkv_review_allow_same_day_review {
        config.rwkv_review_allow_same_day_review = DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW;
    }
    if config.rwkv_review_min_intervening_reviews == 0 {
        config.rwkv_review_min_intervening_reviews = DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS;
    }
    if config.rwkv_review_min_elapsed_secs == 0 {
        config.rwkv_review_min_elapsed_secs = DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS;
    }
    if !config.rwkv_review_first_review_elapsed_from_card_creation {
        config.rwkv_review_first_review_elapsed_from_card_creation =
            DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION;
    }
    if !config.rwkv_review_enforce_grade_order {
        config.rwkv_review_enforce_grade_order = DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER;
    }

    if let Some(fields) = fork_fields_from_other(&config.other) {
        fields.apply_to_config(config);
    }
    if let Some(fields) = legacy_rwkv_fields_from_fsrs_other(&config.other) {
        fields.apply_to_config(config);
    }
    if let Some(fields) = rwkv_fields_from_other(&config.other) {
        fields.apply_to_config(config);
    }

    if config.fsrs_minimum_interval_secs == 0 {
        config.fsrs_minimum_interval_secs = FSRS_MINIMUM_INTERVAL_SECS_DEFAULT;
    }
    if config.fsrs_dynamic_desired_retention_min == 0.0 {
        config.fsrs_dynamic_desired_retention_min = DYNAMIC_DR_MIN_DEFAULT;
    }
    if config.fsrs_dynamic_desired_retention_max == 0.0 {
        config.fsrs_dynamic_desired_retention_max = DYNAMIC_DR_MAX_DEFAULT;
    }
    if config.rwkv_review_batch_size == 0 {
        config.rwkv_review_batch_size = DEFAULT_RWKV_REVIEW_BATCH_SIZE;
    }
    if config.rwkv_review_refresh_interval == 0 {
        config.rwkv_review_refresh_interval = DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL;
    }
}

pub(crate) fn deck_config_inner_for_storage(config: &DeckConfigInner) -> DeckConfigInner {
    let mut storage_config = config.clone();
    let fields = ForkDeckConfigFields::from_config(&storage_config);
    let rwkv_fields = RwkvDeckConfigFields::from_config(&storage_config);
    storage_config.other = other_with_fork_fields(&storage_config.other, fields, rwkv_fields);
    clear_numbered_fork_fields(&mut storage_config);
    storage_config
}

fn fork_fields_from_other(other: &[u8]) -> Option<ForkDeckConfigFields> {
    let value: Value = serde_json::from_slice(other).ok()?;
    serde_json::from_value(value.get(FSRS_FORK_FIELDS_KEY)?.clone()).ok()
}

fn rwkv_fields_from_other(other: &[u8]) -> Option<RwkvDeckConfigFields> {
    let value: Value = serde_json::from_slice(other).ok()?;
    serde_json::from_value(value.get(RWKV_FORK_FIELDS_KEY)?.clone()).ok()
}

fn legacy_rwkv_fields_from_fsrs_other(other: &[u8]) -> Option<RwkvDeckConfigFields> {
    let value: Value = serde_json::from_slice(other).ok()?;
    serde_json::from_value(value.get(FSRS_FORK_FIELDS_KEY)?.clone()).ok()
}

fn other_with_fork_fields(
    other: &[u8],
    fields: ForkDeckConfigFields,
    rwkv_fields: RwkvDeckConfigFields,
) -> Vec<u8> {
    let mut object = serde_json::from_slice::<Value>(other)
        .ok()
        .and_then(|value| value.as_object().cloned())
        .unwrap_or_default();

    if fields.is_empty() {
        object.remove(FSRS_FORK_FIELDS_KEY);
    } else if let Ok(value) = serde_json::to_value(fields) {
        object.insert(FSRS_FORK_FIELDS_KEY.to_string(), value);
    }

    if rwkv_fields.is_empty() {
        object.remove(RWKV_FORK_FIELDS_KEY);
    } else if let Ok(value) = serde_json::to_value(rwkv_fields) {
        object.insert(RWKV_FORK_FIELDS_KEY.to_string(), value);
    }

    if object.is_empty() {
        Vec::new()
    } else {
        serde_json::to_vec(&object).unwrap_or_default()
    }
}

fn clear_numbered_fork_fields(config: &mut DeckConfigInner) {
    config.fsrs_params_7.clear();
    config.fsrs_minimum_interval_secs = 0;
    config.fsrs_dynamic_desired_retention_enabled = false;
    config.fsrs_dynamic_desired_retention_params.clear();
    config.fsrs_dynamic_desired_retention_weights.clear();
    config.fsrs_dynamic_desired_retention_avg_drs.clear();
    config
        .fsrs_dynamic_desired_retention_fsrs_eq_weights
        .clear();
    config.fsrs_dynamic_desired_retention_fsrs_eq_drs.clear();
    config
        .fsrs_dynamic_desired_retention_fixed_target_weights
        .clear();
    config
        .fsrs_dynamic_desired_retention_fixed_target_drs
        .clear();
    config.fsrs_dynamic_desired_retention_min = 0.0;
    config.fsrs_dynamic_desired_retention_max = 0.0;
    config.fsrs_dynamic_desired_retention_clamp = false;
    config.fsrs_version = FsrsVersion::Seven as i32;
    config.review_fuzz_base = None;
    config.review_fuzz_factor_short = None;
    config.review_fuzz_factor_mid = None;
    config.review_fuzz_factor_long = None;
    config.review_fuzz_enabled = None;
    config.same_day_reviews_ignore_review_limit = false;
    config.rwkv_review_enabled = false;
    config.rwkv_review_batch_size = 0;
    config.rwkv_review_refresh_interval = 0;
    config.rwkv_review_refresh_on_exit = false;
    config.rwkv_review_allow_same_day_review = false;
    config.rwkv_review_min_intervening_reviews = 0;
    config.rwkv_review_min_elapsed_secs = 0;
    config.rwkv_review_instant_order_enabled = false;
    config.rwkv_review_dynamic_preset_replay = false;
    config.rwkv_review_candidate_refresh_enabled = false;
    config.rwkv_review_precompute_next_card = false;
    config.rwkv_review_first_review_elapsed_from_card_creation = false;
    config.rwkv_review_enforce_grade_order = false;
    config.rwkv_review_minimum_reviews_per_day = 0;
}

fn non_empty_vec(values: &[f32]) -> Option<Vec<f32>> {
    (!values.is_empty()).then(|| values.to_vec())
}

fn non_default<T: Copy + PartialEq>(value: T, default: T) -> Option<T> {
    (value != default).then_some(value)
}

fn non_default_f32(value: f32, default: f32) -> Option<f32> {
    ((value - default).abs() > f32::EPSILON).then_some(value)
}

fn true_only(value: bool) -> Option<bool> {
    value.then_some(value)
}

#[cfg(test)]
mod tests {
    use prost::Message;
    use serde_json::json;
    use serde_json::Value;

    use super::*;

    fn config_with_fork_fields() -> DeckConfigInner {
        DeckConfigInner {
            fsrs_params_7: vec![0.1; 34],
            fsrs_minimum_interval_secs: 42,
            fsrs_dynamic_desired_retention_enabled: true,
            fsrs_dynamic_desired_retention_params: vec![1.0; 15],
            fsrs_dynamic_desired_retention_weights: vec![0.0, 15.0],
            fsrs_dynamic_desired_retention_avg_drs: vec![0.8, 0.9],
            fsrs_dynamic_desired_retention_fsrs_eq_weights: vec![3.0],
            fsrs_dynamic_desired_retention_fsrs_eq_drs: vec![0.85],
            fsrs_dynamic_desired_retention_fixed_target_weights: vec![4.0],
            fsrs_dynamic_desired_retention_fixed_target_drs: vec![0.86],
            fsrs_dynamic_desired_retention_min: 0.31,
            fsrs_dynamic_desired_retention_max: 0.96,
            fsrs_dynamic_desired_retention_clamp: true,
            fsrs_version: FsrsVersion::Six as i32,
            review_fuzz_base: Some(1.2),
            review_fuzz_factor_short: Some(0.2),
            review_fuzz_factor_mid: Some(0.1),
            review_fuzz_factor_long: Some(0.05),
            review_fuzz_enabled: Some(false),
            same_day_reviews_ignore_review_limit: true,
            rwkv_review_enabled: true,
            rwkv_review_batch_size: 1024,
            rwkv_review_refresh_interval: 5,
            rwkv_review_refresh_on_exit: true,
            rwkv_review_allow_same_day_review: false,
            rwkv_review_min_intervening_reviews: 3,
            rwkv_review_min_elapsed_secs: 300,
            rwkv_review_instant_order_enabled: true,
            rwkv_review_dynamic_preset_replay: true,
            rwkv_review_candidate_refresh_enabled: true,
            rwkv_review_precompute_next_card: false,
            rwkv_review_first_review_elapsed_from_card_creation: false,
            rwkv_review_enforce_grade_order: false,
            rwkv_review_minimum_reviews_per_day: 42,
            ..Default::default()
        }
    }

    #[test]
    fn storage_clears_numbered_fork_fields() {
        let config = config_with_fork_fields();
        let storage_config = deck_config_inner_for_storage(&config);

        assert!(storage_config.fsrs_params_7.is_empty());
        assert_eq!(storage_config.fsrs_minimum_interval_secs, 0);
        assert!(!storage_config.fsrs_dynamic_desired_retention_enabled);
        assert!(storage_config
            .fsrs_dynamic_desired_retention_params
            .is_empty());
        assert_eq!(storage_config.fsrs_version, FsrsVersion::Seven as i32);
        assert_eq!(storage_config.review_fuzz_base, None);
        assert!(!storage_config.same_day_reviews_ignore_review_limit);
        assert!(!storage_config.rwkv_review_enabled);
        assert_eq!(storage_config.rwkv_review_batch_size, 0);
        assert_eq!(storage_config.rwkv_review_refresh_interval, 0);
        assert!(!storage_config.rwkv_review_refresh_on_exit);
        assert!(!storage_config.rwkv_review_allow_same_day_review);
        assert_eq!(storage_config.rwkv_review_min_intervening_reviews, 0);
        assert_eq!(storage_config.rwkv_review_min_elapsed_secs, 0);
        assert!(!storage_config.rwkv_review_instant_order_enabled);
        assert!(!storage_config.rwkv_review_dynamic_preset_replay);
        assert!(!storage_config.rwkv_review_candidate_refresh_enabled);
        assert!(!storage_config.rwkv_review_precompute_next_card);
        assert!(!storage_config.rwkv_review_first_review_elapsed_from_card_creation);
        assert!(!storage_config.rwkv_review_enforce_grade_order);
        assert_eq!(storage_config.rwkv_review_minimum_reviews_per_day, 0);

        let other: Value = serde_json::from_slice(&storage_config.other).unwrap();
        let fsrs_other = other.get(FSRS_FORK_FIELDS_KEY).unwrap();
        assert_eq!(
            fsrs_other.get("same_day_reviews_ignore_review_limit"),
            Some(&json!(true))
        );
        assert!(fsrs_other.get("rwkv_review_enabled").is_none());
        assert!(fsrs_other.get("rwkv_review_batch_size").is_none());
        assert!(fsrs_other.get("rwkv_review_refresh_interval").is_none());
        assert!(fsrs_other.get("rwkv_review_refresh_on_exit").is_none());
        assert!(fsrs_other
            .get("rwkv_review_allow_same_day_review")
            .is_none());
        assert!(fsrs_other
            .get("rwkv_review_min_intervening_reviews")
            .is_none());
        assert!(fsrs_other.get("rwkv_review_min_elapsed_secs").is_none());
        assert!(fsrs_other
            .get("rwkv_review_instant_order_enabled")
            .is_none());
        assert!(fsrs_other
            .get("rwkv_review_dynamic_preset_replay")
            .is_none());
        assert!(fsrs_other
            .get("rwkv_review_candidate_refresh_enabled")
            .is_none());
        assert!(fsrs_other.get("rwkv_review_precompute_next_card").is_none());
        assert!(fsrs_other
            .get("rwkv_review_first_review_elapsed_from_card_creation")
            .is_none());
        assert!(fsrs_other.get("rwkv_review_enforce_grade_order").is_none());
        assert_eq!(
            other.get(RWKV_FORK_FIELDS_KEY),
            Some(&json!({
                "rwkv_review_enabled": true,
                "rwkv_review_batch_size": 1024,
                "rwkv_review_refresh_interval": 5,
                "rwkv_review_refresh_on_exit": true,
                "rwkv_review_allow_same_day_review": false,
                "rwkv_review_min_intervening_reviews": 3,
                "rwkv_review_min_elapsed_secs": 300,
                "rwkv_review_instant_order_enabled": true,
                "rwkv_review_dynamic_preset_replay": true,
                "rwkv_review_candidate_refresh_enabled": true,
                "rwkv_review_precompute_next_card": false,
                "rwkv_review_first_review_elapsed_from_card_creation": false,
                "rwkv_review_enforce_grade_order": false,
                "rwkv_review_minimum_reviews_per_day": 42,
            }))
        );
    }

    #[test]
    fn storage_other_restores_fork_fields() {
        let config = config_with_fork_fields();
        let storage_config = deck_config_inner_for_storage(&config);
        let mut decoded =
            DeckConfigInner::decode(storage_config.encode_to_vec().as_slice()).unwrap();

        restore_fork_fields_from_other(&mut decoded);

        assert_eq!(decoded.fsrs_params_7, config.fsrs_params_7);
        assert_eq!(
            decoded.fsrs_dynamic_desired_retention_params,
            config.fsrs_dynamic_desired_retention_params
        );
        assert_eq!(
            decoded.fsrs_dynamic_desired_retention_clamp,
            config.fsrs_dynamic_desired_retention_clamp
        );
        assert_eq!(decoded.fsrs_version, config.fsrs_version);
        assert_eq!(decoded.review_fuzz_base, config.review_fuzz_base);
        assert_eq!(
            decoded.same_day_reviews_ignore_review_limit,
            config.same_day_reviews_ignore_review_limit
        );
        assert_eq!(decoded.rwkv_review_enabled, config.rwkv_review_enabled);
        assert_eq!(
            decoded.rwkv_review_batch_size,
            config.rwkv_review_batch_size
        );
        assert_eq!(
            decoded.rwkv_review_refresh_interval,
            config.rwkv_review_refresh_interval
        );
        assert_eq!(
            decoded.rwkv_review_refresh_on_exit,
            config.rwkv_review_refresh_on_exit
        );
        assert_eq!(
            decoded.rwkv_review_allow_same_day_review,
            config.rwkv_review_allow_same_day_review
        );
        assert_eq!(
            decoded.rwkv_review_min_intervening_reviews,
            config.rwkv_review_min_intervening_reviews
        );
        assert_eq!(
            decoded.rwkv_review_min_elapsed_secs,
            config.rwkv_review_min_elapsed_secs
        );
        assert_eq!(
            decoded.rwkv_review_instant_order_enabled,
            config.rwkv_review_instant_order_enabled
        );
        assert_eq!(
            decoded.rwkv_review_dynamic_preset_replay,
            config.rwkv_review_dynamic_preset_replay
        );
        assert_eq!(
            decoded.rwkv_review_candidate_refresh_enabled,
            config.rwkv_review_candidate_refresh_enabled
        );
        assert_eq!(
            decoded.rwkv_review_precompute_next_card,
            config.rwkv_review_precompute_next_card
        );
        assert_eq!(
            decoded.rwkv_review_first_review_elapsed_from_card_creation,
            config.rwkv_review_first_review_elapsed_from_card_creation
        );
        assert_eq!(
            decoded.rwkv_review_enforce_grade_order,
            config.rwkv_review_enforce_grade_order
        );
        assert_eq!(
            decoded.rwkv_review_minimum_reviews_per_day,
            config.rwkv_review_minimum_reviews_per_day
        );
    }

    #[test]
    fn precompute_defaults_on_and_can_be_disabled_in_storage() {
        for enabled in [true, false] {
            let config = DeckConfigInner {
                rwkv_review_precompute_next_card: enabled,
                ..Default::default()
            };
            let storage = deck_config_inner_for_storage(&config);
            let mut decoded = DeckConfigInner::decode(storage.encode_to_vec().as_slice()).unwrap();
            restore_fork_fields_from_other(&mut decoded);
            assert_eq!(decoded.rwkv_review_precompute_next_card, enabled);
        }
        let mut old_config = DeckConfigInner::default();
        restore_fork_fields_from_other(&mut old_config);
        assert!(old_config.rwkv_review_precompute_next_card);
    }

    #[test]
    fn legacy_numbered_fields_remain_readable() {
        let mut config = config_with_fork_fields();
        restore_fork_fields_from_other(&mut config);

        assert_eq!(config.fsrs_params_7, vec![0.1; 34]);
        assert_eq!(config.fsrs_version, FsrsVersion::Six as i32);
        assert_eq!(config.fsrs_dynamic_desired_retention_min, 0.31);
    }

    #[test]
    fn default_rwkv_fields_are_restored_from_cleared_storage_fields() {
        let mut config = DeckConfigInner {
            rwkv_review_batch_size: 0,
            rwkv_review_refresh_interval: 0,
            ..Default::default()
        };

        restore_fork_fields_from_other(&mut config);

        assert_eq!(
            config.rwkv_review_batch_size,
            DEFAULT_RWKV_REVIEW_BATCH_SIZE
        );
        assert_eq!(
            config.rwkv_review_refresh_interval,
            DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL
        );
        assert_eq!(
            config.rwkv_review_allow_same_day_review,
            DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW
        );
        assert_eq!(
            config.rwkv_review_min_intervening_reviews,
            DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS
        );
        assert_eq!(
            config.rwkv_review_min_elapsed_secs,
            DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS
        );
        assert_eq!(
            config.rwkv_review_first_review_elapsed_from_card_creation,
            DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION
        );
        assert_eq!(
            config.rwkv_review_enforce_grade_order,
            DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER
        );
    }

    #[test]
    fn legacy_rwkv_fields_under_fsrs_key_remain_readable() {
        let mut config = DeckConfigInner {
            other: serde_json::to_vec(&json!({
                FSRS_FORK_FIELDS_KEY: {
                    "rwkv_review_enabled": true,
                    "rwkv_review_batch_size": 1024,
                    "rwkv_review_refresh_interval": 7,
                    "rwkv_review_refresh_on_exit": true,
                    "rwkv_review_allow_same_day_review": true,
                    "rwkv_review_min_intervening_reviews": 3,
                    "rwkv_review_min_elapsed_secs": 300,
                    "rwkv_review_instant_order_enabled": true,
                    "rwkv_review_dynamic_preset_replay": true,
                    "rwkv_review_candidate_refresh_enabled": true,
                    "rwkv_review_first_review_elapsed_from_card_creation": true,
                    "rwkv_review_enforce_grade_order": false,
                },
            }))
            .unwrap(),
            rwkv_review_batch_size: 0,
            rwkv_review_refresh_interval: 0,
            ..Default::default()
        };

        restore_fork_fields_from_other(&mut config);

        assert!(config.rwkv_review_enabled);
        assert_eq!(config.rwkv_review_batch_size, 1024);
        assert_eq!(config.rwkv_review_refresh_interval, 7);
        assert!(config.rwkv_review_refresh_on_exit);
        assert!(config.rwkv_review_allow_same_day_review);
        assert_eq!(config.rwkv_review_min_intervening_reviews, 3);
        assert_eq!(config.rwkv_review_min_elapsed_secs, 300);
        assert!(config.rwkv_review_instant_order_enabled);
        assert!(config.rwkv_review_dynamic_preset_replay);
        assert!(config.rwkv_review_candidate_refresh_enabled);
        assert!(config.rwkv_review_first_review_elapsed_from_card_creation);
        assert!(!config.rwkv_review_enforce_grade_order);
    }

    #[test]
    fn rwkv_key_takes_precedence_over_legacy_fsrs_key() {
        let mut config = DeckConfigInner {
            other: serde_json::to_vec(&json!({
                FSRS_FORK_FIELDS_KEY: {
                    "rwkv_review_enabled": true,
                    "rwkv_review_batch_size": 1024,
                    "rwkv_review_refresh_interval": 7,
                    "rwkv_review_refresh_on_exit": true,
                    "rwkv_review_allow_same_day_review": true,
                    "rwkv_review_min_intervening_reviews": 3,
                    "rwkv_review_min_elapsed_secs": 300,
                    "rwkv_review_instant_order_enabled": true,
                    "rwkv_review_dynamic_preset_replay": true,
                    "rwkv_review_candidate_refresh_enabled": true,
                    "rwkv_review_first_review_elapsed_from_card_creation": true,
                    "rwkv_review_enforce_grade_order": true,
                },
                RWKV_FORK_FIELDS_KEY: {
                    "rwkv_review_enabled": false,
                    "rwkv_review_batch_size": 256,
                    "rwkv_review_refresh_interval": 3,
                    "rwkv_review_refresh_on_exit": false,
                    "rwkv_review_allow_same_day_review": false,
                    "rwkv_review_min_intervening_reviews": 1,
                    "rwkv_review_min_elapsed_secs": 60,
                    "rwkv_review_instant_order_enabled": false,
                    "rwkv_review_dynamic_preset_replay": false,
                    "rwkv_review_candidate_refresh_enabled": false,
                    "rwkv_review_first_review_elapsed_from_card_creation": false,
                    "rwkv_review_enforce_grade_order": false,
                },
            }))
            .unwrap(),
            rwkv_review_batch_size: 0,
            rwkv_review_refresh_interval: 0,
            ..Default::default()
        };

        restore_fork_fields_from_other(&mut config);

        assert!(!config.rwkv_review_enabled);
        assert_eq!(config.rwkv_review_batch_size, 256);
        assert_eq!(config.rwkv_review_refresh_interval, 3);
        assert!(!config.rwkv_review_refresh_on_exit);
        assert!(!config.rwkv_review_allow_same_day_review);
        assert_eq!(config.rwkv_review_min_intervening_reviews, 1);
        assert_eq!(config.rwkv_review_min_elapsed_secs, 60);
        assert!(!config.rwkv_review_instant_order_enabled);
        assert!(!config.rwkv_review_dynamic_preset_replay);
        assert!(!config.rwkv_review_candidate_refresh_enabled);
        assert!(!config.rwkv_review_first_review_elapsed_from_card_creation);
        assert!(!config.rwkv_review_enforce_grade_order);
    }
}
