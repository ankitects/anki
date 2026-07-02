// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use snafu::Backtrace;
use snafu::OptionExt;
use snafu::ResultExt;
use snafu::Snafu;

use crate::prelude::*;

/// General-purpose error for unexpected [Err]s, [None]s, and other
/// violated constraints.
#[derive(Debug, Snafu)]
#[snafu(visibility(pub), display("{message}"), whatever)]
pub struct InvalidInputError {
    pub message: String,
    #[snafu(source(from(Box<dyn std::error::Error + Send + Sync>, Some)))]
    pub source: Option<Box<dyn std::error::Error + Send + Sync>>,
    pub backtrace: Option<Backtrace>,
}

impl InvalidInputError {
    pub fn message(&self) -> String {
        self.message.clone()
    }

    pub fn context(&self) -> String {
        if let Some(source) = &self.source {
            format!("{source}")
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

/// Allows generating [AnkiError::InvalidInput] from [None] and the
/// typical [Err].
pub trait OrInvalid {
    type Value;
    fn or_invalid(self, message: impl Into<String>) -> Result<Self::Value>;
}

impl<T> OrInvalid for Option<T> {
    type Value = T;

    fn or_invalid(self, message: impl Into<String>) -> Result<T> {
        self.whatever_context::<_, InvalidInputError>(message)
            .map_err(Into::into)
    }
}

impl<T, E: std::error::Error + Send + Sync + 'static> OrInvalid for Result<T, E> {
    type Value = T;

    fn or_invalid(self, message: impl Into<String>) -> Result<T> {
        self.whatever_context::<_, InvalidInputError>(message)
            .map_err(Into::into)
    }
}

/// Returns an [AnkiError::InvalidInput] with the provided format string and an
/// optional underlying error.
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
        return core::result::Result::Err({ $crate::error::AnkiError::InvalidInput {
            source: snafu::FromString::with_source(
                core::convert::Into::into($source),
                format!($fmt$(, $($arg),*)*),
            )
        }})
    };
}

/// Returns an [AnkiError::InvalidInput] unless the condition is true.
#[macro_export]
macro_rules! require {
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
