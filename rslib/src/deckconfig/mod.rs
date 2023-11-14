// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod schema11;
mod service;
pub(crate) mod undo;
mod update;

pub use anki_proto::deck_config::deck_config::config::AnswerAction;
pub use anki_proto::deck_config::deck_config::config::LeechAction;
pub use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
pub use anki_proto::deck_config::deck_config::config::NewCardInsertOrder;
pub use anki_proto::deck_config::deck_config::config::NewCardSortOrder;
pub use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
pub use anki_proto::deck_config::deck_config::config::ReviewMix;
pub use anki_proto::deck_config::deck_config::Config as DeckConfigInner;
pub use schema11::DeckConfSchema11;
pub use schema11::NewCardOrderSchema11;
pub use update::UpdateDeckConfigsRequest;

/// Old deck config and cards table store 250% as 2500.
pub(crate) const INITIAL_EASE_FACTOR_THOUSANDS: u16 = (INITIAL_EASE_FACTOR * 1000.0) as u16;

use crate::define_newtype;
use crate::prelude::*;
use crate::scheduler::states::review::INITIAL_EASE_FACTOR;

define_newtype!(DeckConfigId, i64);

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
    disable_autoplay: false,
    cap_answer_time_to_secs: 60,
    show_timer: false,
    stop_timer_on_answer: false,
    seconds_to_show_question: 0.0,
    seconds_to_show_answer: 0.0,
    answer_action: AnswerAction::BuryCard as i32,
    wait_for_audio: true,
    skip_question_when_replaying_answer: false,
    bury_new: false,
    bury_reviews: false,
    bury_interday_learning: false,
    fsrs_weights: vec![],
    desired_retention: 0.9,
    other: Vec::new(),
    reschedule_fsrs_cards: false,
    sm2_retention: 0.9,
    weight_search: String::new(),
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
                ..DEFAULT_DECK_CONFIG_INNER
            },
        }
    }
}

impl DeckConfig {
    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
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
        0.7,
        0.99,
    );
    ensure_f32_valid(&mut config.sm2_retention, default.sm2_retention, 0.7, 0.97)
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
