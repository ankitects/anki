// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod db;
mod filtered;
mod network;
mod search;

use std::{fmt::Display, io, path::Path};

pub use db::{DbError, DbErrorKind};
pub use filtered::{CustomStudyError, FilteredDeckError};
pub use network::{NetworkError, NetworkErrorKind, SyncError, SyncErrorKind};
pub use search::{ParseError, SearchErrorKind};
use tempfile::PathPersistError;

use crate::i18n::I18n;

pub type Result<T, E = AnkiError> = std::result::Result<T, E>;

#[derive(Debug, PartialEq)]
pub enum AnkiError {
    InvalidInput(String),
    TemplateError(String),
    TemplateSaveError(TemplateSaveError),
    IoError(String),
    FileIoError(FileIoError),
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
    FilteredDeckError(FilteredDeckError),
    SearchError(SearchErrorKind),
    InvalidRegex(String),
    UndoEmpty,
    MultipleNotetypesSelected,
    DatabaseCheckRequired,
    MediaCheckRequired,
    CustomStudyError(CustomStudyError),
    ImportError(ImportError),
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
            AnkiError::TemplateSaveError(err) => {
                let header =
                    tr.card_templates_invalid_template_number(err.ordinal + 1, &err.notetype);
                let details = match err.details {
                    TemplateSaveErrorDetails::TemplateError
                    | TemplateSaveErrorDetails::NoSuchField => tr.card_templates_see_preview(),
                    TemplateSaveErrorDetails::NoFrontField => tr.card_templates_no_front_field(),
                    TemplateSaveErrorDetails::Duplicate(i) => {
                        tr.card_templates_identical_front(i + 1)
                    }
                    TemplateSaveErrorDetails::MissingCloze => tr.card_templates_missing_cloze(),
                    TemplateSaveErrorDetails::ExtraneousCloze => {
                        tr.card_templates_extraneous_cloze()
                    }
                };
                format!("{}<br>{}", header, details)
            }
            AnkiError::DbError(err) => err.localized_description(tr),
            AnkiError::SearchError(kind) => kind.localized_description(tr),
            AnkiError::InvalidInput(info) => {
                if info.is_empty() {
                    tr.errors_invalid_input_empty().into()
                } else {
                    tr.errors_invalid_input_details(info.as_str()).into()
                }
            }
            AnkiError::ParseNumError => tr.errors_parse_number_fail().into(),
            AnkiError::FilteredDeckError(err) => err.localized_description(tr),
            AnkiError::InvalidRegex(err) => format!("<pre>{}</pre>", err),
            AnkiError::MultipleNotetypesSelected => tr.errors_multiple_notetypes_selected().into(),
            AnkiError::DatabaseCheckRequired => tr.errors_please_check_database().into(),
            AnkiError::MediaCheckRequired => tr.errors_please_check_media().into(),
            AnkiError::CustomStudyError(err) => err.localized_description(tr),
            AnkiError::ImportError(err) => err.localized_description(tr),
            AnkiError::IoError(_)
            | AnkiError::JsonError(_)
            | AnkiError::ProtoError(_)
            | AnkiError::Interrupted
            | AnkiError::CollectionNotOpen
            | AnkiError::CollectionAlreadyOpen
            | AnkiError::NotFound
            | AnkiError::Existing
            | AnkiError::UndoEmpty => format!("{:?}", self),
            AnkiError::FileIoError(err) => {
                format!("{}: {}", err.path, err.error)
            }
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
    NoSuchConditional(String),
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

#[derive(Debug, PartialEq)]
pub struct TemplateSaveError {
    pub notetype: String,
    pub ordinal: usize,
    pub details: TemplateSaveErrorDetails,
}

#[derive(Debug, PartialEq)]
pub enum TemplateSaveErrorDetails {
    TemplateError,
    Duplicate(usize),
    NoFrontField,
    NoSuchField,
    MissingCloze,
    ExtraneousCloze,
}

#[derive(Debug, PartialEq, Clone)]
pub enum ImportError {
    Corrupt,
    TooNew,
    MediaImportFailed(String),
}

impl ImportError {
    fn localized_description(&self, tr: &I18n) -> String {
        match self {
            ImportError::Corrupt => tr.importing_the_provided_file_is_not_a(),
            ImportError::TooNew => tr.errors_collection_too_new(),
            ImportError::MediaImportFailed(err) => tr.importing_failed_to_import_media_file(err),
        }
        .into()
    }
}

#[derive(Debug, PartialEq, Clone)]

pub struct FileIoError {
    pub path: String,
    pub error: String,
}

impl AnkiError {
    pub(crate) fn file_io_error<P: AsRef<Path>>(err: std::io::Error, path: P) -> Self {
        AnkiError::FileIoError(FileIoError::new(err, path.as_ref()))
    }
}

impl FileIoError {
    pub fn new(err: std::io::Error, path: &Path) -> FileIoError {
        FileIoError {
            path: path.to_string_lossy().to_string(),
            error: err.to_string(),
        }
    }
}
