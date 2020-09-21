// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection,
    define_newtype,
    err::{AnkiError, Result},
    timestamp::{TimestampMillis, TimestampSecs},
    types::Usn,
};

pub use crate::backend_proto::{
    deck_config_inner::{LeechAction, NewCardOrder},
    DeckConfigInner,
};
pub use schema11::{DeckConfSchema11, NewCardOrderSchema11};
/// Old deck config and cards table store 250% as 2500.
pub const INITIAL_EASE_FACTOR_THOUSANDS: u16 = 2500;

mod schema11;

define_newtype!(DeckConfID, i64);

#[derive(Debug, PartialEq, Clone)]
pub struct DeckConf {
    pub id: DeckConfID,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub inner: DeckConfigInner,
}

impl Default for DeckConf {
    fn default() -> Self {
        DeckConf {
            id: DeckConfID(0),
            name: "".to_string(),
            mtime_secs: Default::default(),
            usn: Default::default(),
            inner: DeckConfigInner {
                learn_steps: vec![1.0, 10.0],
                relearn_steps: vec![10.0],
                disable_autoplay: false,
                cap_answer_time_to_secs: 60,
                visible_timer_secs: 0,
                skip_question_when_replaying_answer: false,
                new_per_day: 20,
                reviews_per_day: 200,
                bury_new: false,
                bury_reviews: false,
                initial_ease: 2.5,
                easy_multiplier: 1.3,
                hard_multiplier: 1.2,
                lapse_multiplier: 0.0,
                interval_multiplier: 1.0,
                maximum_review_interval: 36_500,
                minimum_review_interval: 1,
                graduating_interval_good: 1,
                graduating_interval_easy: 4,
                new_card_order: NewCardOrder::Due as i32,
                leech_action: LeechAction::TagOnly as i32,
                leech_threshold: 8,
                other: vec![],
            },
        }
    }
}

impl Collection {
    /// If fallback is true, guaranteed to return a deck config.
    pub fn get_deck_config(&self, dcid: DeckConfID, fallback: bool) -> Result<Option<DeckConf>> {
        if let Some(conf) = self.storage.get_deck_config(dcid)? {
            return Ok(Some(conf));
        }
        if fallback {
            if let Some(conf) = self.storage.get_deck_config(DeckConfID(1))? {
                return Ok(Some(conf));
            }
            // if even the default deck config is missing, just return the defaults
            Ok(Some(DeckConf::default()))
        } else {
            Ok(None)
        }
    }

    pub(crate) fn add_or_update_deck_config(
        &self,
        conf: &mut DeckConf,
        preserve_usn_and_mtime: bool,
    ) -> Result<()> {
        if !preserve_usn_and_mtime {
            conf.mtime_secs = TimestampSecs::now();
            conf.usn = self.usn()?;
        }
        let orig = self.storage.get_deck_config(conf.id)?;
        if let Some(_orig) = orig {
            self.storage.update_deck_conf(&conf)
        } else {
            if conf.id.0 == 0 {
                conf.id.0 = TimestampMillis::now().0;
            }
            self.storage.add_deck_conf(conf)
        }
    }

    /// Remove a deck configuration. This will force a full sync.
    pub(crate) fn remove_deck_config(&self, dcid: DeckConfID) -> Result<()> {
        if dcid.0 == 1 {
            return Err(AnkiError::invalid_input("can't delete default conf"));
        }
        self.storage.set_schema_modified()?;
        self.storage.remove_deck_conf(dcid)
    }
}
