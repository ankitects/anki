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

use crate::pb;
use crate::scheduler::states::CardState;
use crate::scheduler::states::NewState;
use crate::scheduler::states::NormalState;
use crate::scheduler::states::SchedulingStates;

impl From<SchedulingStates> for pb::scheduler::SchedulingStates {
    fn from(choices: SchedulingStates) -> Self {
        pb::scheduler::SchedulingStates {
            current: Some(choices.current.into()),
            again: Some(choices.again.into()),
            hard: Some(choices.hard.into()),
            good: Some(choices.good.into()),
            easy: Some(choices.easy.into()),
        }
    }
}

impl From<pb::scheduler::SchedulingStates> for SchedulingStates {
    fn from(choices: pb::scheduler::SchedulingStates) -> Self {
        SchedulingStates {
            current: choices.current.unwrap_or_default().into(),
            again: choices.again.unwrap_or_default().into(),
            hard: choices.hard.unwrap_or_default().into(),
            good: choices.good.unwrap_or_default().into(),
            easy: choices.easy.unwrap_or_default().into(),
        }
    }
}

impl From<CardState> for pb::scheduler::SchedulingState {
    fn from(state: CardState) -> Self {
        pb::scheduler::SchedulingState {
            value: Some(match state {
                CardState::Normal(state) => {
                    pb::scheduler::scheduling_state::Value::Normal(state.into())
                }
                CardState::Filtered(state) => {
                    pb::scheduler::scheduling_state::Value::Filtered(state.into())
                }
            }),
            custom_data: None,
        }
    }
}

impl From<pb::scheduler::SchedulingState> for CardState {
    fn from(state: pb::scheduler::SchedulingState) -> Self {
        if let Some(value) = state.value {
            match value {
                pb::scheduler::scheduling_state::Value::Normal(normal) => {
                    CardState::Normal(normal.into())
                }
                pb::scheduler::scheduling_state::Value::Filtered(filtered) => {
                    CardState::Filtered(filtered.into())
                }
            }
        } else {
            CardState::Normal(NormalState::New(NewState::default()))
        }
    }
}
