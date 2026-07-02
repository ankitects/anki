// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::error::DbError;
use crate::error::DbErrorKind as DB;
use crate::error::FilteredDeckError;
use crate::error::InvalidInputError;
use crate::error::NetworkError;
use crate::error::NetworkErrorKind as Net;
use crate::error::NotFoundError;
use crate::error::SearchErrorKind;
use crate::error::SyncError;
use crate::error::SyncErrorKind as Sync;
use crate::prelude::AnkiError;

pub(crate) fn debug_produce_error(s: &str) -> AnkiError {
    let info = "error_value".to_string();
    match s {
        "TemplateError" => AnkiError::TemplateError { info },
        "DbErrorFileTooNew" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::FileTooNew,
            },
        },
        "DbErrorFileTooOld" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::FileTooOld,
            },
        },
        "DbErrorMissingEntity" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::MissingEntity,
            },
        },
        "DbErrorCorrupt" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::Corrupt,
            },
        },
        "DbErrorLocked" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::Locked,
            },
        },
        "DbErrorOther" => AnkiError::DbError {
            source: DbError {
                info,
                kind: DB::Other,
            },
        },
        "NetworkError" => AnkiError::NetworkError {
            source: NetworkError {
                info,
                kind: Net::Offline,
            },
        },
        "SyncErrorConflict" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::Conflict,
            },
        },
        "SyncErrorServerError" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::ServerError,
            },
        },
        "SyncErrorClientTooOld" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::ClientTooOld,
            },
        },
        "SyncErrorAuthFailed" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::AuthFailed,
            },
        },
        "SyncErrorServerMessage" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::ServerMessage,
            },
        },
        "SyncErrorClockIncorrect" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::ClockIncorrect,
            },
        },
        "SyncErrorOther" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::Other,
            },
        },
        "SyncErrorResyncRequired" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::ResyncRequired,
            },
        },
        "SyncErrorDatabaseCheckRequired" => AnkiError::SyncError {
            source: SyncError {
                info,
                kind: Sync::DatabaseCheckRequired,
            },
        },
        "JSONError" => AnkiError::JsonError { info },
        "ProtoError" => AnkiError::ProtoError { info },
        "Interrupted" => AnkiError::Interrupted,
        "CollectionNotOpen" => AnkiError::CollectionNotOpen,
        "CollectionAlreadyOpen" => AnkiError::CollectionAlreadyOpen,
        "NotFound" => AnkiError::NotFound {
            source: NotFoundError {
                type_name: "".to_string(),
                identifier: "".to_string(),
                backtrace: None,
            },
        },
        "Existing" => AnkiError::Existing,
        "FilteredDeckError" => AnkiError::FilteredDeckError {
            source: FilteredDeckError::FilteredDeckRequired,
        },
        "SearchError" => AnkiError::SearchError {
            source: SearchErrorKind::EmptyGroup,
        },
        _ => AnkiError::InvalidInput {
            source: InvalidInputError {
                message: info,
                source: None,
                backtrace: None,
            },
        },
    }
}
