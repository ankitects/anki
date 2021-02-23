// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::RelearnState};

impl From<pb::scheduling_state::Relearning> for RelearnState {
    fn from(state: pb::scheduling_state::Relearning) -> Self {
        RelearnState {
            review: state.review.unwrap_or_default().into(),
            learning: state.learning.unwrap_or_default().into(),
        }
    }
}

impl From<RelearnState> for pb::scheduling_state::Relearning {
    fn from(state: RelearnState) -> Self {
        pb::scheduling_state::Relearning {
            review: Some(state.review.into()),
            learning: Some(state.learning.into()),
        }
    }
}
