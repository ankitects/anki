// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod db;
mod filtered;
mod invalid_input;
mod network;
mod search;

use std::{io, path::Path};

pub use db::{DbError, DbErrorKind};
pub use filtered::{CustomStudyError, FilteredDeckError};
pub use network::{NetworkError, NetworkErrorKind, SyncError, SyncErrorKind};
pub use search::{ParseError, SearchErrorKind};
use snafu::Snafu;
use tempfile::PathPersistError;

pub use self::invalid_input::{InvalidInputContext, InvalidInputError};
use crate::{i18n::I18n, links::HelpPage};

pub type Result<T, E = AnkiError> = std::result::Result<T, E>;

#[derive(Debug, PartialEq, Eq, Snafu)]
pub enum AnkiError {
    #[snafu(context(false))]
    InvalidInput {
        source: InvalidInputError,
    },
    TemplateError {
        info: String,
    },
    #[snafu(context(false))]
    CardTypeError {
        source: CardTypeError,
    },
    IoError {
        info: String,
    },
    #[snafu(context(false))]
    FileIoError {
        source: FileIoError,
    },
    #[snafu(context(false))]
    DbError {
        source: DbError,
    },
    #[snafu(context(false))]
    NetworkError {
        source: NetworkError,
    },
    #[snafu(context(false))]
    SyncError {
        source: SyncError,
    },
    JsonError {
        info: String,
    },
    ProtoError {
        info: String,
    },
    ParseNumError,
    Interrupted,
    CollectionNotOpen,
    CollectionAlreadyOpen,
    NotFound,
    /// Indicates an absent card or note, but (unlike [AnkiError::NotFound]) in
    /// a non-critical context like the browser table, where deleted ids are
    /// deliberately not removed.
    Deleted,
    Existing,
    #[snafu(context(false))]
    FilteredDeckError {
        source: FilteredDeckError,
    },
    #[snafu(context(false))]
    SearchError {
        source: SearchErrorKind,
    },
    InvalidRegex {
        info: String,
    },
    UndoEmpty,
    MultipleNotetypesSelected,
    DatabaseCheckRequired,
    MediaCheckRequired,
    #[snafu(context(false))]
    CustomStudyError {
        source: CustomStudyError,
    },
    #[snafu(context(false))]
    ImportError {
        source: ImportError,
    },
    InvalidId,
}

// error helpers
impl AnkiError {
    pub fn localized_description(&self, tr: &I18n) -> String {
        match self {
            AnkiError::SyncError { source } => source.localized_description(tr),
            AnkiError::NetworkError { source } => source.localized_description(tr),
            AnkiError::TemplateError { info: source } => {
                // already localized
                source.into()
            }
            AnkiError::CardTypeError { source } => {
                let header =
                    tr.card_templates_invalid_template_number(source.ordinal + 1, &source.notetype);
                let details = match source.source {
                    CardTypeErrorDetails::TemplateParseError
                    | CardTypeErrorDetails::NoSuchField => tr.card_templates_see_preview(),
                    CardTypeErrorDetails::NoFrontField => tr.card_templates_no_front_field(),
                    CardTypeErrorDetails::Duplicate { index } => {
                        tr.card_templates_identical_front(index + 1)
                    }
                    CardTypeErrorDetails::MissingCloze => tr.card_templates_missing_cloze(),
                    CardTypeErrorDetails::ExtraneousCloze => tr.card_templates_extraneous_cloze(),
                };
                format!("{}<br>{}", header, details)
            }
            AnkiError::DbError { source } => source.localized_description(tr),
            AnkiError::SearchError { source } => source.localized_description(tr),
            AnkiError::InvalidInput { source } => {
                if source.message.is_empty() {
                    tr.errors_invalid_input_empty().into()
                } else {
                    tr.errors_invalid_input_details(source.message.as_str())
                        .into()
                }
            }
            AnkiError::ParseNumError => tr.errors_parse_number_fail().into(),
            AnkiError::FilteredDeckError { source } => source.localized_description(tr),
            AnkiError::InvalidRegex { info: source } => format!("<pre>{}</pre>", source),
            AnkiError::MultipleNotetypesSelected => tr.errors_multiple_notetypes_selected().into(),
            AnkiError::DatabaseCheckRequired => tr.errors_please_check_database().into(),
            AnkiError::MediaCheckRequired => tr.errors_please_check_media().into(),
            AnkiError::CustomStudyError { source } => source.localized_description(tr),
            AnkiError::ImportError { source } => source.localized_description(tr),
            AnkiError::Deleted => tr.browsing_row_deleted().into(),
            AnkiError::InvalidId => tr.errors_invalid_ids().into(),
            AnkiError::IoError { .. }
            | AnkiError::JsonError { .. }
            | AnkiError::ProtoError { .. }
            | AnkiError::Interrupted
            | AnkiError::CollectionNotOpen
            | AnkiError::CollectionAlreadyOpen
            | AnkiError::NotFound
            | AnkiError::Existing
            | AnkiError::UndoEmpty => format!("{:?}", self),
            AnkiError::FileIoError { source } => {
                format!("{}: {}", source.path, source.error)
            }
        }
    }

    pub fn help_page(&self) -> Option<HelpPage> {
        match self {
            Self::CardTypeError {
                source: CardTypeError { source, .. },
            } => Some(match source {
                CardTypeErrorDetails::TemplateParseError | CardTypeErrorDetails::NoSuchField => {
                    HelpPage::CardTypeTemplateError
                }
                CardTypeErrorDetails::Duplicate { .. } => HelpPage::CardTypeDuplicate,
                CardTypeErrorDetails::NoFrontField => HelpPage::CardTypeNoFrontField,
                CardTypeErrorDetails::MissingCloze => HelpPage::CardTypeMissingCloze,
                CardTypeErrorDetails::ExtraneousCloze => HelpPage::CardTypeExtraneousCloze,
            }),
            _ => None,
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
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
        AnkiError::InvalidRegex {
            info: err.to_string(),
        }
    }
}

#[derive(Debug, PartialEq, Eq, Snafu)]
#[snafu(visibility(pub))]
pub struct CardTypeError {
    pub notetype: String,
    pub ordinal: usize,
    pub source: CardTypeErrorDetails,
}

#[derive(Debug, PartialEq, Eq, Snafu)]
#[snafu(visibility(pub))]
pub enum CardTypeErrorDetails {
    TemplateParseError,
    Duplicate { index: usize },
    NoFrontField,
    NoSuchField,
    MissingCloze,
    ExtraneousCloze,
}

#[derive(Debug, PartialEq, Eq, Clone, Snafu)]
pub enum ImportError {
    Corrupt,
    TooNew,
    MediaImportFailed { info: String },
    NoFieldColumn,
}

impl ImportError {
    fn localized_description(&self, tr: &I18n) -> String {
        match self {
            ImportError::Corrupt => tr.importing_the_provided_file_is_not_a(),
            ImportError::TooNew => tr.errors_collection_too_new(),
            ImportError::MediaImportFailed { info } => {
                tr.importing_failed_to_import_media_file(info)
            }
            ImportError::NoFieldColumn => tr.importing_file_must_contain_field_column(),
        }
        .into()
    }
}

#[derive(Debug, PartialEq, Eq, Clone, Snafu)]
pub struct FileIoError {
    pub path: String,
    pub error: String,
}

impl AnkiError {
    pub(crate) fn file_io_error<P: AsRef<Path>>(err: std::io::Error, path: P) -> Self {
        AnkiError::FileIoError {
            source: FileIoError::new(err, path.as_ref()),
        }
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
