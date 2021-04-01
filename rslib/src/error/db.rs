// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::{types::FromSqlError, Error};
use std::str::Utf8Error;

use super::AnkiError;

#[derive(Debug, PartialEq)]
pub enum DbErrorKind {
    FileTooNew,
    FileTooOld,
    MissingEntity,
    Corrupt,
    Locked,
    Utf8,
    Other,
}

impl From<Error> for AnkiError {
    fn from(err: Error) -> Self {
        if let Error::SqliteFailure(error, Some(reason)) = &err {
            if error.code == rusqlite::ErrorCode::DatabaseBusy {
                return AnkiError::DbError {
                    info: "".to_string(),
                    kind: DbErrorKind::Locked,
                };
            }
            if reason.contains("regex parse error") {
                return AnkiError::InvalidRegex(reason.to_owned());
            }
        }
        AnkiError::DbError {
            info: format!("{:?}", err),
            kind: DbErrorKind::Other,
        }
    }
}

impl From<FromSqlError> for AnkiError {
    fn from(err: FromSqlError) -> Self {
        if let FromSqlError::Other(ref err) = err {
            if let Some(_err) = err.downcast_ref::<Utf8Error>() {
                return AnkiError::DbError {
                    info: "".to_string(),
                    kind: DbErrorKind::Utf8,
                };
            }
        }
        AnkiError::DbError {
            info: format!("{:?}", err),
            kind: DbErrorKind::Other,
        }
    }
}
