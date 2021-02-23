// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::ReviewState};

impl From<pb::scheduling_state::Review> for ReviewState {
    fn from(state: pb::scheduling_state::Review) -> Self {
        ReviewState {
            scheduled_days: state.scheduled_days,
            elapsed_days: state.elapsed_days,
            ease_factor: state.ease_factor,
            lapses: state.lapses,
            leeched: state.leeched,
        }
    }
}

impl From<ReviewState> for pb::scheduling_state::Review {
    fn from(state: ReviewState) -> Self {
        pb::scheduling_state::Review {
            scheduled_days: state.scheduled_days,
            elapsed_days: state.elapsed_days,
            ease_factor: state.ease_factor,
            lapses: state.lapses,
            leeched: state.leeched,
        }
    }
}
