// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use phf::phf_set;
use phf::Set;
use serde::Deserialize as DeTrait;
use serde::Deserialize;
use serde::Deserializer;
use serde::Serialize;
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_json::Value;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;
use serde_tuple::Serialize_tuple;

use super::deck_config_inner_for_storage;
use super::restore_fork_fields_from_other;
use super::DeckConfig;
use super::DeckConfigId;
use super::DeckConfigInner;
use super::NewCardInsertOrder;
use super::DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW;
use super::DEFAULT_RWKV_REVIEW_BATCH_SIZE;
use super::DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER;
use super::DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION;
use super::DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS;
use super::DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS;
use super::DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD;
use super::DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL;
use super::INITIAL_EASE_FACTOR_THOUSANDS;
use crate::serde::default_on_invalid;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

fn wait_for_audio_default() -> bool {
    true
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DeckConfSchema11 {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: DeckConfigId,
    #[serde(rename = "mod", deserialize_with = "deserialize_number_from_string")]
    pub(crate) mtime: TimestampSecs,
    pub(crate) name: String,
    pub(crate) usn: Usn,
    max_taken: i32,
    autoplay: bool,
    #[serde(deserialize_with = "default_on_invalid")]
    timer: u8,
    #[serde(default)]
    replayq: bool,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) new: NewConfSchema11,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) rev: RevConfSchema11,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) lapse: LapseConfSchema11,
    #[serde(rename = "dyn", default, deserialize_with = "default_on_invalid")]
    dynamic: bool,

    // 2021 scheduler options: these were not in schema 11, but we need to persist them
    // so the settings are not lost on upgrade/downgrade.
    #[serde(default)]
    new_mix: i32,
    #[serde(default)]
    new_per_day_minimum: u32,
    #[serde(default)]
    interday_learning_mix: i32,
    #[serde(default)]
    review_order: i32,
    #[serde(default)]
    new_sort_order: i32,
    #[serde(default)]
    new_gather_priority: i32,
    #[serde(default)]
    bury_interday_learning: bool,

    #[serde(default, rename = "fsrsWeights")]
    fsrs_params_4: Vec<f32>,
    #[serde(default)]
    fsrs_params_5: Vec<f32>,
    #[serde(default)]
    fsrs_params_6: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_params_7: Vec<f32>,
    #[serde(
        default = "default_fsrs_minimum_interval_secs",
        skip_serializing_if = "is_default_fsrs_minimum_interval_secs"
    )]
    fsrs_minimum_interval_secs: u32,
    #[serde(default, skip_serializing_if = "is_false")]
    fsrs_dynamic_desired_retention_enabled: bool,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_params: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_weights: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_avg_drs: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_fixed_target_weights: Vec<f32>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    fsrs_dynamic_desired_retention_fixed_target_drs: Vec<f32>,
    #[serde(
        default = "default_dynamic_desired_retention_min",
        skip_serializing_if = "is_default_dynamic_desired_retention_min"
    )]
    fsrs_dynamic_desired_retention_min: f32,
    #[serde(
        default = "default_dynamic_desired_retention_max",
        skip_serializing_if = "is_default_dynamic_desired_retention_max"
    )]
    fsrs_dynamic_desired_retention_max: f32,
    #[serde(default, skip_serializing_if = "is_false")]
    fsrs_dynamic_desired_retention_clamp: bool,
    #[serde(default, skip_serializing_if = "is_default_fsrs_version")]
    fsrs_version: i32,
    #[serde(default)]
    desired_retention: f32,
    #[serde(default)]
    ignore_revlogs_before_date: String,
    #[serde(default, skip_serializing_if = "is_false")]
    rwkv_review_enabled: bool,
    #[serde(
        default = "default_rwkv_review_batch_size",
        skip_serializing_if = "is_default_or_zero_rwkv_review_batch_size"
    )]
    rwkv_review_batch_size: u32,
    #[serde(
        default = "default_rwkv_review_refresh_interval",
        skip_serializing_if = "is_default_or_zero_rwkv_review_refresh_interval"
    )]
    rwkv_review_refresh_interval: u32,
    #[serde(default, skip_serializing_if = "is_false")]
    rwkv_review_refresh_on_exit: bool,
    #[serde(
        default = "default_rwkv_review_allow_same_day_review",
        skip_serializing_if = "is_default_rwkv_review_allow_same_day_review"
    )]
    rwkv_review_allow_same_day_review: bool,
    #[serde(default, skip_serializing_if = "is_false")]
    same_day_reviews_ignore_review_limit: bool,
    #[serde(
        default = "default_rwkv_review_min_intervening_reviews",
        skip_serializing_if = "is_default_rwkv_review_min_intervening_reviews"
    )]
    rwkv_review_min_intervening_reviews: u32,
    #[serde(
        default = "default_rwkv_review_min_elapsed_secs",
        skip_serializing_if = "is_default_rwkv_review_min_elapsed_secs"
    )]
    rwkv_review_min_elapsed_secs: u32,
    #[serde(default, skip_serializing_if = "is_false")]
    rwkv_review_instant_order_enabled: bool,
    #[serde(default, skip_serializing_if = "is_false")]
    rwkv_review_dynamic_preset_replay: bool,
    #[serde(default, skip_serializing_if = "is_false")]
    rwkv_review_candidate_refresh_enabled: bool,
    #[serde(
        default = "default_rwkv_review_first_review_elapsed_from_card_creation",
        skip_serializing_if = "is_default_rwkv_review_first_review_elapsed_from_card_creation"
    )]
    rwkv_review_first_review_elapsed_from_card_creation: bool,
    #[serde(
        default = "default_rwkv_review_enforce_grade_order",
        skip_serializing_if = "is_default_rwkv_review_enforce_grade_order"
    )]
    rwkv_review_enforce_grade_order: bool,
    #[serde(default, skip_serializing_if = "is_zero_u32")]
    rwkv_review_minimum_reviews_per_day: u32,
    #[serde(default)]
    easy_days_percentages: Vec<f32>,
    #[serde(default)]
    stop_timer_on_answer: bool,
    #[serde(default)]
    seconds_to_show_question: f32,
    #[serde(default)]
    seconds_to_show_answer: f32,
    #[serde(default)]
    question_action: QuestionAction,
    #[serde(default)]
    answer_action: AnswerAction,
    #[serde(default = "wait_for_audio_default")]
    wait_for_audio: bool,
    #[serde(default)]
    /// historical retention
    sm2_retention: f32,
    #[serde(default, rename = "weightSearch")]
    param_search: String,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

