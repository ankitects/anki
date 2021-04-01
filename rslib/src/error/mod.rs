// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod search;

pub use search::{ParseError, SearchErrorKind};

use crate::i18n::I18n;
pub use failure::{Error, Fail};
use reqwest::StatusCode;
use std::{io, str::Utf8Error};
use tempfile::PathPersistError;

pub type Result<T, E = AnkiError> = std::result::Result<T, E>;

#[derive(Debug, Fail, PartialEq)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },

    #[fail(display = "invalid card template: {}", info)]
    TemplateError { info: String },

    #[fail(display = "unable to save template {}", ordinal)]
    TemplateSaveError { ordinal: usize },

    #[fail(display = "I/O error: {}", info)]
    IoError { info: String },

    #[fail(display = "DB error: {}", info)]
    DbError { info: String, kind: DbErrorKind },

    #[fail(display = "Network error: {:?} {}", kind, info)]
    NetworkError {
        info: String,
        kind: NetworkErrorKind,
    },

    #[fail(display = "Sync error: {:?}, {}", kind, info)]
    SyncError { info: String, kind: SyncErrorKind },

    #[fail(display = "JSON encode/decode error: {}", info)]
    JsonError { info: String },

    #[fail(display = "Protobuf encode/decode error: {}", info)]
    ProtoError { info: String },

    #[fail(display = "Unable to parse number")]
    ParseNumError,

    #[fail(display = "The user interrupted the operation.")]
    Interrupted,

    #[fail(display = "Operation requires an open collection.")]
    CollectionNotOpen,

    #[fail(display = "Close the existing collection first.")]
    CollectionAlreadyOpen,

    #[fail(display = "A requested item was not found.")]
    NotFound,

    #[fail(display = "The provided item already exists.")]
    Existing,

    #[fail(display = "Unable to place item in/under a filtered deck.")]
    DeckIsFiltered,

    #[fail(display = "Invalid search.")]
    SearchError(SearchErrorKind),

    #[fail(display = "Provided search(es) did not match any cards.")]
    FilteredDeckEmpty,
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput { info: s.into() }
    }

    pub(crate) fn server_message<S: Into<String>>(msg: S) -> AnkiError {
        AnkiError::SyncError {
            info: msg.into(),
            kind: SyncErrorKind::ServerMessage,
        }
    }

    pub(crate) fn sync_misc<S: Into<String>>(msg: S) -> AnkiError {
        AnkiError::SyncError {
            info: msg.into(),
            kind: SyncErrorKind::Other,
        }
    }

    pub fn localized_description(&self, tr: &I18n) -> String {
        match self {
            AnkiError::SyncError { info, kind } => match kind {
                SyncErrorKind::ServerMessage => info.into(),
                SyncErrorKind::Other => info.into(),
                SyncErrorKind::Conflict => tr.sync_conflict(),
                SyncErrorKind::ServerError => tr.sync_server_error(),
                SyncErrorKind::ClientTooOld => tr.sync_client_too_old(),
                SyncErrorKind::AuthFailed => tr.sync_wrong_pass(),
                SyncErrorKind::ResyncRequired => tr.sync_resync_required(),
                SyncErrorKind::ClockIncorrect => tr.sync_clock_off(),
                SyncErrorKind::DatabaseCheckRequired => tr.sync_sanity_check_failed(),
                // server message
                SyncErrorKind::SyncNotStarted => "sync not started".into(),
            }
            .into(),
            AnkiError::NetworkError { kind, info } => {
                let summary = match kind {
                    NetworkErrorKind::Offline => tr.network_offline(),
                    NetworkErrorKind::Timeout => tr.network_timeout(),
                    NetworkErrorKind::ProxyAuth => tr.network_proxy_auth(),
                    NetworkErrorKind::Other => tr.network_other(),
                };
                let details = tr.network_details(info.as_str());
                format!("{}\n\n{}", summary, details)
            }
            AnkiError::TemplateError { info } => {
                // already localized
                info.into()
            }
            AnkiError::TemplateSaveError { ordinal } => tr
                .card_templates_invalid_template_number(ordinal + 1)
                .into(),
            AnkiError::DbError { info, kind } => match kind {
                DbErrorKind::Corrupt => info.clone(),
                DbErrorKind::Locked => "Anki already open, or media currently syncing.".into(),
                _ => format!("{:?}", self),
            },
            AnkiError::SearchError(kind) => kind.localized_description(&tr),
            AnkiError::InvalidInput { info } => {
                if info.is_empty() {
                    tr.errors_invalid_input_empty().into()
                } else {
                    tr.errors_invalid_input_details(info.as_str()).into()
                }
            }
            AnkiError::ParseNumError => tr.errors_parse_number_fail().into(),
            AnkiError::DeckIsFiltered => tr.errors_filtered_parent_deck().into(),
            AnkiError::FilteredDeckEmpty => tr.decks_filtered_deck_search_empty().into(),
            _ => format!("{:?}", self),
        }
    }
}

#[derive(Debug, PartialEq)]
pub enum TemplateError {
    NoClosingBrackets(String),
    ConditionalNotClosed(String),
    ConditionalNotOpen {
        closed: String,
        currently_open: Option<String>,
    },
    FieldNotFound {
        filters: String,
        field: String,
    },
}

