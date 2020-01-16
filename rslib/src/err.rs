// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use failure::{Error, Fail};

pub type Result<T> = std::result::Result<T, AnkiError>;

#[derive(Debug, Fail)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },

    #[fail(display = "invalid card template: {}", info)]
    TemplateParseError { info: String },
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput { info: s.into() }
    }
}

#[derive(Debug)]
pub enum TemplateError {
    NoClosingBrackets(String),
    ConditionalNotClosed(String),
    ConditionalNotOpen(String),
}

impl From<TemplateError> for AnkiError {
    fn from(terr: TemplateError) -> Self {
        AnkiError::TemplateParseError {
            info: match terr {
                TemplateError::NoClosingBrackets(context) => {
                    format!("expected '{{{{field name}}}}', found '{}'", context)
                }
                TemplateError::ConditionalNotClosed(tag) => format!("missing '{{{{/{}}}}}'", tag),
                TemplateError::ConditionalNotOpen(tag) => {
                    format!("missing '{{{{#{}}}}}' or '{{{{^{}}}}}'", tag, tag)
                }
            },
        }
    }
}