fn default_dynamic_desired_retention_min() -> f32 {
    0.30
}

fn default_dynamic_desired_retention_max() -> f32 {
    0.995
}

fn is_false(value: &bool) -> bool {
    !value
}

const DEFAULT_FSRS_MINIMUM_INTERVAL_SECS: u32 = 1;

fn default_fsrs_minimum_interval_secs() -> u32 {
    DEFAULT_FSRS_MINIMUM_INTERVAL_SECS
}

fn is_default_fsrs_minimum_interval_secs(value: &u32) -> bool {
    *value == DEFAULT_FSRS_MINIMUM_INTERVAL_SECS
}

fn default_rwkv_review_batch_size() -> u32 {
    DEFAULT_RWKV_REVIEW_BATCH_SIZE
}

fn is_default_or_zero_rwkv_review_batch_size(value: &u32) -> bool {
    *value == 0 || *value == default_rwkv_review_batch_size()
}

fn default_rwkv_review_refresh_interval() -> u32 {
    DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL
}

fn is_default_or_zero_rwkv_review_refresh_interval(value: &u32) -> bool {
    *value == 0 || *value == default_rwkv_review_refresh_interval()
}

fn default_rwkv_review_allow_same_day_review() -> bool {
    DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW
}

fn is_default_rwkv_review_allow_same_day_review(value: &bool) -> bool {
    *value == default_rwkv_review_allow_same_day_review()
}

fn default_rwkv_review_min_intervening_reviews() -> u32 {
    DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS
}

fn is_default_rwkv_review_min_intervening_reviews(value: &u32) -> bool {
    *value == default_rwkv_review_min_intervening_reviews()
}

fn default_rwkv_review_min_elapsed_secs() -> u32 {
    DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS
}

fn is_default_rwkv_review_min_elapsed_secs(value: &u32) -> bool {
    *value == default_rwkv_review_min_elapsed_secs()
}

fn default_rwkv_review_first_review_elapsed_from_card_creation() -> bool {
    DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION
}

fn is_default_rwkv_review_first_review_elapsed_from_card_creation(value: &bool) -> bool {
    *value == default_rwkv_review_first_review_elapsed_from_card_creation()
}

fn default_rwkv_review_enforce_grade_order() -> bool {
    DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER
}

fn is_default_rwkv_review_enforce_grade_order(value: &bool) -> bool {
    *value == default_rwkv_review_enforce_grade_order()
}

fn is_zero_u32(value: &u32) -> bool {
    *value == 0
}

fn is_default_dynamic_desired_retention_min(value: &f32) -> bool {
    (*value - default_dynamic_desired_retention_min()).abs() <= f32::EPSILON
}

fn is_default_dynamic_desired_retention_max(value: &f32) -> bool {
    (*value - default_dynamic_desired_retention_max()).abs() <= f32::EPSILON
}

fn is_default_fsrs_version(value: &i32) -> bool {
    *value == 0
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, Clone)]
#[repr(u8)]
#[derive(Default)]
pub enum QuestionAction {
    #[default]
    ShowAnswer = 0,
    ShowReminder = 1,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, Clone)]
#[repr(u8)]
#[derive(Default)]
pub enum AnswerAction {
    #[default]
    BuryCard = 0,
    AnswerAgain = 1,
    AnswerGood = 2,
    AnswerHard = 3,
    ShowReminder = 4,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NewConfSchema11 {
    #[serde(default)]
    bury: bool,
    #[serde(deserialize_with = "default_on_invalid")]
    delays: Vec<f32>,
    initial_factor: u16,
    #[serde(deserialize_with = "deserialize_new_intervals")]
    ints: NewCardIntervals,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) order: NewCardOrderSchema11,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) per_day: u32,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize_tuple, Debug, PartialEq, Eq, Clone)]
pub struct NewCardIntervals {
    good: u16,
    easy: u16,
    _unused: u16,
}

impl Default for NewCardIntervals {
    fn default() -> Self {
        Self {
            good: 1,
            easy: 4,
            _unused: 0,
        }
    }
}

