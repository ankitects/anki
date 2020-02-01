// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use failure::{Error, Fail};
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

    #[fail(display = "Network error: {}", info)]
    NetworkError { info: String },

    #[fail(display = "AnkiWeb authentication failed.")]
    AnkiWebAuthenticationFailed,

    #[fail(display = "AnkiWeb error: {}", info)]
    AnkiWebMiscError { info: String },
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput { info: s.into() }
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

impl From<reqwest::Error> for AnkiError {
    fn from(err: reqwest::Error) -> Self {
        AnkiError::NetworkError {
            info: format!("{:?}", err),
        }
    }
}

impl From<zip::result::ZipError> for AnkiError {
    fn from(err: zip::result::ZipError) -> Self {
        AnkiError::AnkiWebMiscError {
            info: format!("{:?}", err),
        }
    }
}

impl From<serde_json::Error> for AnkiError {
    fn from(err: serde_json::Error) -> Self {
        AnkiError::AnkiWebMiscError {
            info: format!("{:?}", err),
        }
    }
}
