// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::ReschedulingFilterState;

impl From<anki_proto::scheduler::scheduling_state::ReschedulingFilter> for ReschedulingFilterState {
    fn from(state: anki_proto::scheduler::scheduling_state::ReschedulingFilter) -> Self {
        ReschedulingFilterState {
            original_state: state.original_state.unwrap_or_default().into(),
        }
    }
}

impl From<ReschedulingFilterState> for anki_proto::scheduler::scheduling_state::ReschedulingFilter {
    fn from(state: ReschedulingFilterState) -> Self {
        anki_proto::scheduler::scheduling_state::ReschedulingFilter {
            original_state: Some(state.original_state.into()),
        }
    }
}
