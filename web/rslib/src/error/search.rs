// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::num::ParseIntError;

use anki_i18n::I18n;
use nom::error::ErrorKind as NomErrorKind;
use nom::error::ParseError as NomParseError;
use snafu::Snafu;

use super::AnkiError;

#[derive(Debug, PartialEq, Eq)]
pub enum ParseError<'a> {
    Anki(&'a str, SearchErrorKind),
    Nom(&'a str, NomErrorKind),
}

#[derive(Debug, PartialEq, Eq, Snafu)]
pub enum SearchErrorKind {
    MisplacedAnd,
    MisplacedOr,
    EmptyGroup,
    UnopenedGroup,
    UnclosedGroup,
    EmptyQuote,
    UnclosedQuote,
    MissingKey,
    UnknownEscape { provided: String },
    InvalidState { provided: String },
    InvalidFlag,
    InvalidPropProperty { provided: String },
    InvalidPropOperator { provided: String },
    InvalidNumber { provided: String, context: String },
    InvalidWholeNumber { provided: String, context: String },
    InvalidPositiveWholeNumber { provided: String, context: String },
    InvalidNegativeWholeNumber { provided: String, context: String },
    InvalidAnswerButton { provided: String, context: String },
    Other { info: Option<String> },
}

impl From<ParseError<'_>> for AnkiError {
    fn from(err: ParseError) -> Self {
        match err {
            ParseError::Anki(_, kind) => AnkiError::SearchError { source: kind },
            ParseError::Nom(_, _) => AnkiError::SearchError {
                source: SearchErrorKind::Other { info: None },
            },
        }
    }
}

impl From<nom::Err<ParseError<'_>>> for AnkiError {
    fn from(err: nom::Err<ParseError<'_>>) -> Self {
        match err {
            nom::Err::Error(e) => e.into(),
            nom::Err::Failure(e) => e.into(),
            nom::Err::Incomplete(_) => AnkiError::SearchError {
                source: SearchErrorKind::Other { info: None },
            },
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

impl From<ParseIntError> for AnkiError {
    fn from(_err: ParseIntError) -> Self {
        AnkiError::ParseNumError
    }
}

impl SearchErrorKind {
    pub fn message(&self, tr: &I18n) -> String {
        let reason = match self {
            SearchErrorKind::MisplacedAnd => tr.search_misplaced_and(),
            SearchErrorKind::MisplacedOr => tr.search_misplaced_or(),
            SearchErrorKind::EmptyGroup => tr.search_empty_group(),
            SearchErrorKind::UnopenedGroup => tr.search_unopened_group(),
            SearchErrorKind::UnclosedGroup => tr.search_unclosed_group(),
            SearchErrorKind::EmptyQuote => tr.search_empty_quote(),
            SearchErrorKind::UnclosedQuote => tr.search_unclosed_quote(),
            SearchErrorKind::MissingKey => tr.search_missing_key(),
            SearchErrorKind::UnknownEscape { provided } => {
                tr.search_unknown_escape(provided.replace('`', "'"))
            }
            SearchErrorKind::InvalidState { provided } => {
                tr.search_invalid_argument("is:", provided.replace('`', "'"))
            }

            SearchErrorKind::InvalidFlag => tr.search_invalid_flag_2(),
            SearchErrorKind::InvalidPropProperty { provided } => {
                tr.search_invalid_argument("prop:", provided.replace('`', "'"))
            }
            SearchErrorKind::InvalidPropOperator { provided } => {
                tr.search_invalid_prop_operator(provided.as_str())
            }
            SearchErrorKind::Other { info: Some(info) } => info.into(),
            SearchErrorKind::Other { info: None } => tr.search_invalid_other(),
            SearchErrorKind::InvalidNumber { provided, context } => {
                tr.search_invalid_number(context.replace('`', "'"), provided.replace('`', "'"))
            }

            SearchErrorKind::InvalidWholeNumber { provided, context } => tr
                .search_invalid_whole_number(context.replace('`', "'"), provided.replace('`', "'")),

            SearchErrorKind::InvalidPositiveWholeNumber { provided, context } => tr
                .search_invalid_positive_whole_number(
                    context.replace('`', "'"),
                    provided.replace('`', "'"),
                ),

            SearchErrorKind::InvalidNegativeWholeNumber { provided, context } => tr
                .search_invalid_negative_whole_number(
                    context.replace('`', "'"),
                    provided.replace('`', "'"),
                ),

            SearchErrorKind::InvalidAnswerButton { provided, context } => tr
                .search_invalid_answer_button(
                    context.replace('`', "'"),
                    provided.replace('`', "'"),
                ),
        };
        tr.search_invalid_search(reason).into()
    }
}
