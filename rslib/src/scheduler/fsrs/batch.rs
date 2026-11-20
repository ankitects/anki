// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::panic::catch_unwind;
use std::panic::AssertUnwindSafe;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use anki_proto::scheduler::ComputeFsrsParamsResponse;
use fsrs::CombinedProgressState;
use rayon::prelude::*;

use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::scheduler::fsrs::params::compute_params_from_prepared;
use crate::scheduler::fsrs::params::new_compute_params_progress_phase;
use crate::scheduler::fsrs::params::ComputeAllParamsPresetProgress;
use crate::scheduler::fsrs::params::ComputeAllParamsProgress;
use crate::scheduler::fsrs::params::ComputeParamsProgressPhase;
use crate::scheduler::fsrs::params::PreparedComputeParams;
use crate::scheduler::fsrs::params::SharedComputeParamsProgressPhase;

pub(crate) struct ComputeParamsBatchInput {
    pub index: usize,
    pub name: String,
    pub prepared: PreparedComputeParams,
}

pub(crate) struct ComputeParamsBatchOutput {
    pub index: usize,
    pub name: String,
    pub result: Result<ComputeFsrsParamsResponse>,
}

struct ComputeParamsBatchJob {
    input: ComputeParamsBatchInput,
    progress_index: usize,
    progress: Arc<std::sync::Mutex<CombinedProgressState>>,
    progress_phase: SharedComputeParamsProgressPhase,
    done: Arc<AtomicBool>,
}

impl ComputeParamsBatchJob {
    fn estimated_reviews(&self) -> usize {
        self.input.prepared.target_counts.total_targets
    }
}

struct ComputeParamsBatchJobLane {
    estimated_reviews: usize,
    jobs: Vec<ComputeParamsBatchJob>,
}

impl Collection {
    pub(crate) fn compute_params_batch(
        &mut self,
        inputs: Vec<ComputeParamsBatchInput>,
    ) -> Result<Vec<ComputeParamsBatchOutput>> {
        self.clear_progress();
        let anki_progress = self.new_progress_handler::<ComputeAllParamsProgress>();
        self.compute_params_batch_with_progress(inputs, anki_progress)
    }

    pub(crate) fn compute_params_batch_with_progress(
        &mut self,
        inputs: Vec<ComputeParamsBatchInput>,
        anki_progress: ThrottlingProgressHandler<ComputeAllParamsProgress>,
    ) -> Result<Vec<ComputeParamsBatchOutput>> {
        let mut jobs = Vec::with_capacity(inputs.len());
        let mut outputs = Vec::new();
        let mut progress_entries = Vec::with_capacity(inputs.len());

        for input in inputs {
            let progress_entry = ComputeAllParamsPresetProgress {
                name: input.name.clone(),
                reviews: input.prepared.target_counts.total_targets as u32,
                long_term_reviews: input.prepared.target_counts.long_term_targets as u32,
                short_term_reviews: input.prepared.target_counts.short_term_targets as u32,
                ..Default::default()
            };
            if input.prepared.target_counts.total_targets == 0 {
                progress_entries.push(ComputeAllParamsPresetProgress {
                    finished: true,
                    skipped: true,
                    ..progress_entry
                });
                outputs.push(ComputeParamsBatchOutput {
                    index: input.index,
                    name: input.name,
                    result: Ok(ComputeFsrsParamsResponse {
                        params: input.prepared.current_params,
                        fsrs_items: 0,
                        health_check_passed: None,
                        fsrs_dynamic_desired_retention_params: Vec::new(),
                        fsrs_dynamic_desired_retention_weights: Vec::new(),
                        fsrs_dynamic_desired_retention_avg_drs: Vec::new(),
                        fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec::new(),
                        fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec::new(),
                        fsrs_dynamic_desired_retention_fixed_target_weights: Vec::new(),
                        fsrs_dynamic_desired_retention_fixed_target_drs: Vec::new(),
                        fsrs_dynamic_desired_retention_min: 0.0,
                        fsrs_dynamic_desired_retention_max: 0.0,
                    }),
                });
                continue;
            }

            let progress_index = progress_entries.len();
            progress_entries.push(progress_entry);
            jobs.push(ComputeParamsBatchJob {
                input,
                progress_index,
                progress: CombinedProgressState::new_shared(),
                progress_phase: new_compute_params_progress_phase(),
                done: Arc::new(AtomicBool::new(false)),
            });
        }

        let total_optimizer_jobs = jobs.len() as u32;
        let progress_thread = self.create_compute_params_batch_progress_thread(
            anki_progress,
            &jobs,
            progress_entries,
            total_optimizer_jobs,
        )?;
        let job_lanes = compute_params_batch_job_lanes(jobs);
        outputs.extend(
            job_lanes
                .into_par_iter()
                .flat_map(|lane| {
                    lane.jobs
                        .into_iter()
                        .map(|job| {
                            // A panic must still mark the job as done, or the
                            // progress thread never stops.
                            let result = catch_unwind(AssertUnwindSafe(|| {
                                compute_params_from_prepared(
                                    job.input.prepared,
                                    Some(job.progress.clone()),
                                    Some(job.progress_phase.clone()),
                                    false,
                                )
                            }))
                            .unwrap_or_else(|_| invalid_input!("FSRS optimizer panicked"));
                            job.done.store(true, Ordering::Release);
                            ComputeParamsBatchOutput {
                                index: job.input.index,
                                name: job.input.name,
                                result,
                            }
                        })
                        .collect::<Vec<_>>()
                })
                .collect::<Vec<_>>(),
        );
        progress_thread.join().ok();

        outputs.sort_unstable_by_key(|output| output.index);
        Ok(outputs)
    }

