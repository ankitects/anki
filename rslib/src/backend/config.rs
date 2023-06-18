// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::config::config_key::Bool as BoolKeyProto;
use anki_proto::config::config_key::String as StringKeyProto;
pub(super) use anki_proto::config::config_service::Service as ConfigService;
use anki_proto::generic;
use serde_json::Value;

use super::Backend;
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

impl ConfigService for Backend {
    type Error = AnkiError;

    fn get_config_json(&self, input: generic::String) -> Result<generic::Json> {
        self.with_col(|col| {
            let val: Option<Value> = col.get_config_optional(input.val.as_str());
            val.or_not_found(input.val)
                .and_then(|v| serde_json::to_vec(&v).map_err(Into::into))
                .map(Into::into)
        })
    }

    fn set_config_json(
        &self,
        input: anki_proto::config::SetConfigJsonRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            let val: Value = serde_json::from_slice(&input.value_json)?;
            col.set_config_json(input.key.as_str(), &val, input.undoable)
        })
        .map(Into::into)
    }

    fn set_config_json_no_undo(
        &self,
        input: anki_proto::config::SetConfigJsonRequest,
    ) -> Result<()> {
        self.with_col(|col| {
            let val: Value = serde_json::from_slice(&input.value_json)?;
            col.transact_no_undo(|col| col.set_config(input.key.as_str(), &val).map(|_| ()))
        })
        .map(Into::into)
    }

    fn remove_config(&self, input: generic::String) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.remove_config(input.val.as_str()))
            .map(Into::into)
    }

    fn get_all_config(&self) -> Result<generic::Json> {
        self.with_col(|col| {
            let conf = col.storage.get_all_config()?;
            serde_json::to_vec(&conf).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_config_bool(
        &self,
        input: anki_proto::config::GetConfigBoolRequest,
    ) -> Result<generic::Bool> {
        self.with_col(|col| {
            Ok(generic::Bool {
                val: col.get_config_bool(input.key().into()),
            })
        })
    }

    fn set_config_bool(
        &self,
        input: anki_proto::config::SetConfigBoolRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.set_config_bool(input.key().into(), input.value, input.undoable))
            .map(Into::into)
    }

    fn get_config_string(
        &self,
        input: anki_proto::config::GetConfigStringRequest,
    ) -> Result<generic::String> {
        self.with_col(|col| {
            Ok(generic::String {
                val: col.get_config_string(input.key().into()),
            })
        })
    }

    fn set_config_string(
        &self,
        input: anki_proto::config::SetConfigStringRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.set_config_string(input.key().into(), &input.value, input.undoable))
            .map(Into::into)
    }

    fn get_preferences(&self) -> Result<anki_proto::config::Preferences> {
        self.with_col(|col| col.get_preferences())
    }

    fn set_preferences(
        &self,
        input: anki_proto::config::Preferences,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.set_preferences(input))
            .map(Into::into)
    }
}
