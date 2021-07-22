// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    backend_proto::backend_error::Kind,
    error::{AnkiError, SyncErrorKind},
    prelude::*,
};

impl AnkiError {
    pub(super) fn into_protobuf(self, tr: &I18n) -> pb::BackendError {
        let localized = self.localized_description(tr);
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
            AnkiError::Existing => Kind::Exists,
            AnkiError::FilteredDeckError(_) => Kind::FilteredDeckError,
            AnkiError::SearchError(_) => Kind::SearchError,
            AnkiError::TemplateSaveError(_) => Kind::TemplateParse,
            AnkiError::ParseNumError => Kind::InvalidInput,
            AnkiError::InvalidRegex(_) => Kind::InvalidInput,
            AnkiError::UndoEmpty => Kind::UndoEmpty,
            AnkiError::MultipleNotetypesSelected => Kind::InvalidInput,
            AnkiError::DatabaseCheckRequired => Kind::InvalidInput,
        };

        pb::BackendError {
            kind: kind as i32,
            localized,
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
