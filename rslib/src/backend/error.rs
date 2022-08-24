// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    error::{AnkiError, SyncErrorKind},
    pb,
    pb::backend_error::Kind,
    prelude::*,
};

impl AnkiError {
    pub(super) fn into_protobuf(self, tr: &I18n) -> pb::BackendError {
        let localized = self.localized_description(tr);
        let help_page = self.help_page().map(|page| page as i32);
        let kind = match self {
            AnkiError::InvalidInput(_) => Kind::InvalidInput,
            AnkiError::TemplateError(_) => Kind::TemplateParse,
            AnkiError::IoError(_) => Kind::IoError,
            AnkiError::DbError(_) => Kind::DbError,
            AnkiError::NetworkError(_) => Kind::NetworkError,
            AnkiError::SyncError(err) => err.kind.into(),
            AnkiError::Interrupted => Kind::Interrupted,
            AnkiError::CollectionNotOpen => Kind::InvalidInput,
            AnkiError::CollectionAlreadyOpen => Kind::InvalidInput,
            AnkiError::JsonError(_) => Kind::JsonError,
            AnkiError::ProtoError(_) => Kind::ProtoError,
            AnkiError::NotFound => Kind::NotFoundError,
            AnkiError::Deleted => Kind::Deleted,
            AnkiError::Existing => Kind::Exists,
            AnkiError::FilteredDeckError(_) => Kind::FilteredDeckError,
            AnkiError::SearchError(_) => Kind::SearchError,
            AnkiError::CardTypeError(_) => Kind::CardTypeError,
            AnkiError::ParseNumError => Kind::InvalidInput,
            AnkiError::InvalidRegex(_) => Kind::InvalidInput,
            AnkiError::UndoEmpty => Kind::UndoEmpty,
            AnkiError::MultipleNotetypesSelected => Kind::InvalidInput,
            AnkiError::DatabaseCheckRequired => Kind::InvalidInput,
            AnkiError::CustomStudyError(_) => Kind::CustomStudyError,
            AnkiError::ImportError(_) => Kind::ImportError,
            AnkiError::FileIoError(_) => Kind::IoError,
            AnkiError::MediaCheckRequired => Kind::InvalidInput,
            AnkiError::InvalidId => Kind::InvalidInput,
        };

        pb::BackendError {
            kind: kind as i32,
            localized,
            help_page,
        }
    }
}

impl From<SyncErrorKind> for Kind {
    fn from(err: SyncErrorKind) -> Self {
        match err {
            SyncErrorKind::AuthFailed => Kind::SyncAuthError,
            _ => Kind::SyncOtherError,
        }
    }
}
