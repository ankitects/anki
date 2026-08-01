// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::RelearnState;

impl From<anki_proto::scheduler::scheduling_state::Relearning> for RelearnState {
    fn from(state: anki_proto::scheduler::scheduling_state::Relearning) -> Self {
        RelearnState {
            review: state.review.unwrap_or_default().into(),
            learning: state.learning.unwrap_or_default().into(),
        }
    }
}

impl From<RelearnState> for anki_proto::scheduler::scheduling_state::Relearning {
    fn from(state: RelearnState) -> Self {
        anki_proto::scheduler::scheduling_state::Relearning {
            review: Some(state.review.into()),
            learning: Some(state.learning.into()),
        }
    }
}
