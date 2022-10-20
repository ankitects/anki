// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{any, fmt};

use snafu::{Backtrace, OptionExt, Snafu};

use crate::prelude::*;

/// Something was unexpectedly missing from the database.
#[derive(Debug, Snafu)]
#[snafu(visibility(pub))]
pub struct NotFoundError {
    pub type_name: &'static str,
    pub identifier: String,
    pub backtrace: Option<Backtrace>,
}

impl NotFoundError {
    pub fn message(&self, tr: &I18n) -> String {
        tr.errors_inconsistent_db_state().into()
    }

    pub fn context(&self) -> String {
        format!("No such {}: '{}'", self.type_name, self.identifier)
    }
}

impl PartialEq for NotFoundError {
    fn eq(&self, other: &Self) -> bool {
        self.type_name == other.type_name && self.identifier == other.identifier
    }
}

impl Eq for NotFoundError {}

/// Allows generating [AnkiError::NotFound] from [Option::None].
pub trait OrNotFound {
    type Value;
    fn or_not_found(self, identifier: impl fmt::Display) -> Result<Self::Value>;
}

impl<T> OrNotFound for Option<T> {
    type Value = T;

    fn or_not_found(self, identifier: impl fmt::Display) -> Result<Self::Value> {
        self.with_context(|| NotFoundSnafu {
            type_name: any::type_name::<Self::Value>(),
            identifier: format!("{identifier}"),
        })
        .map_err(Into::into)
    }
}
