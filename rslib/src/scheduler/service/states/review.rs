// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::ReviewState;

impl From<anki_proto::scheduler::scheduling_state::Review> for ReviewState {
    fn from(state: anki_proto::scheduler::scheduling_state::Review) -> Self {
        ReviewState {
            scheduled_days: state.scheduled_days,
            elapsed_days: state.elapsed_days,
            ease_factor: state.ease_factor,
            lapses: state.lapses,
            leeched: state.leeched,
            memory_state: state.memory_state.map(Into::into),
        }
    }
}

impl From<ReviewState> for anki_proto::scheduler::scheduling_state::Review {
    fn from(state: ReviewState) -> Self {
        anki_proto::scheduler::scheduling_state::Review {
            scheduled_days: state.scheduled_days,
            elapsed_days: state.elapsed_days,
            ease_factor: state.ease_factor,
            lapses: state.lapses,
            leeched: state.leeched,
            memory_state: state.memory_state.map(Into::into),
        }
    }
}
