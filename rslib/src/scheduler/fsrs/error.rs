// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use fsrs_optimizer::FSRSError;
use crate::error::{AnkiError, InvalidInputError};

impl From<FSRSError> for AnkiError {
    fn from(err: FSRSError) -> Self {
        match err {
            FSRSError::NotEnoughData => InvalidInputError {
                message: "Not enough data available".to_string(),
                source: None,
                backtrace: None,
            }.into(),
            FSRSError::Interrupted => AnkiError::Interrupted
        }
    }
}

