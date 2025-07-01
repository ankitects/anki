// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use fsrs::extract_simulator_config;
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
        let mut anki_progress = self.new_progress_handler::<ComputeRetentionProgress>();
        let fsrs = FSRS::new(None)?;
        if req.days_to_simulate == 0 {
            invalid_input!("no days to simulate")
        }
        let (config, cards) = self.simulate_request_to_config(&req)?;
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
                None,
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
