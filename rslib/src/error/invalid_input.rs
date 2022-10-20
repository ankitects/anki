// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use snafu::{Backtrace, OptionExt, ResultExt, Snafu};

use crate::prelude::*;

/// General-purpose error for unexpected [Err]s, [None]s, and other
/// violated constraints.
#[derive(Debug, Snafu)]
#[snafu(visibility(pub), display("{message}"), whatever)]
pub struct InvalidInputError {
    pub message: String,
    #[snafu(source(from(Box<dyn std::error::Error + Send + Sync>, Some)))]
    source: Option<Box<dyn std::error::Error + Send + Sync>>,
    pub backtrace: Option<Backtrace>,
}

impl InvalidInputError {
    pub fn message(&self) -> String {
        self.message.clone()
    }

    pub fn context(&self) -> String {
        if let Some(source) = &self.source {
            format!("{}", source)
        } else {
            String::new()
        }
    }
}

impl PartialEq for InvalidInputError {
    fn eq(&self, other: &Self) -> bool {
        self.message == other.message
    }
}

impl Eq for InvalidInputError {}

/// Allows generating [AnkiError::InvalidInput] from [Option::None] and the
/// typical [core::result::Result::Err].
pub trait OkOrInvalid {
    type Value;
    fn ok_or_invalid(self, message: impl Into<String>) -> Result<Self::Value>;
}

impl<T> OkOrInvalid for Option<T> {
    type Value = T;

    fn ok_or_invalid(self, message: impl Into<String>) -> Result<T> {
        self.whatever_context::<_, InvalidInputError>(message)
            .map_err(Into::into)
    }
}

impl<T, E: std::error::Error + Send + Sync + 'static> OkOrInvalid for Result<T, E> {
    type Value = T;

    fn ok_or_invalid(self, message: impl Into<String>) -> Result<T> {
        self.whatever_context::<_, InvalidInputError>(message)
            .map_err(Into::into)
    }
}

/// Like [snafu::whatever], but returning an [AnkiError].
#[macro_export]
macro_rules! invalid_input {
    ($fmt:literal$(, $($arg:expr),* $(,)?)?) => {
        return core::result::Result::Err({ $crate::error::AnkiError::InvalidInput {
            source: snafu::FromString::without_source(
                format!($fmt$(, $($arg),*)*),
            )
        }})
    };
    ($source:expr, $fmt:literal$(, $($arg:expr),* $(,)?)?) => {
        match $source {
            core::result::Result::Ok(v) => v,
            core::result::Result::Err(e) => {
                return core::result::Result::Err({ $crate::error::AnkiError::InvalidInput {
                    source: snafu::FromString::with_source(
                        core::convert::Into::into(e),
                        format!($fmt$(, $($arg),*)*),
                    )
                }});
            }
        }
    };
}

/// Returns an [AnkiError::InvalidInput] unless the condition is true.
#[macro_export]
macro_rules! ensure_valid_input {
    ($condition:expr, $fmt:literal$(, $($arg:expr),* $(,)?)?) => {
        if !$condition {
            return core::result::Result::Err({ $crate::error::AnkiError::InvalidInput {
                source: snafu::FromString::without_source(
                    format!($fmt$(, $($arg),*)*),
                )
            }});
        }
    };
}
