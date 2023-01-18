// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::pb;
use crate::scheduler::states::NewState;

impl From<pb::scheduler::scheduling_state::New> for NewState {
    fn from(state: pb::scheduler::scheduling_state::New) -> Self {
        NewState {
            position: state.position,
        }
    }
}

impl From<NewState> for pb::scheduler::scheduling_state::New {
    fn from(state: NewState) -> Self {
        pb::scheduler::scheduling_state::New {
            position: state.position,
        }
    }
}