/// This extra logic is required because AnkiDroid's options screen was creating
/// a 2 element array instead of a 3 element one.
fn deserialize_new_intervals<'de, D>(deserializer: D) -> Result<NewCardIntervals, D::Error>
where
    D: Deserializer<'de>,
{
    let vals: Result<Vec<u16>, _> = DeTrait::deserialize(deserializer);
    Ok(vals
        .ok()
        .and_then(|vals| {
            if vals.len() >= 2 {
                Some(NewCardIntervals {
                    good: vals[0],
                    easy: vals[1],
                    _unused: 0,
                })
            } else {
                None
            }
        })
        .unwrap_or_default())
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, Clone)]
#[repr(u8)]
#[derive(Default)]
pub enum NewCardOrderSchema11 {
    Random = 0,
    #[default]
    Due = 1,
}

fn hard_factor_default() -> f32 {
    1.2
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct RevConfSchema11 {
    #[serde(default)]
    bury: bool,
    ease4: f32,
    ivl_fct: f32,
    max_ivl: u32,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) per_day: u32,
    #[serde(default = "hard_factor_default")]
    hard_factor: f32,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, Clone)]
#[repr(u8)]
#[derive(Default)]
pub enum LeechAction {
    Suspend = 0,
    #[default]
    TagOnly = 1,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LapseConfSchema11 {
    #[serde(deserialize_with = "default_on_invalid")]
    delays: Vec<f32>,
    #[serde(deserialize_with = "default_on_invalid")]
    leech_action: LeechAction,
    leech_fails: u32,
    #[serde(default, skip_serializing_if = "is_false")]
    leech_only_if_young: bool,
    min_int: u32,
    mult: f32,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

impl Default for RevConfSchema11 {
    fn default() -> Self {
        RevConfSchema11 {
            bury: false,
            ease4: 1.3,
            ivl_fct: 1.0,
            max_ivl: 36500,
            per_day: 200,
            hard_factor: 1.2,
            other: Default::default(),
        }
    }
}

impl Default for NewConfSchema11 {
    fn default() -> Self {
        NewConfSchema11 {
            bury: false,
            delays: vec![1.0, 10.0],
            initial_factor: INITIAL_EASE_FACTOR_THOUSANDS,
            ints: NewCardIntervals::default(),
            order: NewCardOrderSchema11::default(),
            per_day: 20,
            other: Default::default(),
        }
    }
}

impl Default for LapseConfSchema11 {
    fn default() -> Self {
        LapseConfSchema11 {
            delays: vec![10.0],
            leech_action: LeechAction::default(),
            leech_fails: 8,
            leech_only_if_young: false,
            min_int: 1,
            mult: 0.0,
            other: Default::default(),
        }
    }
}

impl Default for DeckConfSchema11 {
    fn default() -> Self {
        DeckConfSchema11 {
            id: DeckConfigId(0),
            mtime: TimestampSecs(0),
            name: "Default".to_string(),
            usn: Usn(0),
            max_taken: 60,
            autoplay: true,
            timer: 0,
            stop_timer_on_answer: false,
            seconds_to_show_question: 0.0,
            seconds_to_show_answer: 0.0,
            question_action: QuestionAction::ShowAnswer,
            answer_action: AnswerAction::BuryCard,
            wait_for_audio: true,
            replayq: true,
            dynamic: false,
            new: Default::default(),
            rev: Default::default(),
            lapse: Default::default(),
            other: Default::default(),
            new_mix: 0,
            new_per_day_minimum: 0,
            interday_learning_mix: 0,
            review_order: 0,
            new_sort_order: 0,
            new_gather_priority: 0,
            bury_interday_learning: false,
            fsrs_params_4: vec![],
            fsrs_params_5: vec![],
            fsrs_params_6: vec![],
            fsrs_params_7: vec![],
            fsrs_minimum_interval_secs: 1,
            fsrs_dynamic_desired_retention_enabled: false,
            fsrs_dynamic_desired_retention_params: vec![],
            fsrs_dynamic_desired_retention_weights: vec![],
            fsrs_dynamic_desired_retention_avg_drs: vec![],
            fsrs_dynamic_desired_retention_fsrs_eq_weights: vec![],
            fsrs_dynamic_desired_retention_fsrs_eq_drs: vec![],
            fsrs_dynamic_desired_retention_fixed_target_weights: vec![],
            fsrs_dynamic_desired_retention_fixed_target_drs: vec![],
            fsrs_dynamic_desired_retention_min: default_dynamic_desired_retention_min(),
            fsrs_dynamic_desired_retention_max: default_dynamic_desired_retention_max(),
            fsrs_dynamic_desired_retention_clamp: false,
            fsrs_version: 0,
            desired_retention: 0.9,
            sm2_retention: 0.9,
            param_search: "".to_string(),
            ignore_revlogs_before_date: "".to_string(),
            rwkv_review_enabled: false,
            rwkv_review_batch_size: DEFAULT_RWKV_REVIEW_BATCH_SIZE,
            rwkv_review_refresh_interval: DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL,
            rwkv_review_refresh_on_exit: false,
            rwkv_review_allow_same_day_review: DEFAULT_RWKV_REVIEW_ALLOW_SAME_DAY_REVIEW,
            same_day_reviews_ignore_review_limit: false,
            rwkv_review_min_intervening_reviews: DEFAULT_RWKV_REVIEW_MIN_INTERVENING_REVIEWS,
            rwkv_review_min_elapsed_secs: DEFAULT_RWKV_REVIEW_MIN_ELAPSED_SECS,
            rwkv_review_instant_order_enabled: false,
            rwkv_review_dynamic_preset_replay: false,
            rwkv_review_candidate_refresh_enabled: false,
            rwkv_review_first_review_elapsed_from_card_creation:
                DEFAULT_RWKV_REVIEW_FIRST_REVIEW_ELAPSED_FROM_CARD_CREATION,
            rwkv_review_enforce_grade_order: DEFAULT_RWKV_REVIEW_ENFORCE_GRADE_ORDER,
            rwkv_review_minimum_reviews_per_day: 0,
            easy_days_percentages: vec![1.0; 7],
        }
    }
}

// schema11 -> schema15

impl From<DeckConfSchema11> for DeckConfig {
    fn from(mut c: DeckConfSchema11) -> DeckConfig {
        // merge any json stored in new/rev/lapse into top level
        if !c.new.other.is_empty() {
            if let Ok(val) = serde_json::to_value(c.new.other) {
                c.other.insert("new".into(), val);
            }
        }
        if !c.rev.other.is_empty() {
            if let Ok(val) = serde_json::to_value(c.rev.other) {
                c.other.insert("rev".into(), val);
            }
        }
        if !c.lapse.other.is_empty() {
            if let Ok(val) = serde_json::to_value(c.lapse.other) {
                c.other.insert("lapse".into(), val);
            }
        }
        let other_bytes = if c.other.is_empty() {
            vec![]
        } else {
            serde_json::to_vec(&c.other).unwrap_or_default()
        };

        let mut inner = DeckConfigInner {
            learn_steps: c.new.delays,
            relearn_steps: c.lapse.delays,
            new_per_day: c.new.per_day,
            reviews_per_day: c.rev.per_day,
            new_per_day_minimum: c.new_per_day_minimum,
            initial_ease: (c.new.initial_factor as f32) / 1000.0,
            easy_multiplier: c.rev.ease4,
            hard_multiplier: c.rev.hard_factor,
            lapse_multiplier: c.lapse.mult,
            interval_multiplier: c.rev.ivl_fct,
            maximum_review_interval: c.rev.max_ivl,
            minimum_lapse_interval: c.lapse.min_int,
            graduating_interval_good: c.new.ints.good as u32,
            graduating_interval_easy: c.new.ints.easy as u32,
            new_card_insert_order: match c.new.order {
                NewCardOrderSchema11::Random => NewCardInsertOrder::Random,
                NewCardOrderSchema11::Due => NewCardInsertOrder::Due,
            } as i32,
            new_card_gather_priority: c.new_gather_priority,
            new_card_sort_order: c.new_sort_order,
            review_order: c.review_order,
            new_mix: c.new_mix,
            interday_learning_mix: c.interday_learning_mix,
            leech_action: c.lapse.leech_action as i32,
            leech_threshold: c.lapse.leech_fails,
            leech_only_if_young: c.lapse.leech_only_if_young,
            rwkv_review_enabled: c.rwkv_review_enabled,
            rwkv_review_batch_size: c.rwkv_review_batch_size,
            rwkv_review_refresh_interval: c.rwkv_review_refresh_interval,
            rwkv_review_refresh_on_exit: c.rwkv_review_refresh_on_exit,
            rwkv_review_allow_same_day_review: c.rwkv_review_allow_same_day_review,
            same_day_reviews_ignore_review_limit: c.same_day_reviews_ignore_review_limit,
            rwkv_review_min_intervening_reviews: c.rwkv_review_min_intervening_reviews,
            rwkv_review_min_elapsed_secs: c.rwkv_review_min_elapsed_secs,
            rwkv_review_instant_order_enabled: c.rwkv_review_instant_order_enabled,
            rwkv_review_dynamic_preset_replay: c.rwkv_review_dynamic_preset_replay,
            rwkv_review_candidate_refresh_enabled: c.rwkv_review_candidate_refresh_enabled,
            rwkv_review_precompute_next_card: DEFAULT_RWKV_REVIEW_PRECOMPUTE_NEXT_CARD,
            rwkv_review_first_review_elapsed_from_card_creation: c
                .rwkv_review_first_review_elapsed_from_card_creation,
            rwkv_review_enforce_grade_order: c.rwkv_review_enforce_grade_order,
            rwkv_review_minimum_reviews_per_day: c.rwkv_review_minimum_reviews_per_day,
            disable_autoplay: !c.autoplay,
            cap_answer_time_to_secs: c.max_taken.max(0) as u32,
            show_timer: c.timer != 0,
            stop_timer_on_answer: c.stop_timer_on_answer,
            seconds_to_show_question: c.seconds_to_show_question,
            seconds_to_show_answer: c.seconds_to_show_answer,
            question_action: c.question_action as i32,
            answer_action: c.answer_action as i32,
            wait_for_audio: c.wait_for_audio,
            skip_question_when_replaying_answer: !c.replayq,
            bury_new: c.new.bury,
            bury_reviews: c.rev.bury,
            bury_interday_learning: c.bury_interday_learning,
            fsrs_params_4: c.fsrs_params_4,
            fsrs_params_5: c.fsrs_params_5,
            fsrs_params_6: c.fsrs_params_6,
            fsrs_params_7: c.fsrs_params_7,
            fsrs_minimum_interval_secs: c.fsrs_minimum_interval_secs,
            fsrs_dynamic_desired_retention_enabled: c.fsrs_dynamic_desired_retention_enabled,
            fsrs_dynamic_desired_retention_params: c.fsrs_dynamic_desired_retention_params,
            fsrs_dynamic_desired_retention_weights: c.fsrs_dynamic_desired_retention_weights,
            fsrs_dynamic_desired_retention_avg_drs: c.fsrs_dynamic_desired_retention_avg_drs,
            fsrs_dynamic_desired_retention_fsrs_eq_weights: c
                .fsrs_dynamic_desired_retention_fsrs_eq_weights,
            fsrs_dynamic_desired_retention_fsrs_eq_drs: c
                .fsrs_dynamic_desired_retention_fsrs_eq_drs,
            fsrs_dynamic_desired_retention_fixed_target_weights: c
                .fsrs_dynamic_desired_retention_fixed_target_weights,
            fsrs_dynamic_desired_retention_fixed_target_drs: c
                .fsrs_dynamic_desired_retention_fixed_target_drs,
            fsrs_dynamic_desired_retention_min: c.fsrs_dynamic_desired_retention_min,
            fsrs_dynamic_desired_retention_max: c.fsrs_dynamic_desired_retention_max,
            fsrs_dynamic_desired_retention_clamp: c.fsrs_dynamic_desired_retention_clamp,
            fsrs_version: c.fsrs_version,
            ignore_revlogs_before_date: c.ignore_revlogs_before_date,
            easy_days_percentages: c.easy_days_percentages,
            review_fuzz_base: None,
            review_fuzz_factor_short: None,
            review_fuzz_factor_mid: None,
            review_fuzz_factor_long: None,
            review_fuzz_enabled: None,
            desired_retention: c.desired_retention,
            historical_retention: c.sm2_retention,
            param_search: c.param_search,
            other: other_bytes,
        };
        restore_fork_fields_from_other(&mut inner);

        DeckConfig {
            id: c.id,
            name: c.name,
            mtime_secs: c.mtime,
            usn: c.usn,
            inner,
        }
    }
}

// latest schema -> schema 11
impl From<DeckConfig> for DeckConfSchema11 {
    fn from(c: DeckConfig) -> DeckConfSchema11 {
        let same_day_reviews_ignore_review_limit = c.inner.same_day_reviews_ignore_review_limit;
        let rwkv_review_enforce_grade_order = c.inner.rwkv_review_enforce_grade_order;
        let rwkv_review_minimum_reviews_per_day = c.inner.rwkv_review_minimum_reviews_per_day;
        let i = deck_config_inner_for_storage(&c.inner);
        // split extra json up
        let mut top_other: HashMap<String, Value>;
        let mut new_other = Default::default();
        let mut rev_other = Default::default();
        let mut lapse_other = Default::default();
        if i.other.is_empty() {
            top_other = Default::default();
        } else {
            top_other = serde_json::from_slice(&i.other).unwrap_or_default();
            if let Some(new) = top_other.remove("new") {
                let val: HashMap<String, Value> = serde_json::from_value(new).unwrap_or_default();
                new_other = val;
                new_other.retain(|k, _v| !RESERVED_DECKCONF_NEW_KEYS.contains(k))
            }
            if let Some(rev) = top_other.remove("rev") {
                let val: HashMap<String, Value> = serde_json::from_value(rev).unwrap_or_default();
                rev_other = val;
                rev_other.retain(|k, _v| !RESERVED_DECKCONF_REV_KEYS.contains(k))
            }
            if let Some(lapse) = top_other.remove("lapse") {
                let val: HashMap<String, Value> = serde_json::from_value(lapse).unwrap_or_default();
                lapse_other = val;
                lapse_other.retain(|k, _v| !RESERVED_DECKCONF_LAPSE_KEYS.contains(k))
            }
            top_other.retain(|k, _v| !RESERVED_DECKCONF_KEYS.contains(k));
        }
        let new_order = i.new_card_insert_order();
        DeckConfSchema11 {
            id: c.id,
            mtime: c.mtime_secs,
            name: c.name,
            usn: c.usn,
            max_taken: i.cap_answer_time_to_secs as i32,
            autoplay: !i.disable_autoplay,
            timer: i.show_timer.into(),
            stop_timer_on_answer: i.stop_timer_on_answer,
            seconds_to_show_question: i.seconds_to_show_question,
            seconds_to_show_answer: i.seconds_to_show_answer,
            answer_action: match i.answer_action {
                1 => AnswerAction::AnswerAgain,
                2 => AnswerAction::AnswerGood,
                3 => AnswerAction::AnswerHard,
                4 => AnswerAction::ShowReminder,
                _ => AnswerAction::BuryCard,
            },
            question_action: match i.question_action {
                1 => QuestionAction::ShowReminder,
                _ => QuestionAction::ShowAnswer,
            },
            wait_for_audio: i.wait_for_audio,
            replayq: !i.skip_question_when_replaying_answer,
            dynamic: false,
            new: NewConfSchema11 {
                bury: i.bury_new,
                delays: i.learn_steps,
                initial_factor: (i.initial_ease * 1000.0) as u16,
                ints: NewCardIntervals {
                    good: i.graduating_interval_good as u16,
                    easy: i.graduating_interval_easy as u16,
                    _unused: 0,
                },
                order: match new_order {
                    NewCardInsertOrder::Random => NewCardOrderSchema11::Random,
                    NewCardInsertOrder::Due => NewCardOrderSchema11::Due,
                },
                per_day: i.new_per_day,
                other: new_other,
            },
            rev: RevConfSchema11 {
                bury: i.bury_reviews,
                ease4: i.easy_multiplier,
                ivl_fct: i.interval_multiplier,
                max_ivl: i.maximum_review_interval,
                per_day: i.reviews_per_day,
                hard_factor: i.hard_multiplier,
                other: rev_other,
            },
            lapse: LapseConfSchema11 {
                delays: i.relearn_steps,
                leech_action: match i.leech_action {
                    1 => LeechAction::TagOnly,
                    _ => LeechAction::Suspend,
                },
                leech_fails: i.leech_threshold,
                leech_only_if_young: i.leech_only_if_young,
                min_int: i.minimum_lapse_interval,
                mult: i.lapse_multiplier,
                other: lapse_other,
            },
            other: top_other,
            new_mix: i.new_mix,
            new_per_day_minimum: i.new_per_day_minimum,
            interday_learning_mix: i.interday_learning_mix,
            review_order: i.review_order,
            new_sort_order: i.new_card_sort_order,
            new_gather_priority: i.new_card_gather_priority,
            bury_interday_learning: i.bury_interday_learning,
            fsrs_params_4: i.fsrs_params_4,
            fsrs_params_5: i.fsrs_params_5,
            fsrs_params_6: i.fsrs_params_6,
            fsrs_params_7: i.fsrs_params_7,
            fsrs_minimum_interval_secs: i.fsrs_minimum_interval_secs,
            fsrs_dynamic_desired_retention_enabled: i.fsrs_dynamic_desired_retention_enabled,
            fsrs_dynamic_desired_retention_params: i.fsrs_dynamic_desired_retention_params,
            fsrs_dynamic_desired_retention_weights: i.fsrs_dynamic_desired_retention_weights,
            fsrs_dynamic_desired_retention_avg_drs: i.fsrs_dynamic_desired_retention_avg_drs,
            fsrs_dynamic_desired_retention_fsrs_eq_weights: i
                .fsrs_dynamic_desired_retention_fsrs_eq_weights,
            fsrs_dynamic_desired_retention_fsrs_eq_drs: i
                .fsrs_dynamic_desired_retention_fsrs_eq_drs,
            fsrs_dynamic_desired_retention_fixed_target_weights: i
                .fsrs_dynamic_desired_retention_fixed_target_weights,
            fsrs_dynamic_desired_retention_fixed_target_drs: i
                .fsrs_dynamic_desired_retention_fixed_target_drs,
            fsrs_dynamic_desired_retention_min: i.fsrs_dynamic_desired_retention_min,
            fsrs_dynamic_desired_retention_max: i.fsrs_dynamic_desired_retention_max,
            fsrs_dynamic_desired_retention_clamp: i.fsrs_dynamic_desired_retention_clamp,
            fsrs_version: i.fsrs_version,
            desired_retention: i.desired_retention,
            sm2_retention: i.historical_retention,
            param_search: i.param_search,
            ignore_revlogs_before_date: i.ignore_revlogs_before_date,
            rwkv_review_enabled: i.rwkv_review_enabled,
            rwkv_review_batch_size: i.rwkv_review_batch_size,
            rwkv_review_refresh_interval: i.rwkv_review_refresh_interval,
            rwkv_review_refresh_on_exit: i.rwkv_review_refresh_on_exit,
            rwkv_review_allow_same_day_review: i.rwkv_review_allow_same_day_review,
            same_day_reviews_ignore_review_limit,
            rwkv_review_min_intervening_reviews: i.rwkv_review_min_intervening_reviews,
            rwkv_review_min_elapsed_secs: i.rwkv_review_min_elapsed_secs,
            rwkv_review_instant_order_enabled: i.rwkv_review_instant_order_enabled,
            rwkv_review_dynamic_preset_replay: i.rwkv_review_dynamic_preset_replay,
            rwkv_review_candidate_refresh_enabled: i.rwkv_review_candidate_refresh_enabled,
            rwkv_review_first_review_elapsed_from_card_creation: i
                .rwkv_review_first_review_elapsed_from_card_creation,
            rwkv_review_enforce_grade_order,
            rwkv_review_minimum_reviews_per_day,
            easy_days_percentages: i.easy_days_percentages,
        }
    }
}

static RESERVED_DECKCONF_KEYS: Set<&'static str> = phf_set! {
    "id",
    "newSortOrder",
    "replayq",
    "newPerDayMinimum",
    "usn",
    "autoplay",
    "dyn",
    "maxTaken",
    "reviewOrder",
    "buryInterdayLearning",
    "newMix",
    "mod",
    "timer",
    "name",
    "interdayLearningMix",
    "newGatherPriority",
    "fsrsWeights",
    "fsrsParams5",
    "fsrsParams6",
    "fsrsParams7",
    "fsrsMinimumIntervalSecs",
    "fsrsDynamicDesiredRetentionEnabled",
    "fsrsDynamicDesiredRetentionParams",
    "fsrsDynamicDesiredRetentionWeights",
    "fsrsDynamicDesiredRetentionAvgDrs",
    "fsrsDynamicDesiredRetentionFsrsEqWeights",
    "fsrsDynamicDesiredRetentionFsrsEqDrs",
    "fsrsDynamicDesiredRetentionMin",
    "fsrsDynamicDesiredRetentionMax",
    "fsrsVersion",
    "desiredRetention",
    "stopTimerOnAnswer",
    "secondsToShowQuestion",
    "secondsToShowAnswer",
    "questionAction",
    "answerAction",
    "waitForAudio",
    "sm2Retention",
    "weightSearch",
    "ignoreRevlogsBeforeDate",
    "rwkvReviewEnabled",
    "rwkvReviewBatchSize",
    "rwkvReviewRefreshInterval",
    "rwkvReviewRefreshOnExit",
    "rwkvReviewAllowSameDayReview",
    "sameDayReviewsIgnoreReviewLimit",
    "rwkvReviewInstantOrderEnabled",
    "rwkvReviewDynamicPresetReplay",
    "rwkvReviewCandidateRefreshEnabled",
    "rwkvReviewEnforceGradeOrder",
    "rwkvReviewMinimumReviewsPerDay",
    "rwkvReviewPresetTagStateEnabled",
    "rwkvReviewJapaneseFeatureStateEnabled",
    "rwkvReviewJapaneseKanjiField",
    "rwkvReviewJapaneseReadingField",
    "rwkvReviewSelfCorrectionEnabled",
    "easyDaysPercentages",
};

static RESERVED_DECKCONF_NEW_KEYS: Set<&'static str> = phf_set! {
    "order", "delays", "bury", "perDay", "initialFactor", "ints"
};

