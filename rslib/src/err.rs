// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use failure::{Error, Fail};

pub type Result<T> = std::result::Result<T, AnkiError>;

#[derive(Debug, Fail)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },

    #[fail(display = "invalid card template: {}", info)]
    TemplateError { info: String },
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

impl From<TemplateError> for AnkiError {
    fn from(terr: TemplateError) -> Self {
        AnkiError::TemplateError {
            info: match terr {
                TemplateError::NoClosingBrackets(context) => {
                    format!("missing '}}}}' in '{}'", context)
                }
                TemplateError::ConditionalNotClosed(tag) => format!("missing '{{{{/{}}}}}'", tag),
                TemplateError::ConditionalNotOpen {
                    closed,
                    currently_open,
                } => {
                    if let Some(open) = currently_open {
                        format!("Found {{{{/{}}}}}, but expected {{{{/{}}}}}", closed, open)
                    } else {
                        format!(
                            "Found {{{{/{}}}}}, but missing '{{{{#{}}}}}' or '{{{{^{}}}}}'",
                            closed, closed, closed
                        )
                    }
                }
                TemplateError::FieldNotFound { field, filters } => format!(
                    "found '{{{{{}{}}}}}', but there is no field called '{}'",
                    filters, field, field
                ),
            },
        }
    }
}
