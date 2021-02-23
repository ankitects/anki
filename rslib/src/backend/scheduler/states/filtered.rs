// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::FilteredState};

impl From<FilteredState> for pb::scheduling_state::Filtered {
    fn from(state: FilteredState) -> Self {
        pb::scheduling_state::Filtered {
            value: Some(match state {
                FilteredState::Preview(state) => {
                    pb::scheduling_state::filtered::Value::Preview(state.into())
                }
                FilteredState::Rescheduling(state) => {
                    pb::scheduling_state::filtered::Value::Rescheduling(state.into())
                }
            }),
        }
    }
}

impl From<pb::scheduling_state::Filtered> for FilteredState {
    fn from(state: pb::scheduling_state::Filtered) -> Self {
        match state
            .value
            .unwrap_or_else(|| pb::scheduling_state::filtered::Value::Preview(Default::default()))
        {
            pb::scheduling_state::filtered::Value::Preview(state) => {
                FilteredState::Preview(state.into())
            }
            pb::scheduling_state::filtered::Value::Rescheduling(state) => {
                FilteredState::Rescheduling(state.into())
            }
        }
    }
}