    fn create_compute_params_batch_progress_thread(
        &self,
        mut anki_progress: ThrottlingProgressHandler<ComputeAllParamsProgress>,
        jobs: &[ComputeParamsBatchJob],
        progress_entries: Vec<ComputeAllParamsPresetProgress>,
        total_optimizer_jobs: u32,
    ) -> Result<thread::JoinHandle<()>> {
        anki_progress.set(ComputeAllParamsProgress {
            current_iteration: progress_entries
                .iter()
                .filter(|preset| preset.finished && !preset.skipped)
                .count() as u32,
            total_iterations: total_optimizer_jobs,
            presets: progress_entries,
        })?;

        let progresses = jobs
            .iter()
            .map(|job| job.progress.clone())
            .collect::<Vec<_>>();
        let progress_phases = jobs
            .iter()
            .map(|job| job.progress_phase.clone())
            .collect::<Vec<_>>();
        let done = jobs.iter().map(|job| job.done.clone()).collect::<Vec<_>>();
        let progress_indexes = jobs
            .iter()
            .map(|job| job.progress_index)
            .collect::<Vec<_>>();
        let progress_thread = thread::spawn(move || {
            let mut finished = false;
            while !finished {
                thread::sleep(Duration::from_millis(100));
                finished = done.iter().all(|done| done.load(Ordering::Acquire));
                if let Err(_err) = anki_progress.update(false, |state| {
                    state.total_iterations = total_optimizer_jobs;
                    for (((progress_index, progress), progress_phase), done) in progress_indexes
                        .iter()
                        .zip(progresses.iter())
                        .zip(progress_phases.iter())
                        .zip(done.iter())
                    {
                        let preset = &mut state.presets[*progress_index];
                        let guard = progress.lock().unwrap();
                        preset.current_iteration = guard.current() as u32;
                        preset.total_iterations = guard.total() as u32;
                        preset.phase = ComputeParamsProgressPhase::from_shared(progress_phase);
                        preset.finished = done.load(Ordering::Acquire);
                    }
                    state.current_iteration = state
                        .presets
                        .iter()
                        .filter(|preset| preset.finished && !preset.skipped)
                        .count() as u32;
                }) {
                    for progress in &progresses {
                        progress.lock().unwrap().want_abort = true;
                    }
                    return;
                }
            }
        });
        Ok(progress_thread)
    }
}

fn compute_params_batch_job_lanes(
    jobs: Vec<ComputeParamsBatchJob>,
) -> Vec<ComputeParamsBatchJobLane> {
    if jobs.is_empty() {
        return Vec::new();
    }

    let lane_count = rayon::current_num_threads().min(jobs.len());
    compute_params_batch_job_lanes_with_count(jobs, lane_count)
}

