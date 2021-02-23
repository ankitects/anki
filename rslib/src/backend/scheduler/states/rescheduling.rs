// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::ReschedulingFilterState};

impl From<pb::scheduling_state::ReschedulingFilter> for ReschedulingFilterState {
    fn from(state: pb::scheduling_state::ReschedulingFilter) -> Self {
        ReschedulingFilterState {
            original_state: state.original_state.unwrap_or_default().into(),
        }
    }
}

impl From<ReschedulingFilterState> for pb::scheduling_state::ReschedulingFilter {
    fn from(state: ReschedulingFilterState) -> Self {
        pb::scheduling_state::ReschedulingFilter {
            original_state: Some(state.original_state.into()),
        }
    }
}
