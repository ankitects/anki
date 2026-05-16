// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::config::config_key::Bool as BoolKeyProto;
use anki_proto::config::config_key::String as StringKeyProto;
use anki_proto::generic;
use serde_json::Value;

use crate::config::BoolKey;
use crate::config::StringKey;
use crate::prelude::*;

impl From<BoolKeyProto> for BoolKey {
    fn from(k: BoolKeyProto) -> Self {
        match k {
            BoolKeyProto::BrowserTableShowNotesMode => BoolKey::BrowserTableShowNotesMode,
            BoolKeyProto::PreviewBothSides => BoolKey::PreviewBothSides,
            BoolKeyProto::CollapseTags => BoolKey::CollapseTags,
            BoolKeyProto::CollapseNotetypes => BoolKey::CollapseNotetypes,
            BoolKeyProto::CollapseDecks => BoolKey::CollapseDecks,
            BoolKeyProto::CollapseSavedSearches => BoolKey::CollapseSavedSearches,
            BoolKeyProto::CollapseToday => BoolKey::CollapseToday,
            BoolKeyProto::CollapseCardState => BoolKey::CollapseCardState,
            BoolKeyProto::CollapseFlags => BoolKey::CollapseFlags,
            BoolKeyProto::Sched2021 => BoolKey::Sched2021,
            BoolKeyProto::AddingDefaultsToCurrentDeck => BoolKey::AddingDefaultsToCurrentDeck,
            BoolKeyProto::HideAudioPlayButtons => BoolKey::HideAudioPlayButtons,
            BoolKeyProto::InterruptAudioWhenAnswering => BoolKey::InterruptAudioWhenAnswering,
            BoolKeyProto::PasteImagesAsPng => BoolKey::PasteImagesAsPng,
            BoolKeyProto::PasteStripsFormatting => BoolKey::PasteStripsFormatting,
            BoolKeyProto::NormalizeNoteText => BoolKey::NormalizeNoteText,
            BoolKeyProto::IgnoreAccentsInSearch => BoolKey::IgnoreAccentsInSearch,
            BoolKeyProto::RestorePositionBrowser => BoolKey::RestorePositionBrowser,
            BoolKeyProto::RestorePositionReviewer => BoolKey::RestorePositionReviewer,
            BoolKeyProto::ResetCountsBrowser => BoolKey::ResetCountsBrowser,
            BoolKeyProto::ResetCountsReviewer => BoolKey::ResetCountsReviewer,
            BoolKeyProto::RandomOrderReposition => BoolKey::RandomOrderReposition,
            BoolKeyProto::ShiftPositionOfExistingCards => BoolKey::ShiftPositionOfExistingCards,
            BoolKeyProto::RenderLatex => BoolKey::RenderLatex,
            BoolKeyProto::LoadBalancerEnabled => BoolKey::LoadBalancerEnabled,
            BoolKeyProto::FsrsShortTermWithStepsEnabled => BoolKey::FsrsShortTermWithStepsEnabled,
            BoolKeyProto::FsrsLegacyEvaluate => BoolKey::FsrsLegacyEvaluate,
            BoolKeyProto::NewReviewer => BoolKey::NewReviewer,
        }
    }
}

impl From<StringKeyProto> for StringKey {
    fn from(k: StringKeyProto) -> Self {
        match k {
            StringKeyProto::SetDueBrowser => StringKey::SetDueBrowser,
            StringKeyProto::SetDueReviewer => StringKey::SetDueReviewer,
            StringKeyProto::DefaultSearchText => StringKey::DefaultSearchText,
            StringKeyProto::CardStateCustomizer => StringKey::CardStateCustomizer,
        }
    }
}

impl crate::services::ConfigService for Collection {
    fn get_config_json(&mut self, input: generic::String) -> Result<generic::Json> {
        let key = input.val.as_str();
        let val: Option<Value> = self.get_config_optional(key);
        let default = match key {
            "reviewerStorage" => Some(serde_json::from_str("{}").unwrap()),
            _ => None,
        };
        val.or(default)
            .or_not_found(key)
            .and_then(|v| serde_json::to_vec(&v).map_err(Into::into))
            .map(Into::into)
    }

    fn set_config_json(
        &mut self,
        input: anki_proto::config::SetConfigJsonRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        let val: Value = serde_json::from_slice(&input.value_json)?;
        self.set_config_json(input.key.as_str(), &val, input.undoable)
            .map(Into::into)
    }

    fn set_config_json_no_undo(
        &mut self,
        input: anki_proto::config::SetConfigJsonRequest,
    ) -> Result<()> {
        let val: Value = serde_json::from_slice(&input.value_json)?;
        self.transact_no_undo(|col| col.set_config(input.key.as_str(), &val).map(|_| ()))
    }

    fn remove_config(
        &mut self,
        input: generic::String,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.remove_config(input.val.as_str()).map(Into::into)
    }

    fn get_all_config(&mut self) -> Result<generic::Json> {
        let conf = self.storage.get_all_config()?;
        serde_json::to_vec(&conf)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_config_bool(
        &mut self,
        input: anki_proto::config::GetConfigBoolRequest,
    ) -> Result<generic::Bool> {
        Ok(generic::Bool {
            val: Collection::get_config_bool(self, input.key().into()),
        })
    }

    fn set_config_bool(
        &mut self,
        input: anki_proto::config::SetConfigBoolRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.set_config_bool(input.key().into(), input.value, input.undoable)
            .map(Into::into)
    }

    fn get_config_string(
        &mut self,
        input: anki_proto::config::GetConfigStringRequest,
    ) -> Result<generic::String> {
        Ok(generic::String {
            val: Collection::get_config_string(self, input.key().into()),
        })
    }

    fn set_config_string(
        &mut self,
        input: anki_proto::config::SetConfigStringRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.set_config_string(input.key().into(), &input.value, input.undoable)
            .map(Into::into)
    }

    fn get_preferences(&mut self) -> Result<anki_proto::config::Preferences> {
        Collection::get_preferences(self)
    }

    fn set_preferences(
        &mut self,
        input: anki_proto::config::Preferences,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.set_preferences(input).map(Into::into)
    }
}