fn compute_params_batch_job_lanes_with_count(
    mut jobs: Vec<ComputeParamsBatchJob>,
    lane_count: usize,
) -> Vec<ComputeParamsBatchJobLane> {
    jobs.sort_unstable_by_key(|job| std::cmp::Reverse(job.estimated_reviews()));
    let mut lanes = (0..lane_count)
        .map(|_| ComputeParamsBatchJobLane {
            estimated_reviews: 0,
            jobs: Vec::new(),
        })
        .collect::<Vec<_>>();

    for job in jobs {
        let lane = lanes
            .iter_mut()
            .min_by_key(|lane| lane.estimated_reviews)
            .unwrap();
        lane.estimated_reviews += job.estimated_reviews();
        lane.jobs.push(job);
    }

    lanes
}

#[cfg(test)]
mod test {
    use fsrs::ComputeParametersVersion;
    use fsrs::DEFAULT_PARAMETERS;

    use super::*;
    use crate::scheduler::fsrs::params::TrainingTargetCounts;

    fn compute_params_batch_test_job(name: &str, reviews: usize) -> ComputeParamsBatchJob {
        ComputeParamsBatchJob {
            input: ComputeParamsBatchInput {
                index: 0,
                name: name.to_string(),
                prepared: PreparedComputeParams {
                    current_params: DEFAULT_PARAMETERS.to_vec(),
                    num_of_relearning_steps: 0,
                    model_version: ComputeParametersVersion::Fsrs7,
                    include_same_day_reviews: true,
                    enable_scheduling_penalties: true,
                    dynamic_desired_retention_enabled: false,
                    simulator_config: Default::default(),
                    existing_card_input: None,
                    training: Default::default(),
                    target_counts: TrainingTargetCounts {
                        total_targets: reviews,
                        long_term_targets: reviews,
                        short_term_targets: 0,
                    },
                },
            },
            progress_index: 0,
            progress: CombinedProgressState::new_shared(),
            progress_phase: new_compute_params_progress_phase(),
            done: Arc::new(AtomicBool::new(false)),
        }
    }

    #[test]
    fn compute_params_batch_job_lanes_start_largest_and_balance_reviews() {
        let lanes = compute_params_batch_job_lanes_with_count(
            vec![
                compute_params_batch_test_job("small", 40),
                compute_params_batch_test_job("largest", 100),
                compute_params_batch_test_job("large", 90),
                compute_params_batch_test_job("medium", 50),
            ],
            2,
        );

        let lane_totals = lanes
            .iter()
            .map(|lane| lane.estimated_reviews)
            .collect::<Vec<_>>();
        assert_eq!(lane_totals, vec![140, 140]);
        assert_eq!(lanes[0].jobs[0].input.name, "largest");
        assert_eq!(lanes[1].jobs[0].input.name, "large");
    }

    #[test]
    fn compute_params_batch_returns_zero_review_outputs_in_input_order() -> Result<()> {
        let mut col = Collection::new();
        let outputs = col.compute_params_batch(vec![
            ComputeParamsBatchInput {
                index: 1,
                name: "Second".into(),
                prepared: PreparedComputeParams {
                    current_params: vec![2.0; 34],
                    num_of_relearning_steps: 0,
                    model_version: ComputeParametersVersion::Fsrs7,
                    include_same_day_reviews: true,
                    enable_scheduling_penalties: true,
                    dynamic_desired_retention_enabled: false,
                    simulator_config: Default::default(),
                    existing_card_input: None,
                    training: Default::default(),
                    target_counts: TrainingTargetCounts::default(),
                },
            },
            ComputeParamsBatchInput {
                index: 0,
                name: "First".into(),
                prepared: PreparedComputeParams {
                    current_params: vec![1.0; 34],
                    num_of_relearning_steps: 0,
                    model_version: ComputeParametersVersion::Fsrs7,
                    include_same_day_reviews: true,
                    enable_scheduling_penalties: true,
                    dynamic_desired_retention_enabled: false,
                    simulator_config: Default::default(),
                    existing_card_input: None,
                    training: Default::default(),
                    target_counts: TrainingTargetCounts::default(),
                },
            },
        ])?;

        assert_eq!(
            outputs
                .iter()
                .map(|output| (output.index, output.name.as_str()))
                .collect::<Vec<_>>(),
            vec![(0, "First"), (1, "Second")]
        );
        assert_eq!(outputs[0].result.as_ref().unwrap().params, vec![1.0; 34]);
        assert_eq!(outputs[1].result.as_ref().unwrap().params, vec![2.0; 34]);
        Ok(())
    }
}
