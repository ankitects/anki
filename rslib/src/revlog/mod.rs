// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod undo;

use num_enum::TryFromPrimitive;
use serde::Deserialize;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;
use serde_tuple::Serialize_tuple;

use crate::define_newtype;
use crate::prelude::*;
use crate::serde::default_on_invalid;
use crate::serde::deserialize_int_from_number;

define_newtype!(RevlogId, i64);

impl RevlogId {
    pub fn new() -> Self {
        RevlogId(TimestampMillis::now().0)
    }

    pub fn as_secs(self) -> TimestampSecs {
        TimestampSecs(self.0 / 1000)
    }
}

impl From<TimestampMillis> for RevlogId {
    fn from(m: TimestampMillis) -> Self {
        RevlogId(m.0)
    }
}

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq, Eq)]
pub struct RevlogEntry {
    pub id: RevlogId,
    pub cid: CardId,
    pub usn: Usn,
    /// - In the V1 scheduler, 3 represents easy in the learning case.
    /// - 0 represents manual rescheduling.
    #[serde(rename = "ease")]
    pub button_chosen: u8,
    /// Positive values are in days, negative values in seconds.
    #[serde(rename = "ivl", deserialize_with = "deserialize_int_from_number")]
    pub interval: i32,
    /// Positive values are in days, negative values in seconds.
    #[serde(rename = "lastIvl", deserialize_with = "deserialize_int_from_number")]
    pub last_interval: i32,
    /// Card's ease after answering, stored as 10x the %, eg 2500 represents
    /// 250%.
    #[serde(rename = "factor", deserialize_with = "deserialize_int_from_number")]
    pub ease_factor: u32,
    /// Amount of milliseconds taken to answer the card.
    #[serde(rename = "time", deserialize_with = "deserialize_int_from_number")]
    pub taken_millis: u32,
    #[serde(rename = "type", default, deserialize_with = "default_on_invalid")]
    pub review_kind: RevlogReviewKind,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
pub enum RevlogReviewKind {
    Learning = 0,
    Review = 1,
    Relearning = 2,
    /// Old Anki versions called this "Cram" or "Early", and assigned it when
    /// reviewing cards ahead. It is now only used for filtered decks with
    /// rescheduling disabled.
    Filtered = 3,
    Manual = 4,
}

impl Default for RevlogReviewKind {
    fn default() -> Self {
        RevlogReviewKind::Learning
    }
}

impl RevlogEntry {
    pub(crate) fn interval_secs(&self) -> u32 {
        u32::try_from(if self.interval > 0 {
            self.interval.saturating_mul(86_400)
        } else {
            self.interval.saturating_mul(-1)
        })
        .unwrap()
    }
}

impl Collection {
    pub(crate) fn log_manually_scheduled_review(
        &mut self,
        card: &Card,
        original: &Card,
        usn: Usn,
    ) -> Result<()> {
        let entry = RevlogEntry {
            id: RevlogId::new(),
            cid: card.id,
            usn,
            button_chosen: 0,
            interval: i32::try_from(card.interval).unwrap_or(i32::MAX),
            last_interval: i32::try_from(original.interval).unwrap_or(i32::MAX),
            ease_factor: u32::from(card.ease_factor),
            taken_millis: 0,
            review_kind: RevlogReviewKind::Manual,
        };
        self.add_revlog_entry_undoable(entry)?;
        Ok(())
    }
}
