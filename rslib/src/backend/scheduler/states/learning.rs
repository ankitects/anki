// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::LearnState};

impl From<pb::scheduling_state::Learning> for LearnState {
    fn from(state: pb::scheduling_state::Learning) -> Self {
        LearnState {
            remaining_steps: state.remaining_steps,
            scheduled_secs: state.scheduled_secs,
        }
    }
}

impl From<LearnState> for pb::scheduling_state::Learning {
    fn from(state: LearnState) -> Self {
        pb::scheduling_state::Learning {
            remaining_steps: state.remaining_steps,
            scheduled_secs: state.scheduled_secs,
        }
    }
}
