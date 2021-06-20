// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod schema11;
pub(crate) mod undo;
mod update;

pub use schema11::{DeckConfSchema11, NewCardOrderSchema11};
pub use update::UpdateDeckConfigsRequest;

pub use crate::backend_proto::deck_config::{
    config::{
        LeechAction, NewCardGatherPriority, NewCardInsertOrder, NewCardSortOrder, ReviewCardOrder,
        ReviewMix,
    },
    Config as DeckConfigInner,
};

/// Old deck config and cards table store 250% as 2500.
pub(crate) const INITIAL_EASE_FACTOR_THOUSANDS: u16 = (INITIAL_EASE_FACTOR * 1000.0) as u16;

use crate::{
    collection::Collection,
    define_newtype,
    error::{AnkiError, Result},
    scheduler::states::review::INITIAL_EASE_FACTOR,
    timestamp::{TimestampMillis, TimestampSecs},
    types::Usn,
};

define_newtype!(DeckConfigId, i64);

#[derive(Debug, PartialEq, Clone)]
pub struct DeckConfig {
    pub id: DeckConfigId,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub inner: DeckConfigInner,
}

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
                new_card_sort_order: NewCardSortOrder::TemplateThenLowestPosition as i32,
                review_order: ReviewCardOrder::Day as i32,
                new_mix: ReviewMix::MixWithReviews as i32,
                interday_learning_mix: ReviewMix::MixWithReviews as i32,
                leech_action: LeechAction::TagOnly as i32,
                leech_threshold: 8,
                disable_autoplay: false,
                cap_answer_time_to_secs: 60,
                show_timer: false,
                skip_question_when_replaying_answer: false,
                bury_new: false,
                bury_reviews: false,
                other: vec![],
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
                .ok_or(AnkiError::NotFound)?;
            self.update_deck_config_inner(config, original, usn)
        }
    }

    /// Used by the old import code; if provided id is non-zero, will add
    /// instead of ignoring. Does not support undo.
    pub(crate) fn add_or_update_deck_config_legacy(
        &mut self,
        config: &mut DeckConfig,
    ) -> Result<()> {
        let usn = Some(self.usn()?);

        if config.id.0 == 0 {
            self.add_deck_config_inner(config, usn)
        } else {
            config.set_modified(usn.unwrap());
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
        if dcid.0 == 1 {
            return Err(AnkiError::invalid_input("can't delete default conf"));
        }
        let conf = self
            .storage
            .get_deck_config(dcid)?
            .ok_or(AnkiError::NotFound)?;
        self.set_schema_modified()?;
        self.remove_deck_config_undoable(conf)
    }
}
