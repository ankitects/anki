// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::FilteredState;

impl From<FilteredState> for anki_proto::scheduler::scheduling_state::Filtered {
    fn from(state: FilteredState) -> Self {
        anki_proto::scheduler::scheduling_state::Filtered {
            value: Some(match state {
                FilteredState::Preview(state) => {
                    anki_proto::scheduler::scheduling_state::filtered::Value::Preview(state.into())
                }
                FilteredState::Rescheduling(state) => {
                    anki_proto::scheduler::scheduling_state::filtered::Value::Rescheduling(
                        state.into(),
                    )
                }
            }),
        }
    }
}

impl From<anki_proto::scheduler::scheduling_state::Filtered> for FilteredState {
    fn from(state: anki_proto::scheduler::scheduling_state::Filtered) -> Self {
        match state.value.unwrap_or_else(|| {
            anki_proto::scheduler::scheduling_state::filtered::Value::Preview(Default::default())
        }) {
            anki_proto::scheduler::scheduling_state::filtered::Value::Preview(state) => {
                FilteredState::Preview(state.into())
            }
            anki_proto::scheduler::scheduling_state::filtered::Value::Rescheduling(state) => {
                FilteredState::Rescheduling(state.into())
            }
        }
    }
}
