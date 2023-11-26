// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::FSRSError;

use crate::error::AnkiError;

impl From<FSRSError> for AnkiError {
    fn from(err: FSRSError) -> Self {
        match err {
            FSRSError::NotEnoughData => AnkiError::FsrsInsufficientData,
            FSRSError::OptimalNotFound => AnkiError::FsrsUnableToDetermineDesiredRetention,
            FSRSError::Interrupted => AnkiError::Interrupted,
            FSRSError::InvalidWeights => AnkiError::FsrsWeightsInvalid,
        }
    }
}
