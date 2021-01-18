// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::i18n::{tr_args, tr_strs, I18n, TR};
pub use failure::{Error, Fail};
use nom::error::{ErrorKind as NomErrorKind, ParseError as NomParseError};
use reqwest::StatusCode;
use std::{io, str::Utf8Error};
use tempfile::PathPersistError;

pub type Result<T> = std::result::Result<T, AnkiError>;

#[derive(Debug, Fail, PartialEq)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },

    #[fail(display = "invalid card template: {}", info)]
    TemplateError { info: String },

    #[fail(display = "unable to save template {}", ordinal)]
    TemplateSaveError { ordinal: usize },

    #[fail(display = "I/O error: {}", info)]
    IOError { info: String },

    #[fail(display = "DB error: {}", info)]
    DBError { info: String, kind: DBErrorKind },

    #[fail(display = "Network error: {:?} {}", kind, info)]
    NetworkError {
        info: String,
        kind: NetworkErrorKind,
    },

    #[fail(display = "Sync error: {:?}, {}", kind, info)]
    SyncError { info: String, kind: SyncErrorKind },

    #[fail(display = "JSON encode/decode error: {}", info)]
    JSONError { info: String },

    #[fail(display = "Protobuf encode/decode error: {}", info)]
    ProtoError { info: String },

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

    pub fn localized_description(&self, i18n: &I18n) -> String {
        match self {
            AnkiError::SyncError { info, kind } => match kind {
                SyncErrorKind::ServerMessage => info.into(),
                SyncErrorKind::Other => info.into(),
                SyncErrorKind::Conflict => i18n.tr(TR::SyncConflict),
                SyncErrorKind::ServerError => i18n.tr(TR::SyncServerError),
                SyncErrorKind::ClientTooOld => i18n.tr(TR::SyncClientTooOld),
                SyncErrorKind::AuthFailed => i18n.tr(TR::SyncWrongPass),
                SyncErrorKind::ResyncRequired => i18n.tr(TR::SyncResyncRequired),
                SyncErrorKind::ClockIncorrect => i18n.tr(TR::SyncClockOff),
                SyncErrorKind::DatabaseCheckRequired => i18n.tr(TR::SyncSanityCheckFailed),
                // server message
                SyncErrorKind::SyncNotStarted => "sync not started".into(),
            }
            .into(),
            AnkiError::NetworkError { kind, info } => {
                let summary = match kind {
                    NetworkErrorKind::Offline => i18n.tr(TR::NetworkOffline),
                    NetworkErrorKind::Timeout => i18n.tr(TR::NetworkTimeout),
                    NetworkErrorKind::ProxyAuth => i18n.tr(TR::NetworkProxyAuth),
                    NetworkErrorKind::Other => i18n.tr(TR::NetworkOther),
                };
                let details = i18n.trn(TR::NetworkDetails, tr_strs!["details"=>info]);
                format!("{}\n\n{}", summary, details)
            }
            AnkiError::TemplateError { info } => {
                // already localized
                info.into()
            }
            AnkiError::TemplateSaveError { ordinal } => i18n.trn(
                TR::CardTemplatesInvalidTemplateNumber,
                tr_args!["number"=>ordinal+1],
            ),
            AnkiError::DBError { info, kind } => match kind {
                DBErrorKind::Corrupt => info.clone(),
                DBErrorKind::Locked => "Anki already open, or media currently syncing.".into(),
                _ => format!("{:?}", self),
            },
            AnkiError::SearchError(kind) => {
                let reason = match kind {
                    SearchErrorKind::MisplacedAnd => i18n.tr(TR::SearchMisplacedAnd),
                    SearchErrorKind::MisplacedOr => i18n.tr(TR::SearchMisplacedOr),
                    SearchErrorKind::EmptyGroup => i18n.tr(TR::SearchEmptyGroup),
                    SearchErrorKind::UnopenedGroup => i18n.tr(TR::SearchUnopenedGroup),
                    SearchErrorKind::UnclosedGroup => i18n.tr(TR::SearchUnclosedGroup),
                    SearchErrorKind::EmptyQuote => i18n.tr(TR::SearchEmptyQuote),
                    SearchErrorKind::UnclosedQuote => i18n.tr(TR::SearchUnclosedQuote),
                    SearchErrorKind::MissingKey => i18n.tr(TR::SearchMissingKey),
                    SearchErrorKind::UnknownEscape(ctx) => i18n
                        .trn(
                            TR::SearchUnknownEscape,
                            tr_strs!["val"=>(htmlescape::encode_minimal(ctx))],
                        )
                        .into(),
                    SearchErrorKind::InvalidIdList => i18n.tr(TR::SearchInvalidIdList),
                    SearchErrorKind::InvalidState(state) => i18n
                        .trn(
                            TR::SearchInvalidArgument,
                            tr_strs!("term" => "is:", "argument" => state),
                        )
                        .into(),
                    SearchErrorKind::InvalidFlag => i18n.tr(TR::SearchInvalidFlag),
                    SearchErrorKind::InvalidAdded => i18n
                        .trn(
                            TR::SearchInvalidFollowedByPositiveDays,
                            tr_strs!("term" => "added:"),
                        )
                        .into(),
                    SearchErrorKind::InvalidEdited => i18n
                        .trn(
                            TR::SearchInvalidFollowedByPositiveDays,
                            tr_strs!("term" => "edited:"),
                        )
                        .into(),
                    SearchErrorKind::InvalidRatedDays => i18n.tr(TR::SearchInvalidRatedDays),
                    SearchErrorKind::InvalidRatedEase(ctx) => i18n
                        .trn(TR::SearchInvalidRatedEase, tr_strs!["val"=>(ctx)])
                        .into(),
                    SearchErrorKind::InvalidNumber(ctx) => i18n
                        .trn(TR::SearchInvalidNumber, tr_strs!["val"=>(ctx)])
                        .into(),
                    SearchErrorKind::InvalidResched => i18n
                        .trn(
                            TR::SearchInvalidFollowedByPositiveDays,
                            tr_strs!("term" => "resched:"),
                        )
                        .into(),
                    SearchErrorKind::InvalidDupeMid | SearchErrorKind::InvalidDupeText => {
                        // this is an undocumented search keyword, so no translation
                        "`dupe:` arguments were invalid".into()
                    }
                    SearchErrorKind::InvalidPropProperty(prop) => i18n
                        .trn(
                            TR::SearchInvalidArgument,
                            tr_strs!("term" => "prop:", "argument" => prop),
                        )
                        .into(),
                    SearchErrorKind::InvalidPropOperator(ctx) => i18n
                        .trn(TR::SearchInvalidPropOperator, tr_strs!["val"=>(ctx)])
                        .into(),
                    SearchErrorKind::InvalidPropFloat(ctx) => i18n
                        .trn(
                            TR::SearchInvalidPropFloat,
                            tr_strs!["val"=>(htmlescape::encode_minimal(ctx))],
                        )
                        .into(),
                    SearchErrorKind::InvalidPropInteger(ctx) => i18n
                        .trn(
                            TR::SearchInvalidPropInteger,
                            tr_strs!["val"=>(htmlescape::encode_minimal(ctx))],
                        )
                        .into(),
                    SearchErrorKind::InvalidPropUnsigned(ctx) => i18n
                        .trn(
                            TR::SearchInvalidPropUnsigned,
                            tr_strs!["val"=>(htmlescape::encode_minimal(ctx))],
                        )
                        .into(),
                    SearchErrorKind::InvalidDid => i18n.tr(TR::SearchInvalidDid),
                    SearchErrorKind::InvalidMid => i18n.tr(TR::SearchInvalidMid),
                    SearchErrorKind::Regex(text) => text.into(),
                    SearchErrorKind::Other(Some(info)) => info.into(),
                    SearchErrorKind::Other(None) => i18n.tr(TR::SearchInvalidOther),
                };
                i18n.trn(
                    TR::SearchInvalidSearch,
                    tr_args!("reason" => reason.into_owned()),
                )
            }
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
        AnkiError::IOError {
            info: format!("{:?}", err),
        }
    }
}

