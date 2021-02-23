// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::NewState};

impl From<pb::scheduling_state::New> for NewState {
    fn from(state: pb::scheduling_state::New) -> Self {
        NewState {
            position: state.position,
        }
    }
}

impl From<NewState> for pb::scheduling_state::New {
    fn from(state: NewState) -> Self {
        pb::scheduling_state::New {
            position: state.position,
        }
    }
}
