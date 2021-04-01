// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod db;
mod network;
mod search;

pub use {
    db::DbErrorKind,
    network::{NetworkError, NetworkErrorKind, SyncError, SyncErrorKind},
    search::{ParseError, SearchErrorKind},
};

use crate::i18n::I18n;
pub use failure::{Error, Fail};
use std::io;
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

    #[fail(display = "Network error: {:?}", _0)]
    NetworkError(NetworkError),

    #[fail(display = "Sync error: {:?}", _0)]
    SyncError(SyncError),

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

    #[fail(display = "Invalid regex provided.")]
    InvalidRegex(String),
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput { info: s.into() }
    }

    pub(crate) fn server_message<S: Into<String>>(msg: S) -> AnkiError {
        AnkiError::sync_error(msg, SyncErrorKind::ServerMessage)
    }

    pub fn localized_description(&self, tr: &I18n) -> String {
        match self {
            AnkiError::SyncError(err) => err.localized_description(tr),
            AnkiError::NetworkError(err) => err.localized_description(tr),
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

impl From<PathPersistError> for AnkiError {
    fn from(e: PathPersistError) -> Self {
        AnkiError::IoError {
            info: e.to_string(),
        }
    }
}

impl From<regex::Error> for AnkiError {
    fn from(err: regex::Error) -> Self {
        AnkiError::InvalidRegex(err.to_string())
    }
}
