// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{define_newtype, prelude::*};
use crate::{
    serde::{default_on_invalid, deserialize_int_from_number},
    undo::Undo,
};
use num_enum::TryFromPrimitive;
use serde::Deserialize;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;

define_newtype!(RevlogID, i64);

impl RevlogID {
    pub fn new() -> Self {
        RevlogID(TimestampMillis::now().0)
    }

    pub fn as_secs(self) -> TimestampSecs {
        TimestampSecs(self.0 / 1000)
    }
}

impl From<TimestampMillis> for RevlogID {
    fn from(m: TimestampMillis) -> Self {
        RevlogID(m.0)
    }
}

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq)]
pub struct RevlogEntry {
    pub id: RevlogID,
    pub cid: CardID,
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
    /// Card's ease after answering, stored as 10x the %, eg 2500 represents 250%.
    #[serde(rename = "factor", deserialize_with = "deserialize_int_from_number")]
    pub ease_factor: u32,
    /// Amount of milliseconds taken to answer the card.
    #[serde(rename = "time", deserialize_with = "deserialize_int_from_number")]
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
    Manual = 4,
    // Preview = 5,
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

impl Collection {
    /// Add the provided revlog entry, modifying the ID if it is not unique.
    pub(crate) fn add_revlog_entry(&mut self, mut entry: RevlogEntry) -> Result<RevlogID> {
        entry.id = self.storage.add_revlog_entry(&entry, true)?;
        let id = entry.id;
        self.save_undo(Box::new(RevlogAdded(entry)));
        Ok(id)
    }

    pub(crate) fn log_manually_scheduled_review(
        &mut self,
        card: &Card,
        original: &Card,
        usn: Usn,
    ) -> Result<()> {
        let entry = RevlogEntry {
            id: RevlogID::new(),
            cid: card.id,
            usn,
            button_chosen: 0,
            interval: card.interval as i32,
            last_interval: original.interval as i32,
            ease_factor: card.ease_factor as u32,
            taken_millis: 0,
            review_kind: RevlogReviewKind::Manual,
        };
        self.add_revlog_entry(entry)?;
        Ok(())
    }
}

#[derive(Debug)]
pub(crate) struct RevlogAdded(RevlogEntry);
#[derive(Debug)]
pub(crate) struct RevlogRemoved(RevlogEntry);

impl Undo for RevlogAdded {
    fn undo(self: Box<Self>, col: &mut crate::collection::Collection, _usn: Usn) -> Result<()> {
        col.storage.remove_revlog_entry(self.0.id)?;
        col.save_undo(Box::new(RevlogRemoved(self.0)));
        Ok(())
    }
}

impl Undo for RevlogRemoved {
    fn undo(self: Box<Self>, col: &mut crate::collection::Collection, _usn: Usn) -> Result<()> {
        col.storage.add_revlog_entry(&self.0, false)?;
        col.save_undo(Box::new(RevlogAdded(self.0)));
        Ok(())
    }
}
