// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use num_enum::TryFromPrimitive;
use serde_repr::{Deserialize_repr, Serialize_repr};

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
pub enum CardType {
    New = 0,
    Learn = 1,
    Review = 2,
    Relearn = 3,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, TryFromPrimitive, Clone, Copy)]
#[repr(i8)]
pub enum CardQueue {
    /// due is the order cards are shown in
    New = 0,
    /// due is a unix timestamp
    Learn = 1,
    /// due is days since creation date
    Review = 2,
    DayLearn = 3,
    /// due is a unix timestamp.
    /// preview cards only placed here when failed.
    PreviewRepeat = 4,
    /// cards are not due in these states
    Suspended = -1,
    UserBuried = -2,
    SchedBuried = -3,
}
