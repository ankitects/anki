use anki_proto::scheduler::simulate_fsrs_review_request::cmrr_target::Kind;
// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use fsrs::extract_simulator_config;
use fsrs::SimulationResult;
use fsrs::SimulatorConfig;
use fsrs::FSRS;

use crate::prelude::*;
use crate::revlog::RevlogEntry;

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeRetentionProgress {
    pub current: u32,
    pub total: u32,
}

impl Collection {
    pub fn compute_optimal_retention(&mut self, req: SimulateFsrsReviewRequest) -> Result<f32> {
        // Helper macro to wrap the closure for "CMRRTargetFn"s
        macro_rules! wrap {
            ($f:expr) => {
                Some(fsrs::CMRRTargetFn(std::sync::Arc::new($f)))
            };
        }

        let target_type = req.target.unwrap().kind;

        let days_to_simulate = req.days_to_simulate as f32;

        let target = match target_type {
            Some(Kind::Memorized(_)) => None,
            Some(Kind::Stability(_)) => {
                wrap!(move |SimulationResult {
                                cards,
                                cost_per_day,
                                ..
                            },
                            params| {
                    let total_cost = cost_per_day.iter().sum::<f32>();
                    total_cost
                        / cards.iter().fold(0., |p, c| {
                            p + (c.retention_on(params, days_to_simulate) * c.stability)
                        })
                })
            }
            None => None,
        };

        let mut anki_progress = self.new_progress_handler::<ComputeRetentionProgress>();
        let fsrs = FSRS::new(None)?;
        if req.days_to_simulate == 0 {
            invalid_input!("no days to simulate")
        }
        let (mut config, cards) = self.simulate_request_to_config(&req)?;

        if let Some(Kind::Memorized(settings)) = target_type {
            let loss_aversion = settings.loss_aversion;

            config.relearning_step_transitions[0][0] *= loss_aversion;
            config.relearning_step_transitions[1][0] *= loss_aversion;
            config.relearning_step_transitions[2][0] *= loss_aversion;
        }

        Ok(fsrs
            .optimal_retention(
                &config,
                &req.params,
                |ip| {
                    anki_progress
                        .update(false, |p| {
                            p.current = ip.current as u32;
                        })
                        .is_ok()
                },
                Some(cards),
                target,
            )?
            .clamp(0.7, 0.95))
    }

    pub fn get_optimal_retention_parameters(
        &mut self,
        revlogs: Vec<RevlogEntry>,
    ) -> Result<SimulatorConfig> {
        let fsrs_revlog: Vec<fsrs::RevlogEntry> = revlogs.into_iter().map(|r| r.into()).collect();
        let params =
            extract_simulator_config(fsrs_revlog, self.timing_today()?.next_day_at.into(), true);
        Ok(params)
    }
}

impl From<crate::revlog::RevlogReviewKind> for fsrs::RevlogReviewKind {
    fn from(kind: crate::revlog::RevlogReviewKind) -> Self {
        match kind {
            crate::revlog::RevlogReviewKind::Learning => fsrs::RevlogReviewKind::Learning,
            crate::revlog::RevlogReviewKind::Review => fsrs::RevlogReviewKind::Review,
            crate::revlog::RevlogReviewKind::Relearning => fsrs::RevlogReviewKind::Relearning,
            crate::revlog::RevlogReviewKind::Filtered => fsrs::RevlogReviewKind::Filtered,
            crate::revlog::RevlogReviewKind::Manual
            | crate::revlog::RevlogReviewKind::Rescheduled => fsrs::RevlogReviewKind::Manual,
        }
    }
}

impl From<crate::revlog::RevlogEntry> for fsrs::RevlogEntry {
    fn from(entry: crate::revlog::RevlogEntry) -> Self {
        fsrs::RevlogEntry {
            id: entry.id.into(),
            cid: entry.cid.into(),
            usn: entry.usn.into(),
            button_chosen: entry.button_chosen,
            interval: entry.interval,
            last_interval: entry.last_interval,
            ease_factor: entry.ease_factor,
            taken_millis: entry.taken_millis,
            review_kind: entry.review_kind.into(),
        }
    }
}
