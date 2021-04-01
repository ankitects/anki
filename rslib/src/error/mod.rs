// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod db;
mod network;
mod search;

pub use {
    db::{DbError, DbErrorKind},
    network::{NetworkError, NetworkErrorKind, SyncError, SyncErrorKind},
    search::{ParseError, SearchErrorKind},
};

use crate::i18n::I18n;
use std::{fmt::Display, io};
use tempfile::PathPersistError;

pub type Result<T, E = AnkiError> = std::result::Result<T, E>;

#[derive(Debug, PartialEq)]
pub enum AnkiError {
    InvalidInput(String),
    TemplateError(String),
    TemplateSaveError { ordinal: usize },
    IoError(String),
    DbError(DbError),
    NetworkError(NetworkError),
    SyncError(SyncError),
    JsonError(String),
    ProtoError(String),
    ParseNumError,
    Interrupted,
    CollectionNotOpen,
    CollectionAlreadyOpen,
    NotFound,
    Existing,
    DeckIsFiltered,
    SearchError(SearchErrorKind),
    FilteredDeckEmpty,
    InvalidRegex(String),
}

impl Display for AnkiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}", self)
    }
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput(s.into())
    }

    pub fn localized_description(&self, tr: &I18n) -> String {
        match self {
            AnkiError::SyncError(err) => err.localized_description(tr),
            AnkiError::NetworkError(err) => err.localized_description(tr),
            AnkiError::TemplateError(info) => {
                // already localized
                info.into()
            }
            AnkiError::TemplateSaveError { ordinal } => tr
                .card_templates_invalid_template_number(ordinal + 1)
                .into(),
            AnkiError::DbError(err) => err.localized_description(tr),
            AnkiError::SearchError(kind) => kind.localized_description(&tr),
            AnkiError::InvalidInput(info) => {
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
        AnkiError::IoError(format!("{:?}", err))
    }
}

impl From<serde_json::Error> for AnkiError {
    fn from(err: serde_json::Error) -> Self {
        AnkiError::JsonError(err.to_string())
    }
}

impl From<prost::EncodeError> for AnkiError {
    fn from(err: prost::EncodeError) -> Self {
        AnkiError::ProtoError(err.to_string())
    }
}

impl From<prost::DecodeError> for AnkiError {
    fn from(err: prost::DecodeError) -> Self {
        AnkiError::ProtoError(err.to_string())
    }
}

impl From<PathPersistError> for AnkiError {
    fn from(e: PathPersistError) -> Self {
        AnkiError::IoError(e.to_string())
    }
}

impl From<regex::Error> for AnkiError {
    fn from(err: regex::Error) -> Self {
        AnkiError::InvalidRegex(err.to_string())
    }
}
