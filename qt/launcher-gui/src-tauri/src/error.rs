// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use snafu::Snafu;

// TODO: these aren't used yet
#[derive(Debug, PartialEq, Snafu)]
pub enum Error {
    OsUnsupported,
    InvalidInput,
}
