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

use crate::scheduler::states::CardState;
use crate::scheduler::states::NewState;
use crate::scheduler::states::NormalState;
use crate::scheduler::states::SchedulingStates;

impl From<SchedulingStates> for anki_proto::scheduler::SchedulingStates {
    fn from(choices: SchedulingStates) -> Self {
        anki_proto::scheduler::SchedulingStates {
            current: Some(choices.current.into()),
            again: Some(choices.again.into()),
            hard: Some(choices.hard.into()),
            good: Some(choices.good.into()),
            easy: Some(choices.easy.into()),
        }
    }
}

impl From<anki_proto::scheduler::SchedulingStates> for SchedulingStates {
    fn from(choices: anki_proto::scheduler::SchedulingStates) -> Self {
        SchedulingStates {
            current: choices.current.unwrap_or_default().into(),
            again: choices.again.unwrap_or_default().into(),
            hard: choices.hard.unwrap_or_default().into(),
            good: choices.good.unwrap_or_default().into(),
            easy: choices.easy.unwrap_or_default().into(),
        }
    }
}

impl From<CardState> for anki_proto::scheduler::SchedulingState {
    fn from(state: CardState) -> Self {
        anki_proto::scheduler::SchedulingState {
            kind: Some(match state {
                CardState::Normal(state) => {
                    anki_proto::scheduler::scheduling_state::Kind::Normal(state.into())
                }
                CardState::Filtered(state) => {
                    anki_proto::scheduler::scheduling_state::Kind::Filtered(state.into())
                }
            }),
            custom_data: None,
        }
    }
}

impl From<anki_proto::scheduler::SchedulingState> for CardState {
    fn from(state: anki_proto::scheduler::SchedulingState) -> Self {
        if let Some(value) = state.kind {
            match value {
                anki_proto::scheduler::scheduling_state::Kind::Normal(normal) => {
                    CardState::Normal(normal.into())
                }
                anki_proto::scheduler::scheduling_state::Kind::Filtered(filtered) => {
                    CardState::Filtered(filtered.into())
                }
            }
        } else {
            CardState::Normal(NormalState::New(NewState::default()))
        }
    }
}
