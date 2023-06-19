// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::LearnState;

impl From<anki_proto::scheduler::scheduling_state::Learning> for LearnState {
    fn from(state: anki_proto::scheduler::scheduling_state::Learning) -> Self {
        LearnState {
            remaining_steps: state.remaining_steps,
            scheduled_secs: state.scheduled_secs,
        }
    }
}

impl From<LearnState> for anki_proto::scheduler::scheduling_state::Learning {
    fn from(state: LearnState) -> Self {
        anki_proto::scheduler::scheduling_state::Learning {
            remaining_steps: state.remaining_steps,
            scheduled_secs: state.scheduled_secs,
        }
    }
}
