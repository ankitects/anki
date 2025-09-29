// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde_aux::field_attributes::deserialize_bool_from_anything;
use strum::IntoStaticStr;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum BoolKey {
    ApplyAllParentLimits,
    BrowserTableShowNotesMode,
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
    IgnoreAccentsInSearch,
    InterruptAudioWhenAnswering,
    NewCardsIgnoreReviewLimit,
    PasteImagesAsPng,
    PasteStripsFormatting,
    RenderLatex,
    PreviewBothSides,
    RestorePositionBrowser,
    RestorePositionReviewer,
    ResetCountsBrowser,
    ResetCountsReviewer,
    RandomOrderReposition,
    Sched2021,
    ShiftPositionOfExistingCards,
    MergeNotetypes,
    WithScheduling,
    WithDeckConfigs,
    Fsrs,
    FsrsHealthCheck,
    FsrsLegacyEvaluate,
    LoadBalancerEnabled,
    FsrsShortTermWithStepsEnabled,
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
    #[strum(to_string = "showColor")]
    ShowColoredButtons,
}

/// This is a workaround for old clients that used ints to represent boolean
/// values. For new config items, prefer using a bool directly.
#[derive(Deserialize, Default)]
struct BoolLike(
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    #[allow(dead_code)]
    bool,
);

impl Collection {
    pub fn get_config_bool(&self, key: BoolKey) -> bool {
        match key {
            // some keys default to true
            BoolKey::InterruptAudioWhenAnswering
            | BoolKey::ShowIntervalsAboveAnswerButtons
            | BoolKey::ShowColoredButtons
            | BoolKey::AddingDefaultsToCurrentDeck
            | BoolKey::FutureDueShowBacklog
            | BoolKey::ShowRemainingDueCountsInStudy
            | BoolKey::CardCountsSeparateInactive
            | BoolKey::RestorePositionBrowser
            | BoolKey::RestorePositionReviewer
            | BoolKey::LoadBalancerEnabled
            | BoolKey::FsrsHealthCheck
            | BoolKey::NormalizeNoteText => self.get_config_optional(key).unwrap_or(true),

            // other options default to false
            other => self.get_config_default(other),
        }
    }

    pub fn set_config_bool(
        &mut self,
        key: BoolKey,
        value: bool,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        let op = if undoable {
            Op::UpdateConfig
        } else {
            Op::SkipUndo
        };
        self.transact(op, |col| {
            col.set_config(key, &value)?;
            Ok(())
        })
    }
}

impl Collection {
    pub(crate) fn set_config_bool_inner(&mut self, key: BoolKey, value: bool) -> Result<bool> {
        self.set_config(key, &value)
    }
}