impl From<rusqlite::Error> for AnkiError {
    fn from(err: rusqlite::Error) -> Self {
        if let rusqlite::Error::SqliteFailure(error, Some(reason)) = &err {
            if error.code == rusqlite::ErrorCode::DatabaseBusy {
                return AnkiError::DBError {
                    info: "".to_string(),
                    kind: DBErrorKind::Locked,
                };
            }
            if reason.contains("regex parse error") {
                return AnkiError::SearchError(SearchErrorKind::Regex(reason.to_owned()));
            }
        }
        AnkiError::DBError {
            info: format!("{:?}", err),
            kind: DBErrorKind::Other,
        }
    }
}

impl From<rusqlite::types::FromSqlError> for AnkiError {
    fn from(err: rusqlite::types::FromSqlError) -> Self {
        if let rusqlite::types::FromSqlError::Other(ref err) = err {
            if let Some(_err) = err.downcast_ref::<Utf8Error>() {
                return AnkiError::DBError {
                    info: "".to_string(),
                    kind: DBErrorKind::Utf8,
                };
            }
        }
        AnkiError::DBError {
            info: format!("{:?}", err),
            kind: DBErrorKind::Other,
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
        AnkiError::JSONError {
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
pub enum DBErrorKind {
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
        AnkiError::IOError {
            info: e.to_string(),
        }
    }
}

#[derive(Debug, PartialEq)]
pub enum ParseError<'a> {
    Anki(&'a str, SearchErrorKind),
    Nom(&'a str, NomErrorKind),
}

#[derive(Debug, PartialEq)]
pub enum SearchErrorKind {
    MisplacedAnd,
    MisplacedOr,
    EmptyGroup,
    UnopenedGroup,
    UnclosedGroup,
    EmptyQuote,
    UnclosedQuote,
    MissingKey,
    UnknownEscape(String),
    InvalidIdList,
    InvalidState(String),
    InvalidFlag,
    InvalidAdded,
    InvalidEdited,
    InvalidRatedDays,
    InvalidRatedEase(String),
    InvalidDupeMid,
    InvalidDupeText,
    InvalidResched,
    InvalidPropProperty(String),
    InvalidPropOperator(String),
    InvalidPropFloat(String),
    InvalidPropInteger(String),
    InvalidPropUnsigned(String),
    InvalidNumber(String),
    InvalidDid,
    InvalidMid,
    Regex(String),
    Other(Option<String>),
}

impl From<ParseError<'_>> for AnkiError {
    fn from(err: ParseError) -> Self {
        match err {
            ParseError::Anki(_, kind) => AnkiError::SearchError(kind),
            ParseError::Nom(_, _) => AnkiError::SearchError(SearchErrorKind::Other(None)),
        }
    }
}

impl From<nom::Err<ParseError<'_>>> for AnkiError {
    fn from(err: nom::Err<ParseError<'_>>) -> Self {
        match err {
            nom::Err::Error(e) => e.into(),
            nom::Err::Failure(e) => e.into(),
            nom::Err::Incomplete(_) => AnkiError::SearchError(SearchErrorKind::Other(None)),
        }
    }
}

impl<'a> NomParseError<&'a str> for ParseError<'a> {
    fn from_error_kind(input: &'a str, kind: NomErrorKind) -> Self {
        ParseError::Nom(input, kind)
    }

    fn append(_: &str, _: NomErrorKind, other: Self) -> Self {
        other
    }
}
