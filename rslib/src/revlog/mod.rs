// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod undo;

use num_enum::TryFromPrimitive;
use serde::Deserialize;
use serde::Serialize;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;
use serde_tuple::Serialize_tuple;

use crate::define_newtype;
use crate::prelude::*;
use crate::probe::ProbeId;
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

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq, Eq, Clone)]
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
    /// 250%. When FSRS is active, difficulty is normalized to 100-1100 range,
    /// so a 0 difficulty can be distinguished from SM-2 learning.
    #[serde(rename = "factor", deserialize_with = "deserialize_int_from_number")]
    pub ease_factor: u32,
    /// Amount of milliseconds taken to answer the card.
    #[serde(rename = "time", deserialize_with = "deserialize_int_from_number")]
    pub taken_millis: u32,
    #[serde(rename = "type", default, deserialize_with = "default_on_invalid")]
    pub review_kind: RevlogReviewKind,
    /// Milliseconds from the question being shown to the answer being
    /// revealed. None for entries logged before this field existed, or by
    /// clients that don't report it; distinguishable from a real zero.
    /// Unlike `taken_millis`, this is stored uncapped, so the deck's answer
    /// time limit never silently rewrites it.
    #[serde(default)]
    pub reveal_millis: Option<u32>,
    /// JSON object with optional extra data about the review, mirroring the
    /// `cards.data` pattern; see [RevlogData]. Empty for ordinary reviews and
    /// for entries logged before the column existed.
    #[serde(default)]
    pub data: String,
}

/// Helper for serdeing the revlog data column.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Default)]
#[serde(default)]
pub struct RevlogData {
    /// The probe variant that was shown in place of the original card.
    /// None means the original was shown.
    #[serde(
        rename = "vid",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub variant_id: Option<ProbeId>,
}

impl RevlogData {
    pub(crate) fn from_str(s: &str) -> Self {
        serde_json::from_str(s).unwrap_or_default()
    }

    /// The empty string when there is nothing to record, so rows for
    /// ordinary reviews stay identical to ones from older clients.
    pub(crate) fn to_data_string(&self) -> String {
        if *self == Self::default() {
            String::new()
        } else {
            serde_json::to_string(self).unwrap_or_default()
        }
    }
}

impl RevlogEntry {
    /// The probe variant recorded for this review, if any.
    pub fn variant_id(&self) -> Option<ProbeId> {
        RevlogData::from_str(&self.data).variant_id
    }
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
#[derive(Default)]
pub enum RevlogReviewKind {
    #[default]
    Learning = 0,
    Review = 1,
    Relearning = 2,
    /// Old Anki versions called this "Cram" or "Early". It's assigned when
    /// reviewing cards before they're due, or when rescheduling is
    /// disabled.
    Filtered = 3,
    Manual = 4,
    Rescheduled = 5,
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

    pub(crate) fn last_interval_secs(&self) -> u32 {
        u32::try_from(if self.last_interval > 0 {
            self.last_interval.saturating_mul(86_400)
        } else {
            self.last_interval.saturating_mul(-1)
        })
        .unwrap()
    }

    /// Returns true if this entry represents a reset operation.
    /// These entries are created when a card is reset using
    /// [`Collection::reschedule_cards_as_new`].
    /// The 0 value of `ease_factor` differentiates it
    /// from entry created by [`Collection::set_due_date`] that has
    /// `RevlogReviewKind::Manual` but non-zero `ease_factor`.
    pub(crate) fn is_reset(&self) -> bool {
        self.review_kind == RevlogReviewKind::Manual && self.ease_factor == 0
    }

    /// Returns true if this entry represents a cramming operation.
    /// These entries are created when a card is reviewed in a
    /// filtered deck with "Reschedule cards based on my answers
    /// in this deck" disabled.
    /// [`crate::scheduler::answering::CardStateUpdater::apply_preview_state`].
    /// The 0 value of `ease_factor` distinguishes it from the entry
    /// created when a card is reviewed before its due date in a
    /// filtered deck with reschedule enabled or using Grade Now.
    pub(crate) fn is_cramming(&self) -> bool {
        self.review_kind == RevlogReviewKind::Filtered && self.ease_factor == 0
    }

