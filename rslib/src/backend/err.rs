// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    err::{AnkiError, NetworkErrorKind, SyncErrorKind},
    prelude::*,
};

/// Convert an Anki error to a protobuf error.
pub(super) fn anki_error_to_proto_error(err: AnkiError, i18n: &I18n) -> pb::BackendError {
    use pb::backend_error::Value as V;
    let localized = err.localized_description(i18n);
    let value = match err {
        AnkiError::InvalidInput { .. } => V::InvalidInput(pb::Empty {}),
        AnkiError::TemplateError { .. } => V::TemplateParse(pb::Empty {}),
        AnkiError::IOError { .. } => V::IoError(pb::Empty {}),
        AnkiError::DBError { .. } => V::DbError(pb::Empty {}),
        AnkiError::NetworkError { kind, .. } => {
            V::NetworkError(pb::NetworkError { kind: kind.into() })
        }
        AnkiError::SyncError { kind, .. } => V::SyncError(pb::SyncError { kind: kind.into() }),
        AnkiError::Interrupted => V::Interrupted(pb::Empty {}),
        AnkiError::CollectionNotOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::CollectionAlreadyOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::JSONError { info } => V::JsonError(info),
        AnkiError::ProtoError { info } => V::ProtoError(info),
        AnkiError::NotFound => V::NotFoundError(pb::Empty {}),
        AnkiError::Existing => V::Exists(pb::Empty {}),
        AnkiError::DeckIsFiltered => V::DeckIsFiltered(pb::Empty {}),
        AnkiError::SearchError(_) => V::InvalidInput(pb::Empty {}),
        AnkiError::TemplateSaveError { .. } => V::TemplateParse(pb::Empty {}),
        AnkiError::ParseNumError => V::InvalidInput(pb::Empty {}),
    };

    pb::BackendError {
        value: Some(value),
        localized,
    }
}

impl std::convert::From<NetworkErrorKind> for i32 {
    fn from(e: NetworkErrorKind) -> Self {
        use pb::network_error::NetworkErrorKind as V;
        (match e {
            NetworkErrorKind::Offline => V::Offline,
            NetworkErrorKind::Timeout => V::Timeout,
            NetworkErrorKind::ProxyAuth => V::ProxyAuth,
            NetworkErrorKind::Other => V::Other,
        }) as i32
    }
}

impl std::convert::From<SyncErrorKind> for i32 {
    fn from(e: SyncErrorKind) -> Self {
        use pb::sync_error::SyncErrorKind as V;
        (match e {
            SyncErrorKind::Conflict => V::Conflict,
            SyncErrorKind::ServerError => V::ServerError,
            SyncErrorKind::ClientTooOld => V::ClientTooOld,
            SyncErrorKind::AuthFailed => V::AuthFailed,
            SyncErrorKind::ServerMessage => V::ServerMessage,
            SyncErrorKind::ResyncRequired => V::ResyncRequired,
            SyncErrorKind::DatabaseCheckRequired => V::DatabaseCheckRequired,
            SyncErrorKind::Other => V::Other,
            SyncErrorKind::ClockIncorrect => V::ClockIncorrect,
            SyncErrorKind::SyncNotStarted => V::SyncNotStarted,
        }) as i32
    }
}
