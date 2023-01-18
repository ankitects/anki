// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::error::AnkiError;
use crate::error::SyncErrorKind;
use crate::pb;
use crate::pb::backend::backend_error::Kind;
use crate::prelude::*;

impl AnkiError {
    pub fn into_protobuf(self, tr: &I18n) -> pb::backend::BackendError {
        let message = self.message(tr);
        let help_page = self.help_page().map(|page| page as i32);
        let context = self.context();
        let backtrace = self.backtrace();
        let kind = match self {
            AnkiError::InvalidInput { .. } => Kind::InvalidInput,
            AnkiError::TemplateError { .. } => Kind::TemplateParse,
            AnkiError::DbError { .. } => Kind::DbError,
            AnkiError::NetworkError { .. } => Kind::NetworkError,
            AnkiError::SyncError { source } => source.kind.into(),
            AnkiError::Interrupted => Kind::Interrupted,
            AnkiError::CollectionNotOpen => Kind::InvalidInput,
            AnkiError::CollectionAlreadyOpen => Kind::InvalidInput,
            AnkiError::JsonError { .. } => Kind::JsonError,
            AnkiError::ProtoError { .. } => Kind::ProtoError,
            AnkiError::NotFound { .. } => Kind::NotFoundError,
            AnkiError::Deleted => Kind::Deleted,
            AnkiError::Existing => Kind::Exists,
            AnkiError::FilteredDeckError { .. } => Kind::FilteredDeckError,
            AnkiError::SearchError { .. } => Kind::SearchError,
            AnkiError::CardTypeError { .. } => Kind::CardTypeError,
            AnkiError::ParseNumError => Kind::InvalidInput,
            AnkiError::InvalidRegex { .. } => Kind::InvalidInput,
            AnkiError::UndoEmpty => Kind::UndoEmpty,
            AnkiError::MultipleNotetypesSelected => Kind::InvalidInput,
            AnkiError::DatabaseCheckRequired => Kind::InvalidInput,
            AnkiError::CustomStudyError { .. } => Kind::CustomStudyError,
            AnkiError::ImportError { .. } => Kind::ImportError,
            AnkiError::FileIoError { .. } => Kind::IoError,
            AnkiError::MediaCheckRequired => Kind::InvalidInput,
            AnkiError::InvalidId => Kind::InvalidInput,
        };

        pb::backend::BackendError {
            kind: kind as i32,
            message,
            help_page,
            context,
            backtrace,
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
