// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use failure::{Error, Fail};
use reqwest::StatusCode;
use std::io;

pub type Result<T> = std::result::Result<T, AnkiError>;

#[derive(Debug, Fail)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },

    #[fail(display = "invalid card template: {}", info)]
    TemplateError { info: String, q_side: bool },

    #[fail(display = "I/O error: {}", info)]
    IOError { info: String },

    #[fail(display = "DB error: {}", info)]
    DBError { info: String },

    #[fail(display = "Network error: {:?} {}", kind, info)]
    NetworkError {
        info: String,
        kind: NetworkErrorKind,
    },

    #[fail(display = "Sync error: {:?}, {}", kind, info)]
    SyncError { info: String, kind: SyncErrorKind },

    #[fail(display = "The user interrupted the operation.")]
    Interrupted,
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
        AnkiError::IOError {
            info: format!("{:?}", err),
        }
    }
}

impl From<rusqlite::Error> for AnkiError {
    fn from(err: rusqlite::Error) -> Self {
        AnkiError::DBError {
            info: format!("{:?}", err),
        }
    }
}

impl From<rusqlite::types::FromSqlError> for AnkiError {
    fn from(err: rusqlite::types::FromSqlError) -> Self {
        AnkiError::DBError {
            info: format!("{:?}", err),
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
    Other,
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
        _ => AnkiError::NetworkError {
            info,
            kind: NetworkErrorKind::Other,
        },
    }
}

fn guess_reqwest_error(info: String) -> AnkiError {
    let kind = if info.contains("unreachable") || info.contains("dns") {
        NetworkErrorKind::Offline
    } else {
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
        AnkiError::sync_misc(err.to_string())
    }
}
