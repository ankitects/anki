// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::PreviewState;

impl From<anki_proto::scheduler::scheduling_state::Preview> for PreviewState {
    fn from(state: anki_proto::scheduler::scheduling_state::Preview) -> Self {
        PreviewState {
            scheduled_secs: state.scheduled_secs,
            finished: state.finished,
        }
    }
}

impl From<PreviewState> for anki_proto::scheduler::scheduling_state::Preview {
    fn from(state: PreviewState) -> Self {
        anki_proto::scheduler::scheduling_state::Preview {
            scheduled_secs: state.scheduled_secs,
            finished: state.finished,
        }
    }
}
