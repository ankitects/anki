// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;
use serde_aux::field_attributes::deserialize_bool_from_anything;
use serde_derive::Deserialize;
use strum::IntoStaticStr;

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum BoolKey {
    CardCountsSeparateInactive,
    CollapseCardState,
    CollapseDecks,
    CollapseFlags,
    CollapseNotetypes,
    CollapseSavedSearches,
    CollapseTags,
    CollapseToday,
    FutureDueShowBacklog,
    HideAudioPlayButtons,
    InterruptAudioWhenAnswering,
    PasteImagesAsPng,
    PasteStripsFormatting,
    PreviewBothSides,
    Sched2021,

    #[strum(to_string = "sortBackwards")]
    BrowserSortBackwards,
    #[strum(to_string = "normalize_note_text")]
    NormalizeNoteText,
    #[strum(to_string = "dayLearnFirst")]
    ShowDayLearningCardsFirst,
    #[strum(to_string = "estTimes")]
    ShowIntervalsAboveAnswerButtons,
    #[strum(to_string = "dueCounts")]
    ShowRemainingDueCountsInStudy,
    #[strum(to_string = "addToCur")]
    AddingDefaultsToCurrentDeck,
}

/// This is a workaround for old clients that used ints to represent boolean
/// values. For new config items, prefer using a bool directly.
#[derive(Deserialize, Default)]
struct BoolLike(#[serde(deserialize_with = "deserialize_bool_from_anything")] bool);

impl Collection {
    pub(crate) fn get_bool(&self, key: BoolKey) -> bool {
        match key {
            BoolKey::BrowserSortBackwards => {
                // older clients were storing this as an int
                self.get_config_default::<BoolLike, _>(BoolKey::BrowserSortBackwards)
                    .0
            }

            // some keys default to true
            BoolKey::InterruptAudioWhenAnswering
            | BoolKey::ShowIntervalsAboveAnswerButtons
            | BoolKey::AddingDefaultsToCurrentDeck
            | BoolKey::FutureDueShowBacklog
            | BoolKey::ShowRemainingDueCountsInStudy
            | BoolKey::CardCountsSeparateInactive
            | BoolKey::NormalizeNoteText => self.get_config_optional(key).unwrap_or(true),

            // other options default to false
            other => self.get_config_default(other),
        }
    }

    pub(crate) fn set_bool(&mut self, key: BoolKey, value: bool) -> Result<()> {
        self.set_config(key, &value)
    }
}