static RESERVED_DECKCONF_REV_KEYS: Set<&'static str> = phf_set! {
    "maxIvl", "hardFactor", "ease4", "ivlFct", "perDay", "bury"
};

static RESERVED_DECKCONF_LAPSE_KEYS: Set<&'static str> = phf_set! {
    "leechFails", "mult", "leechAction", "leechOnlyIfYoung", "delays", "minInt"
};

#[cfg(test)]
mod test {
    use itertools::Itertools;
    use prost::Message;
    use serde::de::IntoDeserializer;
    use serde_json::json;
    use serde_json::Value;

    use super::*;
    use crate::deckconfig::ReviewMix;
    use crate::prelude::*;

    #[test]
    fn deck_options_metadata_survives_unsupported_preset_edits() -> Result<()> {
        for mix in [
            ReviewMix::BeforeReviews,
            ReviewMix::AfterReviews,
            ReviewMix::MixWithReviews,
        ] {
            let metadata = json!({
                "intraday_learning_mix": mix as i32,
                "future_option": "preserve",
            });
            let mut wire = serde_json::to_value(DeckConfSchema11::default())?;
            wire["jschoreels.deckoptions"] = metadata.clone();

            // The sync-format reader has no field for these options: it carries
            // them as opaque extra data, including when known settings are edited.
            let mut unsupported: DeckConfSchema11 = serde_json::from_value(wire)?;
            unsupported.name = "Edited on another client".into();
            unsupported.rev.per_day = 123;
            let wire = serde_json::to_value(unsupported)?;
            assert_eq!(wire["jschoreels.deckoptions"], metadata);

            let mut restored = DeckConfig::from(serde_json::from_value::<DeckConfSchema11>(wire)?);
            restored.inner = DeckConfigInner::decode(
                deck_config_inner_for_storage(&restored.inner)
                    .encode_to_vec()
                    .as_slice(),
            )?;
            assert_eq!(restored.intraday_learning_mix(), mix);
            assert_eq!(restored.name, "Edited on another client");
            assert_eq!(restored.inner.reviews_per_day, 123);
            let wire = serde_json::to_value(DeckConfSchema11::from(restored))?;
            assert_eq!(wire["jschoreels.deckoptions"], metadata);
        }
        Ok(())
    }

