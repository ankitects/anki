// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{any, fmt};

use snafu::{Backtrace, OptionExt, Snafu};

use crate::prelude::*;

#[derive(Debug, Snafu)]
#[snafu(visibility(pub), display("{message}"))]
pub struct NotFoundError {
    pub message: String,
    pub backtrace: Option<Backtrace>,
}

impl PartialEq for NotFoundError {
    fn eq(&self, other: &Self) -> bool {
        self.message == other.message
    }
}

impl Eq for NotFoundError {}

/// Allows generating [AnkiError::NotFound] from [Option::None] and the
/// typical [core::result::Result::Err].
pub trait OkOrNotFound {
    type Value;
    fn ok_or_not_found(self, identifier: impl fmt::Debug) -> Result<Self::Value>;
}

impl<T> OkOrNotFound for Option<T> {
    type Value = T;

    fn ok_or_not_found(self, identifier: impl fmt::Debug) -> Result<Self::Value> {
        self.with_context(|| NotFoundSnafu {
            message: format!(
                "no such {}: {identifier:?}",
                any::type_name::<Self::Value>()
            ),
        })
        .map_err(Into::into)
    }
}
