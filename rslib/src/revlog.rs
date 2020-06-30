// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::serde::default_on_invalid;
use crate::{define_newtype, prelude::*};
use num_enum::TryFromPrimitive;
use serde::Deserialize;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;

define_newtype!(RevlogID, i64);

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq)]
pub struct RevlogEntry {
    pub id: TimestampMillis,
    pub cid: CardID,
    pub usn: Usn,
    /// - In the V1 scheduler, 3 represents easy in the learning case.
    /// - 0 represents manual rescheduling.
    #[serde(rename = "ease")]
    pub button_chosen: u8,
    /// Positive values are in days, negative values in seconds.
    #[serde(rename = "ivl")]
    pub interval: i32,
    /// Positive values are in days, negative values in seconds.
    #[serde(rename = "lastIvl")]
    pub last_interval: i32,
    /// Card's ease after answering, stored as 10x the %, eg 2500 represents 250%.
    #[serde(rename = "factor")]
    pub ease_factor: u32,
    /// Amount of milliseconds taken to answer the card.
    #[serde(rename = "time")]
    pub taken_millis: u32,
    #[serde(rename = "type", default, deserialize_with = "default_on_invalid")]
    pub review_kind: RevlogReviewKind,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
pub enum RevlogReviewKind {
    Learning = 0,
    Review = 1,
    Relearning = 2,
    EarlyReview = 3,
}

impl Default for RevlogReviewKind {
    fn default() -> Self {
        RevlogReviewKind::Learning
    }
}

impl RevlogEntry {
    pub(crate) fn interval_secs(&self) -> u32 {
        (if self.interval > 0 {
            self.interval * 86_400
        } else {
            -self.interval
        }) as u32
    }
}