    #[test]
    fn all_reserved_fields_are_removed() -> Result<()> {
        let key_source = DeckConfSchema11::default();
        let mut config = DeckConfig::default();
        let empty: &[&String] = &[];

        config.inner.other = serde_json::to_vec(&key_source)?;
        let s11 = DeckConfSchema11::from(config);
        assert_eq!(&s11.other.keys().collect_vec(), empty);
        assert_eq!(&s11.new.other.keys().collect_vec(), empty);
        assert_eq!(&s11.rev.other.keys().collect_vec(), empty);
        assert_eq!(&s11.lapse.other.keys().collect_vec(), empty);

        Ok(())
    }

    #[test]
    fn new_intervals() {
        let decode = |value: Value| -> NewCardIntervals {
            deserialize_new_intervals(value.into_deserializer()).unwrap()
        };
        assert_eq!(
            decode(json!([2, 4, 6])),
            NewCardIntervals {
                good: 2,
                easy: 4,
                _unused: 0
            }
        );
        assert_eq!(
            decode(json!([3, 9])),
            NewCardIntervals {
                good: 3,
                easy: 9,
                _unused: 0
            }
        );
        // invalid input will yield defaults
        assert_eq!(
            decode(json!([4])),
            NewCardIntervals {
                good: 1,
                easy: 4,
                _unused: 0
            }
        );
        assert_eq!(
            decode(json!([-5, 4, 3])),
            NewCardIntervals {
                good: 1,
                easy: 4,
                _unused: 0
            }
        );
    }

