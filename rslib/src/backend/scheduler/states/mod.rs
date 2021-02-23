// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod filtered;
mod learning;
mod new;
mod normal;
mod preview;
mod relearning;
mod rescheduling;
mod review;

use crate::{
    backend_proto as pb,
    scheduler::states::{CardState, NewState, NextCardStates, NormalState},
};

impl From<NextCardStates> for pb::NextCardStates {
    fn from(choices: NextCardStates) -> Self {
        pb::NextCardStates {
            current: Some(choices.current.into()),
            again: Some(choices.again.into()),
            hard: Some(choices.hard.into()),
            good: Some(choices.good.into()),
            easy: Some(choices.easy.into()),
        }
    }
}

impl From<pb::NextCardStates> for NextCardStates {
    fn from(choices: pb::NextCardStates) -> Self {
        NextCardStates {
            current: choices.current.unwrap_or_default().into(),
            again: choices.again.unwrap_or_default().into(),
            hard: choices.hard.unwrap_or_default().into(),
            good: choices.good.unwrap_or_default().into(),
            easy: choices.easy.unwrap_or_default().into(),
        }
    }
}

impl From<CardState> for pb::SchedulingState {
    fn from(state: CardState) -> Self {
        pb::SchedulingState {
            value: Some(match state {
                CardState::Normal(state) => pb::scheduling_state::Value::Normal(state.into()),
                CardState::Filtered(state) => pb::scheduling_state::Value::Filtered(state.into()),
            }),
        }
    }
}

impl From<pb::SchedulingState> for CardState {
    fn from(state: pb::SchedulingState) -> Self {
        if let Some(value) = state.value {
            match value {
                pb::scheduling_state::Value::Normal(normal) => CardState::Normal(normal.into()),
                pb::scheduling_state::Value::Filtered(filtered) => {
                    CardState::Filtered(filtered.into())
                }
            }
        } else {
            CardState::Normal(NormalState::New(NewState::default()))
        }
    }
}
