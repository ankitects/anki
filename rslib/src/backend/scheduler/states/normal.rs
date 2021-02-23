// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, scheduler::states::NormalState};

impl From<NormalState> for pb::scheduling_state::Normal {
    fn from(state: NormalState) -> Self {
        pb::scheduling_state::Normal {
            value: Some(match state {
                NormalState::New(state) => pb::scheduling_state::normal::Value::New(state.into()),
                NormalState::Learning(state) => {
                    pb::scheduling_state::normal::Value::Learning(state.into())
                }
                NormalState::Review(state) => {
                    pb::scheduling_state::normal::Value::Review(state.into())
                }
                NormalState::Relearning(state) => {
                    pb::scheduling_state::normal::Value::Relearning(state.into())
                }
            }),
        }
    }
}

impl From<pb::scheduling_state::Normal> for NormalState {
    fn from(state: pb::scheduling_state::Normal) -> Self {
        match state
            .value
            .unwrap_or_else(|| pb::scheduling_state::normal::Value::New(Default::default()))
        {
            pb::scheduling_state::normal::Value::New(state) => NormalState::New(state.into()),
            pb::scheduling_state::normal::Value::Learning(state) => {
                NormalState::Learning(state.into())
            }
            pb::scheduling_state::normal::Value::Review(state) => NormalState::Review(state.into()),
            pb::scheduling_state::normal::Value::Relearning(state) => {
                NormalState::Relearning(state.into())
            }
        }
    }
}
