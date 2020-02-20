// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// while Anki tends to only use positive numbers, sqlite only supports
// signed integers, so these numbers are signed as well.

pub type ObjID = i64;
pub type Usn = i32;
pub type Timestamp = i64;