    #[test]
    fn rwkv_batch_size_defaults_and_omits_default() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewBatchSize").is_none());

        let decoded: DeckConfSchema11 = serde_json::from_value(serialized)?;
        assert_eq!(
            decoded.rwkv_review_batch_size,
            DEFAULT_RWKV_REVIEW_BATCH_SIZE
        );

        Ok(())
    }

    #[test]
    fn rwkv_precompute_setting_survives_schema11_roundtrip() -> Result<()> {
        for enabled in [true, false] {
            let mut config = DeckConfig::default();
            config.inner.rwkv_review_precompute_next_card = enabled;
            let encoded = serde_json::to_value(DeckConfSchema11::from(config))?;
            let decoded: DeckConfSchema11 = serde_json::from_value(encoded)?;
            assert_eq!(
                DeckConfig::from(decoded)
                    .inner
                    .rwkv_review_precompute_next_card,
                enabled
            );
        }
        Ok(())
    }

    #[test]
    fn non_default_rwkv_batch_size_serializes_for_legacy_json() -> Result<()> {
        let config = DeckConfSchema11 {
            rwkv_review_batch_size: 1024,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewBatchSize"], json!(1024));

        Ok(())
    }

    #[test]
    fn rwkv_refresh_interval_defaults_and_omits_default() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewRefreshInterval").is_none());

        let decoded: DeckConfSchema11 = serde_json::from_value(serialized)?;
        assert_eq!(
            decoded.rwkv_review_refresh_interval,
            DEFAULT_RWKV_REVIEW_REFRESH_INTERVAL
        );

        Ok(())
    }

    #[test]
    fn non_default_rwkv_refresh_interval_serializes_for_legacy_json() -> Result<()> {
        let config = DeckConfSchema11 {
            rwkv_review_refresh_interval: 5,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewRefreshInterval"], json!(5));

        Ok(())
    }

    #[test]
    fn rwkv_refresh_on_exit_omits_default_and_serializes_enabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewRefreshOnExit").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_refresh_on_exit: true,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewRefreshOnExit"], json!(true));

        Ok(())
    }

    #[test]
    fn rwkv_allow_same_day_review_omits_default_and_serializes_disabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewAllowSameDayReview").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_allow_same_day_review: false,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewAllowSameDayReview"], json!(false));

        Ok(())
    }

    #[test]
    fn same_day_reviews_ignore_review_limit_omits_default_and_serializes_enabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("sameDayReviewsIgnoreReviewLimit").is_none());

        let config = DeckConfSchema11 {
            same_day_reviews_ignore_review_limit: true,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["sameDayReviewsIgnoreReviewLimit"], json!(true));

        Ok(())
    }

    #[test]
    fn rwkv_repeat_guards_omit_default_and_serialize_zero() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewMinInterveningReviews").is_none());
        assert!(serialized.get("rwkvReviewMinElapsedSecs").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_min_intervening_reviews: 0,
            rwkv_review_min_elapsed_secs: 0,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewMinInterveningReviews"], json!(0));
        assert_eq!(serialized["rwkvReviewMinElapsedSecs"], json!(0));

        Ok(())
    }

    #[test]
    fn rwkv_instant_order_omits_default_and_serializes_enabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewInstantOrderEnabled").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_instant_order_enabled: true,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewInstantOrderEnabled"], json!(true));

        Ok(())
    }

    #[test]
    fn rwkv_minimum_reviews_omits_default_and_serializes_nonzero() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewMinimumReviewsPerDay").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_minimum_reviews_per_day: 42,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewMinimumReviewsPerDay"], json!(42));

        Ok(())
    }

    #[test]
    fn rwkv_dynamic_preset_replay_omits_default_and_serializes_enabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewDynamicPresetReplay").is_none());

        let config = DeckConfSchema11 {
            rwkv_review_dynamic_preset_replay: true,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewDynamicPresetReplay"], json!(true));

        Ok(())
    }

    #[test]
    fn rwkv_candidate_refresh_omits_default_and_serializes_enabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized
            .get("rwkvReviewCandidateRefreshEnabled")
            .is_none());

        let config = DeckConfSchema11 {
            rwkv_review_candidate_refresh_enabled: true,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(serialized["rwkvReviewCandidateRefreshEnabled"], json!(true));

        Ok(())
    }

    #[test]
    fn rwkv_first_review_elapsed_omits_default_and_serializes_disabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized
            .get("rwkvReviewFirstReviewElapsedFromCardCreation")
            .is_none());

        let config = DeckConfSchema11 {
            rwkv_review_first_review_elapsed_from_card_creation: false,
            ..DeckConfSchema11::default()
        };

        let serialized = serde_json::to_value(config)?;
        assert_eq!(
            serialized["rwkvReviewFirstReviewElapsedFromCardCreation"],
            json!(false)
        );

        Ok(())
    }

    #[test]
    fn rwkv_enforce_grade_order_omits_default_and_serializes_disabled() -> Result<()> {
        let serialized = serde_json::to_value(DeckConfSchema11::default())?;
        assert!(serialized.get("rwkvReviewEnforceGradeOrder").is_none());

        let default_config = DeckConfSchema11::from(DeckConfig::default());
        let serialized = serde_json::to_value(default_config)?;
        assert!(serialized.get("rwkvReviewEnforceGradeOrder").is_none());

        let mut disabled_config = DeckConfig::default();
        disabled_config.inner.rwkv_review_enforce_grade_order = false;
        let serialized = serde_json::to_value(DeckConfSchema11::from(disabled_config))?;
        assert_eq!(serialized["rwkvReviewEnforceGradeOrder"], json!(false));

        Ok(())
    }
}
