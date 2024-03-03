// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::FSRSError;

use crate::error::AnkiError;
use crate::error::InvalidInputError;

impl From<FSRSError> for AnkiError {
    fn from(err: FSRSError) -> Self {
        match err {
            FSRSError::NotEnoughData => AnkiError::FsrsInsufficientData,
            FSRSError::OptimalNotFound => AnkiError::FsrsUnableToDetermineDesiredRetention,
            FSRSError::Interrupted => AnkiError::Interrupted,
            FSRSError::InvalidParameters => AnkiError::FsrsWeightsInvalid,
            FSRSError::InvalidInput => AnkiError::InvalidInput {
                source: InvalidInputError {
                    message: "invalid weights provided".to_string(),
                    source: None,
                    backtrace: None,
                },
            },
        }
    }
}
