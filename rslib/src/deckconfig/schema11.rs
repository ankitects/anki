// Copyright: Ankitects Pty Ltd and contributors
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

use super::DeckConfig;
use super::DeckConfigId;
use super::DeckConfigInner;
use super::NewCardInsertOrder;
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

    #[serde(default)]
    fsrs_weights: Vec<f32>,
    #[serde(default)]
    desired_retention: f32,
    #[serde(default)]
    stop_timer_on_answer: bool,
    #[serde(default)]
    seconds_to_show_question: f32,
    #[serde(default)]
    seconds_to_show_answer: f32,
    #[serde(default)]
    answer_action: AnswerAction,
    #[serde(default = "wait_for_audio_default")]
    wait_for_audio: bool,
    #[serde(default)]
    reschedule_fsrs_cards: bool,
    #[serde(default)]
    sm2_retention: f32,
    #[serde(default)]
    weight_search: String,

    #[serde(flatten)]
    other: HashMap<String, Value>,
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
pub enum NewCardOrderSchema11 {
    Random = 0,
    Due = 1,
}

impl Default for NewCardOrderSchema11 {
    fn default() -> Self {
        Self::Due
    }
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
            fsrs_weights: vec![],
            desired_retention: 0.9,
            reschedule_fsrs_cards: false,
            sm2_retention: 0.9,
            weight_search: "".to_string(),
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

        DeckConfig {
            id: c.id,
            name: c.name,
            mtime_secs: c.mtime,
            usn: c.usn,
            inner: DeckConfigInner {
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
                disable_autoplay: !c.autoplay,
                cap_answer_time_to_secs: c.max_taken.max(0) as u32,
                show_timer: c.timer != 0,
                stop_timer_on_answer: c.stop_timer_on_answer,
                seconds_to_show_question: c.seconds_to_show_question,
                seconds_to_show_answer: c.seconds_to_show_answer,
                answer_action: c.answer_action as i32,
                wait_for_audio: c.wait_for_audio,
                skip_question_when_replaying_answer: !c.replayq,
                bury_new: c.new.bury,
                bury_reviews: c.rev.bury,
                bury_interday_learning: c.bury_interday_learning,
                fsrs_weights: c.fsrs_weights,
                desired_retention: c.desired_retention,
                reschedule_fsrs_cards: c.reschedule_fsrs_cards,
                sm2_retention: c.sm2_retention,
                weight_search: c.weight_search,
                other: other_bytes,
            },
        }
    }
}

// latest schema -> schema 11
impl From<DeckConfig> for DeckConfSchema11 {
    fn from(c: DeckConfig) -> DeckConfSchema11 {
        // split extra json up
        let mut top_other: HashMap<String, Value>;
        let mut new_other = Default::default();
        let mut rev_other = Default::default();
        let mut lapse_other = Default::default();
        if c.inner.other.is_empty() {
            top_other = Default::default();
        } else {
            top_other = serde_json::from_slice(&c.inner.other).unwrap_or_default();
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
        let i = c.inner;
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
                0 => AnswerAction::BuryCard,
                1 => AnswerAction::AnswerAgain,
                3 => AnswerAction::AnswerHard,
                4 => AnswerAction::ShowReminder,
                _ => AnswerAction::AnswerGood,
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
            fsrs_weights: i.fsrs_weights,
            desired_retention: i.desired_retention,
            reschedule_fsrs_cards: i.reschedule_fsrs_cards,
            sm2_retention: i.sm2_retention,
            weight_search: i.weight_search,
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
    "desiredRetention",
    "stopTimerOnAnswer",
    "secondsToShowQuestion",
    "secondsToShowAnswer",
    "answerAction",
    "waitForAudio",
    "rescheduleFsrsCards",
    "sm2Retention",
    "weightSearch",
};

static RESERVED_DECKCONF_NEW_KEYS: Set<&'static str> = phf_set! {
    "order", "delays", "bury", "perDay", "initialFactor", "ints"
};

static RESERVED_DECKCONF_REV_KEYS: Set<&'static str> = phf_set! {
    "maxIvl", "hardFactor", "ease4", "ivlFct", "perDay", "bury"
};

static RESERVED_DECKCONF_LAPSE_KEYS: Set<&'static str> = phf_set! {
    "leechFails", "mult", "leechAction", "delays", "minInt"
};

#[cfg(test)]
mod test {
    use itertools::Itertools;
    use serde::de::IntoDeserializer;
    use serde_json::json;
    use serde_json::Value;

    use super::*;
    use crate::prelude::*;

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
}
