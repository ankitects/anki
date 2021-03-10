// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::PreviewState};

impl From<pb::scheduling_state::Preview> for PreviewState {
    fn from(state: pb::scheduling_state::Preview) -> Self {
        PreviewState {
            scheduled_secs: state.scheduled_secs,
            finished: state.finished,
        }
    }
}

impl From<PreviewState> for pb::scheduling_state::Preview {
    fn from(state: PreviewState) -> Self {
        pb::scheduling_state::Preview {
            scheduled_secs: state.scheduled_secs,
            finished: state.finished,
        }
    }
}
