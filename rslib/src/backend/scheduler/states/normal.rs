// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::scheduler::states::NormalState;

impl From<NormalState> for anki_proto::scheduler::scheduling_state::Normal {
    fn from(state: NormalState) -> Self {
        anki_proto::scheduler::scheduling_state::Normal {
            value: Some(match state {
                NormalState::New(state) => {
                    anki_proto::scheduler::scheduling_state::normal::Value::New(state.into())
                }
                NormalState::Learning(state) => {
                    anki_proto::scheduler::scheduling_state::normal::Value::Learning(state.into())
                }
                NormalState::Review(state) => {
                    anki_proto::scheduler::scheduling_state::normal::Value::Review(state.into())
                }
                NormalState::Relearning(state) => {
                    anki_proto::scheduler::scheduling_state::normal::Value::Relearning(state.into())
                }
            }),
        }
    }
}

impl From<anki_proto::scheduler::scheduling_state::Normal> for NormalState {
    fn from(state: anki_proto::scheduler::scheduling_state::Normal) -> Self {
        match state.value.unwrap_or_else(|| {
            anki_proto::scheduler::scheduling_state::normal::Value::New(Default::default())
        }) {
            anki_proto::scheduler::scheduling_state::normal::Value::New(state) => {
                NormalState::New(state.into())
            }
            anki_proto::scheduler::scheduling_state::normal::Value::Learning(state) => {
                NormalState::Learning(state.into())
            }
            anki_proto::scheduler::scheduling_state::normal::Value::Review(state) => {
                NormalState::Review(state.into())
            }
            anki_proto::scheduler::scheduling_state::normal::Value::Relearning(state) => {
                NormalState::Relearning(state.into())
            }
        }
    }
}