impl From<io::Error> for AnkiError {
    fn from(err: io::Error) -> Self {
        AnkiError::IoError {
            info: format!("{:?}", err),
        }
    }
}

impl From<rusqlite::Error> for AnkiError {
    fn from(err: rusqlite::Error) -> Self {
        if let rusqlite::Error::SqliteFailure(error, Some(reason)) = &err {
            if error.code == rusqlite::ErrorCode::DatabaseBusy {
                return AnkiError::DbError {
                    info: "".to_string(),
                    kind: DbErrorKind::Locked,
                };
            }
            if reason.contains("regex parse error") {
                return AnkiError::SearchError(SearchErrorKind::Regex(reason.to_owned()));
            }
        }
        AnkiError::DbError {
            info: format!("{:?}", err),
            kind: DbErrorKind::Other,
        }
    }
}

impl From<rusqlite::types::FromSqlError> for AnkiError {
    fn from(err: rusqlite::types::FromSqlError) -> Self {
        if let rusqlite::types::FromSqlError::Other(ref err) = err {
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

#[derive(Debug, PartialEq)]
pub enum NetworkErrorKind {
    Offline,
    Timeout,
    ProxyAuth,
    Other,
}

impl From<reqwest::Error> for AnkiError {
    fn from(err: reqwest::Error) -> Self {
        let url = err.url().map(|url| url.as_str()).unwrap_or("");
        let str_err = format!("{}", err);
        // strip url from error to avoid exposing keys
        let info = str_err.replace(url, "");

        if err.is_timeout() {
            AnkiError::NetworkError {
                info,
                kind: NetworkErrorKind::Timeout,
            }
        } else if err.is_status() {
            error_for_status_code(info, err.status().unwrap())
        } else {
            guess_reqwest_error(info)
        }
    }
}

#[derive(Debug, PartialEq)]
pub enum SyncErrorKind {
    Conflict,
    ServerError,
    ClientTooOld,
    AuthFailed,
    ServerMessage,
    ClockIncorrect,
    Other,
    ResyncRequired,
    DatabaseCheckRequired,
    SyncNotStarted,
}

fn error_for_status_code(info: String, code: StatusCode) -> AnkiError {
    use reqwest::StatusCode as S;
    match code {
        S::PROXY_AUTHENTICATION_REQUIRED => AnkiError::NetworkError {
            info,
            kind: NetworkErrorKind::ProxyAuth,
        },
        S::CONFLICT => AnkiError::SyncError {
            info,
            kind: SyncErrorKind::Conflict,
        },
        S::FORBIDDEN => AnkiError::SyncError {
            info,
            kind: SyncErrorKind::AuthFailed,
        },
        S::NOT_IMPLEMENTED => AnkiError::SyncError {
            info,
            kind: SyncErrorKind::ClientTooOld,
        },
        S::INTERNAL_SERVER_ERROR | S::BAD_GATEWAY | S::GATEWAY_TIMEOUT | S::SERVICE_UNAVAILABLE => {
            AnkiError::SyncError {
                info,
                kind: SyncErrorKind::ServerError,
            }
        }
        S::BAD_REQUEST => AnkiError::SyncError {
            info,
            kind: SyncErrorKind::DatabaseCheckRequired,
        },
        _ => AnkiError::NetworkError {
            info,
            kind: NetworkErrorKind::Other,
        },
    }
}

fn guess_reqwest_error(mut info: String) -> AnkiError {
    if info.contains("dns error: cancelled") {
        return AnkiError::Interrupted;
    }
    let kind = if info.contains("unreachable") || info.contains("dns") {
        NetworkErrorKind::Offline
    } else if info.contains("timed out") {
        NetworkErrorKind::Timeout
    } else {
        if info.contains("invalid type") {
            info = format!(
                "{} {} {}\n\n{}",
                "Please force a full sync in the Preferences screen to bring your devices into sync.",
                "Then, please use the Check Database feature, and sync to your other devices.",
                "If problems persist, please post on the support forum.",
                info,
            );
        }

        NetworkErrorKind::Other
    };
    AnkiError::NetworkError { info, kind }
}

impl From<zip::result::ZipError> for AnkiError {
    fn from(err: zip::result::ZipError) -> Self {
        AnkiError::sync_misc(err.to_string())
    }
}

impl From<serde_json::Error> for AnkiError {
    fn from(err: serde_json::Error) -> Self {
        AnkiError::JsonError {
            info: err.to_string(),
        }
    }
}

impl From<prost::EncodeError> for AnkiError {
    fn from(err: prost::EncodeError) -> Self {
        AnkiError::ProtoError {
            info: err.to_string(),
        }
    }
}

impl From<prost::DecodeError> for AnkiError {
    fn from(err: prost::DecodeError) -> Self {
        AnkiError::ProtoError {
            info: err.to_string(),
        }
    }
}

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

impl From<PathPersistError> for AnkiError {
    fn from(e: PathPersistError) -> Self {
        AnkiError::IoError {
            info: e.to_string(),
        }
    }
}

impl From<regex::Error> for AnkiError {
    fn from(_err: regex::Error) -> Self {
        AnkiError::InvalidInput {
            info: "invalid regex".into(),
        }
    }
}
