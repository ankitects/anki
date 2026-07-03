// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::interval_kind::IntervalKind;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct NewState {
    pub position: u32,
}

impl NewState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        // todo: consider packing the due number in here; it would allow us to restore
        // the original position of cards - though not as cheaply as if it were
        // a card property.
        IntervalKind::InSecs(0)
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        RevlogReviewKind::Learning
    }
}
