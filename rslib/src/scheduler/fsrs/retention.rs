// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::scheduler::ComputeOptimalRetentionRequest;
use fsrs::SimulatorConfig;
use fsrs::FSRS;

use crate::prelude::*;

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeRetentionProgress {
    pub current: u32,
    pub total: u32,
}

impl Collection {
    pub fn compute_optimal_retention(
        &mut self,
        req: ComputeOptimalRetentionRequest,
    ) -> Result<f32> {
        let mut anki_progress = self.new_progress_handler::<ComputeRetentionProgress>();
        let fsrs = FSRS::new(None)?;
        Ok(fsrs.optimal_retention(
            &SimulatorConfig {
                deck_size: req.deck_size as usize,
                learn_span: req.days_to_simulate as usize,
                max_cost_perday: req.max_seconds_of_study_per_day as f64,
                max_ivl: req.max_interval as f64,
                recall_cost: req.recall_secs as f64,
                forget_cost: req.forget_secs as f64,
                learn_cost: req.learn_secs as f64,
            },
            &req.weights,
            |ip| {
                anki_progress
                    .update(false, |p| {
                        p.total = ip.total as u32;
                        p.current = ip.current as u32;
                    })
                    .is_ok()
            },
        )? as f32)
    }
}