    pub(crate) fn has_rating(&self) -> bool {
        self.button_chosen > 0
    }

    /// Returns true if the review entry is not manually rescheduled and not
    /// cramming. Used to filter out entries that shouldn't be considered
    /// for statistics and scheduling.
    pub(crate) fn has_rating_and_affects_scheduling(&self) -> bool {
        // not rescheduled/set due date/reset
        self.has_rating()
            // not cramming
            && !self.is_cramming()
    }
}

impl Collection {
    // set due date or reset
    pub(crate) fn log_manually_scheduled_review(
        &mut self,
        card: &Card,
        original_interval: u32,
        usn: Usn,
    ) -> Result<()> {
        self.log_scheduled_review(card, original_interval, usn, RevlogReviewKind::Manual)
    }

    // reschedule cards on change
    pub(crate) fn log_rescheduled_review(
        &mut self,
        card: &Card,
        original_interval: u32,
        usn: Usn,
    ) -> Result<()> {
        self.log_scheduled_review(card, original_interval, usn, RevlogReviewKind::Rescheduled)
    }

    fn log_scheduled_review(
        &mut self,
        card: &Card,
        original_interval: u32,
        usn: Usn,
        review_kind: RevlogReviewKind,
    ) -> Result<()> {
        let ease_factor = u32::from(
            card.memory_state
                .map(|s| (s.difficulty_shifted() * 1000.) as u16)
                .unwrap_or(card.ease_factor),
        );
        let entry = RevlogEntry {
            id: RevlogId::new(),
            cid: card.id,
            usn,
            button_chosen: 0,
            interval: i32::try_from(card.interval).unwrap_or(i32::MAX),
            last_interval: i32::try_from(original_interval).unwrap_or(i32::MAX),
            ease_factor,
            taken_millis: 0,
            review_kind,
            reveal_millis: None,
            data: String::new(),
        };
        self.add_revlog_entry_undoable(entry)?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn reveal_millis_serde_compat() {
        // entries from before the field existed deserialize as None, not 0
        let entry: RevlogEntry = serde_json::from_str("[1,2,-1,3,5,-60,2500,3000,1]").unwrap();
        assert_eq!(entry.taken_millis, 3000);
        assert_eq!(entry.reveal_millis, None);

        let entry: RevlogEntry = serde_json::from_str("[1,2,-1,3,5,-60,2500,3000,1,1500]").unwrap();
        assert_eq!(entry.reveal_millis, Some(1500));

        let json = serde_json::to_string(&entry).unwrap();
        assert_eq!(serde_json::from_str::<RevlogEntry>(&json).unwrap(), entry);
    }

    #[test]
    fn data_serde_compat() {
        // entries from before the data column existed deserialize as empty
        let entry: RevlogEntry = serde_json::from_str("[1,2,-1,3,5,-60,2500,3000,1]").unwrap();
        assert_eq!(entry.data, "");
        assert_eq!(entry.variant_id(), None);
        let entry: RevlogEntry = serde_json::from_str("[1,2,-1,3,5,-60,2500,3000,1,1500]").unwrap();
        assert_eq!(entry.data, "");

        let entry: RevlogEntry =
            serde_json::from_str(r#"[1,2,-1,3,5,-60,2500,3000,1,1500,"{\"vid\":123}"]"#).unwrap();
        assert_eq!(entry.variant_id(), Some(ProbeId(123)));

        let json = serde_json::to_string(&entry).unwrap();
        assert_eq!(serde_json::from_str::<RevlogEntry>(&json).unwrap(), entry);

        // unknown keys and invalid content are tolerated
        assert_eq!(
            RevlogData::from_str(r#"{"future":1}"#),
            RevlogData::default()
        );
        assert_eq!(RevlogData::from_str("not json"), RevlogData::default());
        // and nothing to record round-trips as the empty string
        assert_eq!(RevlogData::default().to_data_string(), "");
        assert_eq!(
            RevlogData {
                variant_id: Some(ProbeId(123))
            }
            .to_data_string(),
            r#"{"vid":123}"#
        );
    }
}
