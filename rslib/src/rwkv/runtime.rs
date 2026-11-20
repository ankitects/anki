// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io;
use std::path::Path;
use std::path::PathBuf;

use super::api::*;
use super::paired;
use super::StatePrecision;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ModelKind {
    Legacy,
    Sm40,
    Sm36,
    Sm55,
    Sm56,
    Sm72,
    Sm72e20,
    Sm72e25,
    Sm92,
    /// The instant-only small model (SM57): its curve head is untrained, so
    /// it never provides curve outputs (see [`RwkvRuntime::InstantOnly`] and
    /// [`RwkvRuntime::load_paired`]).
    Sm57,
    /// The show-time instant-only model, stage 2 (SM98): instant-only like
    /// SM57, trained on show-time inputs. It serves RWKV-Instant beside the
    /// release, or alone as [`RwkvRuntime::InstantOnly`] when no preset uses
    /// RWKV-Curve; never as a full model.
    Sm98,
    /// The smaller instant-only model (SM101), the "Tiny" choice: SM98 with
    /// one card layer instead of two (a smaller card state), trained the
    /// same way. It serves RWKV-Instant exactly where SM98 does.
    Sm101,
    /// The show-time instant-only big model (SM99), the Big choice's
    /// RWKV-Instant model: the show-time big model (SM91s2, `Legacy`)
    /// trained two more epochs on show-time inputs with the curve loss off.
    /// It runs on the big engine beside `Legacy`, or alone as an instant-only
    /// runtime when no preset uses RWKV-Curve; never as a full model.
    Sm99,
    /// The Tiny two-head model (SM106), the Tiny choice's one model: the
    /// show-time retrain (SM92) cut to one card layer (the tiny plan of
    /// SM101) and trained on with both heads on show-time inputs. A full
    /// model (curve, intervals, S90, instant recall and buttons) without an
    /// answer-time head; it runs alone when the one-model opt-out is on.
    Sm106,
}

impl ModelKind {
    pub fn parse(value: &str) -> io::Result<Self> {
        match value {
            "legacy" => Ok(Self::Legacy),
            "sm40" => Ok(Self::Sm40),
            "sm36" => Ok(Self::Sm36),
            "sm55" => Ok(Self::Sm55),
            "sm56" => Ok(Self::Sm56),
            "sm72" => Ok(Self::Sm72),
            "sm72e20" => Ok(Self::Sm72e20),
            "sm72e25" => Ok(Self::Sm72e25),
            "sm92" => Ok(Self::Sm92),
            "sm57" => Ok(Self::Sm57),
            "sm98" => Ok(Self::Sm98),
            "sm101" => Ok(Self::Sm101),
            "sm99" => Ok(Self::Sm99),
            "sm106" => Ok(Self::Sm106),
            _ => Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "unknown RWKV model",
            )),
        }
    }

    /// Checkpoints trained without the deleted-card signal: their missing
    /// note, deck and preset flags (inputs 13..15) are always zero. Legacy is
    /// the big model (1 leak-free epoch) since 2026-11-10 and the show-time
    /// big model (SM91s2), trained from it, since 2026-11-11.
    pub fn leak_free_inputs(self) -> bool {
        matches!(
            self,
            Self::Legacy
                | Self::Sm72
                | Self::Sm72e20
                | Self::Sm72e25
                | Self::Sm92
                | Self::Sm57
                | Self::Sm98
                | Self::Sm101
                | Self::Sm99
                | Self::Sm106
        )
    }

    /// Checkpoints whose curve head was never trained: they may provide
    /// instant recall and button probabilities only, never intervals.
    pub fn instant_only(self) -> bool {
        matches!(self, Self::Sm57 | Self::Sm98 | Self::Sm101 | Self::Sm99)
    }

    /// The small architecture; `Legacy` and `Sm99` run on the big engine.
    fn small(self) -> bool {
        !matches!(self, Self::Legacy | Self::Sm99)
    }

    /// Whether the model runs on the primary engine (`super::RwkvInference`)
    /// of this build; otherwise it runs on `super::alternate`.
    fn on_primary_engine(self) -> bool {
        self.small() == super::SMALL_MODEL
    }

    /// Card layers of the small models' checkpoints: the small engine reads
    /// the count from the weights, and a file that does not match its model
    /// key is refused.
    fn card_layers(self) -> Option<usize> {
        match self {
            Self::Legacy | Self::Sm99 => None,
            Self::Sm101 | Self::Sm106 => Some(1),
            _ => Some(2),
        }
    }
}

fn check_card_layers(model: ModelKind, card_layers: usize) -> io::Result<()> {
    match model.card_layers() {
        Some(expected) if expected != card_layers => {
            Err(invalid_input("RWKV weights do not match the model"))
        }
        _ => Ok(()),
    }
}

/// The release model and a second small model for RWKV-Instant (the
/// show-time retrain, or the instant-only model) replaying the same history.
/// Both keep their own recurrent state; the release provides the curve,
/// intervals, S90 and the answer-time head, the instant model provides
/// instant recall and button probabilities. Every serialized state holds
/// both (`super::paired`). Both models are of one architecture and run on
/// its engine: two small models (Small, Tiny) or the two big models (Big:
/// the show-time big model (SM91s2) and the show-time instant-only big model
/// (SM99)).
pub struct PairedRuntime<E> {
    release: E,
    instant: E,
}

pub enum RwkvRuntime {
    Primary(Box<super::RwkvInference>),
    Alternate(Box<super::alternate::RwkvInference>),
    /// An instant-only model alone, for a profile that uses RWKV-Instant and
    /// no RWKV-Curve: instant recall and button probabilities only. Curve
    /// retrievability, intervals and S90 are `None`, there is no answer
    /// time, and workload simulations (which schedule with the curve) fail.
    InstantOnly(Box<super::RwkvInference>),
    /// [`Self::InstantOnly`] on the alternate engine (the instant-only big
    /// model (SM99) with the default features).
    AlternateInstantOnly(Box<super::alternate::RwkvInference>),
    Paired(Box<PairedRuntime<super::RwkvInference>>),
    /// [`Self::Paired`] on the alternate engine (the Big pair with the
    /// default features).
    AlternatePaired(Box<PairedRuntime<super::alternate::RwkvInference>>),
}

#[derive(Clone)]
pub enum RwkvRuntimeState {
    Primary(super::RwkvInferenceState),
    Alternate(super::alternate::RwkvInferenceState),
    Paired(Box<(super::RwkvInferenceState, super::RwkvInferenceState)>),
    AlternatePaired(
        Box<(
            super::alternate::RwkvInferenceState,
            super::alternate::RwkvInferenceState,
        )>,
    ),
}

/// `$single` runs on a one-model runtime; `$paired` on a paired one (both
/// expand once per engine).
macro_rules! dispatch {
    ($runtime:expr, $engine:ident, $call:expr, $pair:ident => $paired:expr) => {
        match $runtime {
            RwkvRuntime::Primary($engine) => $call,
            RwkvRuntime::Alternate($engine) => $call,
            RwkvRuntime::InstantOnly($engine) => $call,
            RwkvRuntime::AlternateInstantOnly($engine) => $call,
            RwkvRuntime::Paired($pair) => $paired,
            RwkvRuntime::AlternatePaired($pair) => $paired,
        }
    };
}

/// Returns `$body` from the enclosing function for an instant-only runtime
/// on either engine.
macro_rules! return_if_instant_only {
    ($runtime:expr, $engine:ident => $body:expr) => {
        match $runtime {
            RwkvRuntime::InstantOnly($engine) => return $body,
            RwkvRuntime::AlternateInstantOnly($engine) => return $body,
            _ => {}
        }
    };
}

fn invalid_input(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}

fn no_curve_model() -> io::Error {
    invalid_input("RWKV workload simulations need the RWKV-Curve model; the instant-only model has no trained curve")
}

/// A `review` of the instant-only model alone: no curve, intervals or S90.
fn instant_only_review_output(
    (retrievability, button_probabilities, next): (f32, [f32; 4], ReviewStateOwned),
) -> io::Result<ReviewOutput> {
    let take = |state: Option<Vec<u8>>| state.ok_or_else(|| invalid_input("missing RWKV state"));
    Ok(ReviewOutput {
        retrievability,
        curve_retrievability: None,
        button_probabilities,
        current_interval: None,
        current_s90: None,
        intervals: [None; 4],
        s90s: [None; 4],
        card_state: take(next.card)?,
        deck_state: take(next.deck)?,
        note_state: take(next.note)?,
        preset_state: take(next.preset)?,
        global_state: take(next.global)?,
    })
}

/// An output of the instant-only model alone: its curve head is untrained.
fn instant_only_output(
    retrievability: f32,
    button_probabilities: [f32; 4],
) -> ReviewPredictionOutput {
    ReviewPredictionOutput {
        retrievability,
        curve_retrievability: None,
        button_probabilities,
        current_interval: None,
        current_s90: None,
        intervals: [None; 4],
        s90s: [None; 4],
    }
}

fn split_requests(
    requests: Vec<ReviewPredictionRequest>,
) -> io::Result<(Vec<ReviewPredictionRequest>, Vec<ReviewPredictionRequest>)> {
    let mut release = Vec::with_capacity(requests.len());
    let mut instant = Vec::with_capacity(requests.len());
    for request in requests {
        let (release_state, instant_state) = paired::split_owned_state(&request.state)?;
        release.push(ReviewPredictionRequest {
            input: request.input.clone(),
            state: release_state,
        });
        instant.push(ReviewPredictionRequest {
            input: request.input,
            state: instant_state,
        });
    }
    Ok((release, instant))
}

fn instant_requests(
    requests: Vec<ReviewPredictionRequest>,
) -> io::Result<Vec<ReviewPredictionRequest>> {
    requests
        .into_iter()
        .map(|request| {
            let (_, state) = paired::split_owned_state(&request.state)?;
            Ok(ReviewPredictionRequest {
                input: request.input,
                state,
            })
        })
        .collect()
}

fn release_requests(
    requests: Vec<ReviewPredictionRequest>,
) -> io::Result<Vec<ReviewPredictionRequest>> {
    requests
        .into_iter()
        .map(|request| {
            let (state, _) = paired::split_owned_state(&request.state)?;
            Ok(ReviewPredictionRequest {
                input: request.input,
                state,
            })
        })
        .collect()
}

fn spill_path_for_instant(path: &Path) -> PathBuf {
    let mut name = path.as_os_str().to_owned();
    name.push(".instant");
    PathBuf::from(name)
}

impl<E: Send> PairedRuntime<E> {
    /// Both models advance on every answer, in parallel; their states stay
    /// in step because they see the same inputs in the same order.
    fn advance<T, U>(
        &mut self,
        release: impl FnOnce(&mut E) -> io::Result<T> + Send,
        instant: impl FnOnce(&mut E) -> io::Result<U> + Send,
    ) -> io::Result<(T, U)>
    where
        T: Send,
        U: Send,
    {
        let (release_result, instant_result) =
            rayon::join(|| release(&mut self.release), || instant(&mut self.instant));
        Ok((release_result?, instant_result?))
    }
}

impl RwkvRuntime {
    pub fn load_with_precision(
        path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        model: ModelKind,
    ) -> io::Result<Self> {
        if model.instant_only() {
            // Small instant-only models need the small-model feature; the
            // instant-only big model (SM99) runs on the big engine of either
            // build.
            if model.small() && !super::SMALL_MODEL {
                return Err(invalid_input("unsupported instant-only RWKV model"));
            }
            return Ok(if model.on_primary_engine() {
                Self::InstantOnly(Box::new(Self::load_primary(
                    path,
                    target_retention,
                    max_interval_days,
                    precision,
                    model,
                )?))
            } else {
                Self::AlternateInstantOnly(Box::new(Self::load_alternate(
                    path,
                    target_retention,
                    max_interval_days,
                    precision,
                    model,
                )?))
            });
        }
        if model.on_primary_engine() {
            Ok(Self::Primary(Box::new(Self::load_primary(
                path,
                target_retention,
                max_interval_days,
                precision,
                model,
            )?)))
        } else {
            Ok(Self::Alternate(Box::new(Self::load_alternate(
                path,
                target_retention,
                max_interval_days,
                precision,
                model,
            )?)))
        }
    }

    fn load_alternate(
        path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        model: ModelKind,
    ) -> io::Result<super::alternate::RwkvInference> {
        let precision = match precision {
            StatePrecision::F32 => super::alternate::StatePrecision::F32,
            StatePrecision::Int8 => super::alternate::StatePrecision::Int8,
            StatePrecision::Int4 => super::alternate::StatePrecision::Int4,
            StatePrecision::Int4CardsInt8Notes => {
                super::alternate::StatePrecision::Int4CardsInt8Notes
            }
        };
        let engine = super::alternate::RwkvInference::load_with_precision(
            path,
            target_retention,
            max_interval_days,
            precision,
        )?;
        check_card_layers(model, engine.card_layers())?;
        if model.leak_free_inputs() {
            engine.with_leak_free_inputs()
        } else {
            Ok(engine)
        }
    }

    fn load_primary(
        path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        model: ModelKind,
    ) -> io::Result<super::RwkvInference> {
        let engine = super::RwkvInference::load_with_precision(
            path,
            target_retention,
            max_interval_days,
            precision,
        )?;
        check_card_layers(model, engine.card_layers())?;
        if model.leak_free_inputs() {
            engine.with_leak_free_inputs()
        } else {
            Ok(engine)
        }
    }

    /// Loads a release model with an instant model beside it. Both are
    /// distinct models of one architecture (two small models, which need
    /// the small-model feature, or the two big models on the big engine)
    /// with the same inputs and state precision; the instant model may be
    /// instant-only (its curve is never used) or a full model, whose curve
    /// and answer-time outputs are ignored.
    pub fn load_paired(
        release_path: PathBuf,
        instant_path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        release_model: ModelKind,
        instant_model: ModelKind,
    ) -> io::Result<Self> {
        if (release_model.small() && !super::SMALL_MODEL)
            || release_model.small() != instant_model.small()
            || release_model.instant_only()
            || instant_model == release_model
            || release_model.leak_free_inputs() != instant_model.leak_free_inputs()
        {
            return Err(invalid_input("unsupported RWKV model pair"));
        }
        if release_model.on_primary_engine() {
            let release = Self::load_primary(
                release_path,
                target_retention,
                max_interval_days,
                precision,
                release_model,
            )?;
            let instant = Self::load_primary(
                instant_path,
                target_retention,
                max_interval_days,
                precision,
                instant_model,
            )?;
            Ok(Self::Paired(Box::new(PairedRuntime { release, instant })))
        } else {
            let release = Self::load_alternate(
                release_path,
                target_retention,
                max_interval_days,
                precision,
                release_model,
            )?;
            let instant = Self::load_alternate(
                instant_path,
                target_retention,
                max_interval_days,
                precision,
                instant_model,
            )?;
            Ok(Self::AlternatePaired(Box::new(PairedRuntime {
                release,
                instant,
            })))
        }
    }

    pub fn is_paired(&self) -> bool {
        matches!(self, Self::Paired(_) | Self::AlternatePaired(_))
    }

    fn is_instant_only(&self) -> bool {
        matches!(self, Self::InstantOnly(_) | Self::AlternateInstantOnly(_))
    }

    /// Whether this runtime provides curve retrievability, intervals and S90.
    pub fn has_curve(&self) -> bool {
        !self.is_instant_only()
    }

    pub fn state_for_card(&self, card_id: i64) -> RwkvRuntimeState {
        match self {
            Self::Primary(engine) | Self::InstantOnly(engine) => {
                RwkvRuntimeState::Primary(engine.state_for_card(card_id))
            }
            Self::Alternate(engine) | Self::AlternateInstantOnly(engine) => {
                RwkvRuntimeState::Alternate(engine.state_for_card(card_id))
            }
            Self::Paired(pair) => RwkvRuntimeState::Paired(Box::new((
                pair.release.state_for_card(card_id),
                pair.instant.state_for_card(card_id),
            ))),
            Self::AlternatePaired(pair) => RwkvRuntimeState::AlternatePaired(Box::new((
                pair.release.state_for_card(card_id),
                pair.instant.state_for_card(card_id),
            ))),
        }
    }

    pub fn state_for_review(
        &self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
    ) -> RwkvRuntimeState {
        match self {
            Self::Primary(engine) | Self::InstantOnly(engine) => RwkvRuntimeState::Primary(
                engine.state_for_review(card_id, note_id, deck_id, preset_id),
            ),
            Self::Alternate(engine) | Self::AlternateInstantOnly(engine) => {
                RwkvRuntimeState::Alternate(
                    engine.state_for_review(card_id, note_id, deck_id, preset_id),
                )
            }
            Self::Paired(pair) => RwkvRuntimeState::Paired(Box::new((
                pair.release
                    .state_for_review(card_id, note_id, deck_id, preset_id),
                pair.instant
                    .state_for_review(card_id, note_id, deck_id, preset_id),
            ))),
            Self::AlternatePaired(pair) => RwkvRuntimeState::AlternatePaired(Box::new((
                pair.release
                    .state_for_review(card_id, note_id, deck_id, preset_id),
                pair.instant
                    .state_for_review(card_id, note_id, deck_id, preset_id),
            ))),
        }
    }

    pub fn restore_state(&mut self, state: &RwkvRuntimeState) -> io::Result<()> {
        match (self, state) {
            (
                Self::Primary(engine) | Self::InstantOnly(engine),
                RwkvRuntimeState::Primary(state),
            ) => engine.restore_state(state),
            (
                Self::Alternate(engine) | Self::AlternateInstantOnly(engine),
                RwkvRuntimeState::Alternate(state),
            ) => engine.restore_state(state),
            (Self::Paired(pair), RwkvRuntimeState::Paired(state)) => {
                pair.release.restore_state(&state.0);
                pair.instant.restore_state(&state.1);
            }
            (Self::AlternatePaired(pair), RwkvRuntimeState::AlternatePaired(state)) => {
                pair.release.restore_state(&state.0);
                pair.instant.restore_state(&state.1);
            }
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV undo state belongs to a different model",
                ))
            }
        }
        Ok(())
    }

    pub fn review(
        &mut self,
        input: ReviewInput,
        state: ReviewState<'_>,
    ) -> io::Result<ReviewOutput> {
        return_if_instant_only!(self, engine => instant_only_review_output(
            engine.review_query_outputs(input, state)?,
        ));
        dispatch!(self, engine, engine.review(input, state), pair => {
            let (release_state, instant_state) = paired::split_review_state(&state)?;
            let release_input = input.clone();
            let (mut output, (retrievability, button_probabilities, next)) = pair.advance(
                |release| release.review(release_input, paired::as_review_state(&release_state)),
                |instant| instant.review_query_outputs(input, paired::as_review_state(&instant_state)),
            )?;
            let join = |release: &[u8], instant: Option<Vec<u8>>| -> io::Result<Vec<u8>> {
                let instant = instant.ok_or_else(|| invalid_input("missing instant RWKV state"))?;
                Ok(paired::join_state(release, &instant))
            };
            output.retrievability = retrievability;
            output.button_probabilities = button_probabilities;
            output.card_state = join(&output.card_state, next.card)?;
            output.deck_state = join(&output.deck_state, next.deck)?;
            output.note_state = join(&output.note_state, next.note)?;
            output.preset_state = join(&output.preset_state, next.preset)?;
            output.global_state = join(&output.global_state, next.global)?;
            Ok(output)
        })
    }

    pub fn predict_many(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
    ) -> io::Result<Vec<ReviewPredictionOutput>> {
        return_if_instant_only!(self, engine => Ok(engine
            .predict_query_outputs_many(&requests)?
            .into_iter()
            .map(|(retrievability, buttons)| instant_only_output(retrievability, buttons))
            .collect()));
        dispatch!(self, engine, engine.predict_many(requests), pair => {
            let (release_requests, instant_requests) = split_requests(requests)?;
            let (mut outputs, instant_outputs) = pair.advance(
                |release| release.predict_many(release_requests),
                |instant| instant.predict_query_outputs_many(&instant_requests),
            )?;
            for (output, (retrievability, button_probabilities)) in
                outputs.iter_mut().zip(instant_outputs)
            {
                output.retrievability = retrievability;
                output.button_probabilities = button_probabilities;
            }
            Ok(outputs)
        })
    }

    pub fn predict_retrievability_many(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
    ) -> io::Result<Vec<f32>> {
        dispatch!(self, engine, engine.predict_retrievability_many(requests), pair => {
            pair.instant
                .predict_retrievability_many(instant_requests(requests)?)
        })
    }

    /// Whether the model folder had the optional answer-time head (the
    /// release's folder for a paired runtime).
    pub fn has_answer_time_head(&self) -> bool {
        if self.is_instant_only() {
            return false;
        }
        dispatch!(self, engine, engine.has_answer_time_head(), pair => {
            pair.release.has_answer_time_head()
        })
    }

    /// See [`super::RwkvInference::predict_answer_times`]. A paired runtime
    /// runs the head on the release's state, the features it was trained on.
    pub fn predict_answer_times(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
        history: Vec<[f32; super::ANSWER_TIME_FEATURES]>,
    ) -> io::Result<Vec<Option<super::AnswerTimePrediction>>> {
        if self.is_instant_only() {
            // The head was trained on the release's features, never these.
            return Ok(vec![None; requests.len()]);
        }
        dispatch!(self, engine, engine.predict_answer_times(requests, history), pair => {
            pair.release
                .predict_answer_times(release_requests(requests)?, history)
        })
    }

    pub fn predict_retrievability_many_from_warm_up(
        &mut self,
        inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<f32>> {
        dispatch!(
            self,
            engine,
            engine.predict_retrievability_many_from_warm_up(inputs),
            pair => pair.instant.predict_retrievability_many_from_warm_up(inputs)
        )
    }

    pub fn predict_retrievability_many_after_review(
        &self,
        answer: ReviewInput,
        query_inputs: Vec<ReviewInput>,
        snapshot: RwkvWorkloadSimulationSnapshot,
    ) -> io::Result<Vec<f32>> {
        dispatch!(
            self,
            engine,
            engine.predict_retrievability_many_after_review(answer, query_inputs, snapshot),
            pair => pair.instant.predict_retrievability_many_after_review(
                answer,
                query_inputs,
                paired::simulation_snapshot_half(snapshot, true)?,
            )
        )
    }

    pub fn predict_retrievability_many_after_reviews(
        &self,
        answers: Vec<ReviewInput>,
        query_inputs: Vec<ReviewInput>,
        snapshot: RwkvWorkloadSimulationSnapshot,
    ) -> io::Result<Vec<Vec<f32>>> {
        dispatch!(
            self,
            engine,
            engine.predict_retrievability_many_after_reviews(answers, query_inputs, snapshot),
            pair => pair.instant.predict_retrievability_many_after_reviews(
                answers,
                query_inputs,
                paired::simulation_snapshot_half(snapshot, true)?,
            )
        )
    }

    pub fn predict_retrievability_many_after_reviews_from_warm_up(
        &mut self,
        answers: Vec<ReviewInput>,
        query_inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<Vec<f32>>> {
        dispatch!(
            self,
            engine,
            engine.predict_retrievability_many_after_reviews_from_warm_up(answers, query_inputs),
            pair => pair
                .instant
                .predict_retrievability_many_after_reviews_from_warm_up(answers, query_inputs)
        )
    }

    pub fn warm_up_reviews(
        &mut self,
        reviews: Vec<ReviewInput>,
        record_predictions: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        dispatch!(self, engine, engine.warm_up_reviews(reviews, record_predictions), pair => {
            let release_reviews = reviews.clone();
            pair.advance(
                |release| release.warm_up_reviews(release_reviews, false),
                |instant| instant.warm_up_reviews(reviews, record_predictions),
            )
            .map(|(_, predictions)| predictions)
        })
    }

    pub fn forecast_history_predictions(
        &self,
        reviews: Vec<ReviewInput>,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<Vec<(usize, f32)>> {
        dispatch!(
            self,
            engine,
            engine.forecast_history_predictions(reviews, progress),
            pair => pair.instant.forecast_history_predictions(reviews, progress)
        )
    }

    /// Replay answers and return each answer's immediate post-review
    /// prediction.
    pub fn warm_up_reviews_post(
        &mut self,
        reviews: Vec<ReviewInput>,
    ) -> io::Result<Vec<(usize, f32)>> {
        dispatch!(self, engine, engine.warm_up_reviews_post(reviews), pair => {
            let release_reviews = reviews.clone();
            pair.advance(
                |release| release.warm_up_reviews(release_reviews, false),
                |instant| instant.warm_up_reviews_post(reviews),
            )
            .map(|(_, predictions)| predictions)
        })
    }

    pub fn warm_up_reviews_query(
        &mut self,
        reviews: Vec<ReviewInput>,
        queries: Vec<ReviewInput>,
        after: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        dispatch!(self, engine, engine.warm_up_reviews_query(reviews, queries, after), pair => {
            // Validate before either model advances (the instant model checks
            // the same rule before its replay).
            if reviews.len() != queries.len()
                || reviews.iter().zip(&queries).any(|(answer, query)| {
                    !query.is_query
                        || query.ease.is_some()
                        || query.duration_millis.is_some()
                        || query.card_id != answer.card_id
                        || query.note_id != answer.note_id
                        || query.deck_id != answer.deck_id
                        || query.preset_id != answer.preset_id
                })
            {
                return Err(invalid_input("invalid replay queries"));
            }
            let release_reviews = reviews.clone();
            pair.advance(
                |release| release.warm_up_reviews(release_reviews, false),
                |instant| instant.warm_up_reviews_query(reviews, queries, after),
            )
            .map(|(_, predictions)| predictions)
        })
    }

    pub fn warm_up_snapshot(&self) -> io::Result<RwkvWarmUpSnapshot> {
        dispatch!(self, engine, Ok(engine.warm_up_snapshot()), pair => {
            paired::join_warm_up_snapshot(
                pair.release.warm_up_snapshot(),
                pair.instant.warm_up_snapshot(),
            )
        })
    }

    pub fn warm_up_state(&self, input: &ReviewInput) -> io::Result<ReviewStateOwned> {
        dispatch!(self, engine, Ok(engine.warm_up_state(input)), pair => {
            paired::join_owned_state(
                pair.release.warm_up_state(input),
                pair.instant.warm_up_state(input),
            )
        })
    }

    pub fn append_warm_up_snapshot_binary(&self, path: PathBuf) -> io::Result<()> {
        dispatch!(self, engine, engine.append_warm_up_snapshot_binary(path), pair => {
            pair.release
                .append_paired_warm_up_snapshot_binary(&pair.instant, path)
        })
    }

    // Keep the engine checkpoint API unchanged at the dispatch boundary.
    #[allow(clippy::too_many_arguments)]
    pub fn write_warm_up_state_checkpoint(
        &mut self,
        path: PathBuf,
        store_generation: &str,
        parent_segment_id: Option<i64>,
        last_review_id: i64,
        review_count: i64,
        history_hash: &str,
        replay_key: &str,
        previous_review_ids: &[u8],
        previous_intervals: &[u8],
        review_counts: &[u8],
        full: bool,
        durable: bool,
    ) -> io::Result<i64> {
        dispatch!(
            self,
            engine,
            engine.write_warm_up_state_checkpoint(
                path,
                store_generation,
                parent_segment_id,
                last_review_id,
                review_count,
                history_hash,
                replay_key,
                previous_review_ids,
                previous_intervals,
                review_counts,
                full,
                durable
            ),
            pair => {
                let PairedRuntime { release, instant } = &mut **pair;
                release.write_paired_warm_up_state_checkpoint(
                    instant,
                    path,
                    store_generation,
                    parent_segment_id,
                    last_review_id,
                    review_count,
                    history_hash,
                    replay_key,
                    previous_review_ids,
                    previous_intervals,
                    review_counts,
                    full,
                    durable,
                )
            }
        )
    }

    pub fn finish_warm_up_state_checkpoints(&mut self) -> io::Result<()> {
        dispatch!(self, engine, engine.finish_warm_up_state_checkpoints(), pair => {
            pair.release.finish_warm_up_state_checkpoints()?;
            pair.instant.finish_warm_up_state_checkpoints()
        })
    }

    pub fn restore_warm_up_state_checkpoint(
        &mut self,
        path: PathBuf,
        store_generation: &str,
        segment_id: i64,
    ) -> io::Result<()> {
        dispatch!(
            self,
            engine,
            engine.restore_warm_up_state_checkpoint(path, store_generation, segment_id),
            pair => {
                let PairedRuntime { release, instant } = &mut **pair;
                release.restore_paired_warm_up_state_checkpoint(
                    instant,
                    path,
                    store_generation,
                    segment_id,
                )
            }
        )
    }

    pub fn restore_warm_up_snapshot(&mut self, snapshot: RwkvWarmUpSnapshot) -> io::Result<()> {
        dispatch!(self, engine, engine.restore_warm_up_snapshot(snapshot), pair => {
            let (release, instant) = paired::split_warm_up_snapshot(&snapshot)?;
            drop(snapshot);
            pair.release.restore_warm_up_snapshot(release)?;
            pair.instant.restore_warm_up_snapshot(instant)
        })
    }

    pub fn restore_warm_up_state(
        &mut self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
        state: ReviewStateOwned,
    ) -> io::Result<()> {
        dispatch!(
            self,
            engine,
            engine.restore_warm_up_state(card_id, note_id, deck_id, preset_id, state),
            pair => {
                let (release, instant) = paired::split_owned_state(&state)?;
                pair.release
                    .restore_warm_up_state(card_id, note_id, deck_id, preset_id, release)?;
                pair.instant
                    .restore_warm_up_state(card_id, note_id, deck_id, preset_id, instant)
            }
        )
    }

    pub fn stash_warm_up_state(&mut self) -> io::Result<()> {
        dispatch!(self, engine, engine.stash_warm_up_state(), pair => {
            pair.release.stash_warm_up_state()?;
            if let Err(error) = pair.instant.stash_warm_up_state() {
                pair.release.restore_stashed_warm_up_state()?;
                return Err(error);
            }
            Ok(())
        })
    }

    /// A paired runtime spills the instant model's state next to `path`.
    pub fn stash_warm_up_state_to_file(&mut self, path: &Path) -> io::Result<()> {
        dispatch!(self, engine, engine.stash_warm_up_state_to_file(path), pair => {
            pair.release.stash_warm_up_state_to_file(path)?;
            if let Err(error) = pair
                .instant
                .stash_warm_up_state_to_file(&spill_path_for_instant(path))
            {
                pair.release.restore_stashed_warm_up_state()?;
                return Err(error);
            }
            Ok(())
        })
    }

    pub fn restore_stashed_warm_up_state(&mut self) -> io::Result<()> {
        dispatch!(self, engine, engine.restore_stashed_warm_up_state(), pair => {
            pair.release.restore_stashed_warm_up_state()?;
            pair.instant.restore_stashed_warm_up_state()
        })
    }

    pub fn discard_stashed_warm_up_state(&mut self) {
        dispatch!(self, engine, engine.discard_stashed_warm_up_state(), pair => {
            pair.release.discard_stashed_warm_up_state();
            pair.instant.discard_stashed_warm_up_state();
        })
    }

    pub fn reset_warm_up_state(&mut self) {
        dispatch!(self, engine, engine.reset_warm_up_state(), pair => {
            pair.release.reset_warm_up_state();
            pair.instant.reset_warm_up_state();
        })
    }

    pub fn cache_state(&self) -> Vec<u8> {
        dispatch!(self, engine, engine.cache_state(), pair => {
            paired::join_cache(&pair.release.cache_state(), &pair.instant.cache_state())
        })
    }

    pub fn restore_cache_state(&mut self, bytes: &[u8]) -> io::Result<()> {
        dispatch!(self, engine, engine.restore_cache_state(bytes), pair => {
            let (release, instant) = paired::split_cache(bytes)?;
            pair.release.restore_cache_state(release)?;
            pair.instant.restore_cache_state(instant)
        })
    }

    /// Workload simulations schedule with the curve: a paired runtime runs
    /// them on the release model.
    pub fn simulate_workload(
        &self,
        inputs: Vec<RwkvWorkloadSimulationInput>,
        snapshot: RwkvWorkloadSimulationSnapshot,
        config: RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadSimulationOutput> {
        if self.is_instant_only() {
            return Err(no_curve_model());
        }
        dispatch!(
            self,
            engine,
            engine.simulate_workload(inputs, snapshot, config, progress),
            pair => pair.release.simulate_workload(
                inputs,
                paired::simulation_snapshot_half(snapshot, false)?,
                config,
                progress,
            )
        )
    }

    pub fn simulate_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvWorkloadSimulationInput>,
        config: RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadSimulationOutput> {
        if self.is_instant_only() {
            return Err(no_curve_model());
        }
        dispatch!(
            self,
            engine,
            engine.simulate_workload_from_warm_up(inputs, config, progress),
            pair => pair.release.simulate_workload_from_warm_up(inputs, config, progress)
        )
    }

    /// The forecast schedules RWKV presets with the curve, so a paired runtime
    /// runs it on the release model.
    pub fn forecast_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvForecastCard>,
        config: RwkvWorkloadForecastConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadForecastOutput> {
        if self.is_instant_only() {
            return Err(no_curve_model());
        }
        dispatch!(
            self,
            engine,
            engine.forecast_workload_from_warm_up(inputs, config, progress),
            pair => pair.release.forecast_workload_from_warm_up(inputs, config, progress)
        )
    }

    pub fn forecast_instant_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvInstantForecastCard>,
        config: RwkvInstantForecastConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
        day_progress: &mut dyn FnMut(u32, u32, [f32; 5]) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadForecastOutput> {
        dispatch!(
            self,
            engine,
            engine.forecast_instant_workload_from_warm_up(inputs, config, progress, day_progress),
            pair => pair.instant.forecast_instant_workload_from_warm_up(inputs, config, progress, day_progress)
        )
    }

    pub fn compare_workloads_from_warm_up(
        &self,
        inputs: Vec<RwkvComparisonCard>,
        config: RwkvComparisonConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvComparisonOutput> {
        if self.is_instant_only() {
            return Err(no_curve_model());
        }
        dispatch!(
            self,
            engine,
            engine.compare_workloads_from_warm_up(inputs, config, progress),
            pair => pair.release.compare_workloads_from_warm_up(inputs, config, progress)
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn load(model: ModelKind, precision: StatePrecision) -> RwkvRuntime {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(match model {
            ModelKind::Legacy => "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin",
            ModelKind::Sm40 => "../qt/aqt/rwkv_inference/sm40v2/weights.f32.bin",
            ModelKind::Sm36 => "../qt/aqt/rwkv_inference/sm36/weights.f32.bin",
            ModelKind::Sm55 => "../qt/aqt/rwkv_inference/sm55/weights.f32.bin",
            ModelKind::Sm56 => "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
            ModelKind::Sm72 => "../qt/aqt/rwkv_inference/sm72/weights.f32.bin",
            ModelKind::Sm72e20 => "../qt/aqt/rwkv_inference/sm72e20/weights.f32.bin",
            ModelKind::Sm72e25 => "../qt/aqt/rwkv_inference/sm72e25/weights.f32.bin",
            ModelKind::Sm92 => "../qt/aqt/rwkv_inference/sm92/weights.f32.bin",
            ModelKind::Sm57 => "../qt/aqt/rwkv_inference/sm57/weights.f32.bin",
            ModelKind::Sm98 => "../qt/aqt/rwkv_inference/sm98/weights.f32.bin",
            ModelKind::Sm101 => "../qt/aqt/rwkv_inference/sm101/weights.f32.bin",
            ModelKind::Sm99 => "../qt/aqt/rwkv_inference/sm99/weights.f32.bin",
            ModelKind::Sm106 => "../qt/aqt/rwkv_inference/sm106/weights.f32.bin",
        });
        RwkvRuntime::load_with_precision(path, 0.9, 36_500, precision, model).unwrap()
    }

    #[test]
    fn rwkv_runtime_restores_each_model_at_each_precision() {
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let mut legacy = load(ModelKind::Legacy, precision);
            let mut small = load(ModelKind::Sm40, precision);
            // Even an empty runtime snapshot must carry the model identity.
            assert!(legacy.restore_cache_state(&small.cache_state()).is_err());
            assert!(small.restore_cache_state(&legacy.cache_state()).is_err());
            assert!(legacy.restore_state(&small.state_for_card(100)).is_err());
            assert!(small.restore_state(&legacy.state_for_card(100)).is_err());
            for (model, mut engine) in [
                (ModelKind::Legacy, legacy),
                (ModelKind::Sm40, small),
                (ModelKind::Sm36, load(ModelKind::Sm36, precision)),
                (ModelKind::Sm55, load(ModelKind::Sm55, precision)),
                (ModelKind::Sm56, load(ModelKind::Sm56, precision)),
                (ModelKind::Sm72, load(ModelKind::Sm72, precision)),
                (ModelKind::Sm72e20, load(ModelKind::Sm72e20, precision)),
                (ModelKind::Sm72e25, load(ModelKind::Sm72e25, precision)),
                (ModelKind::Sm92, load(ModelKind::Sm92, precision)),
                (ModelKind::Sm106, load(ModelKind::Sm106, precision)),
            ] {
                let answers = crate::rwkv::precision_tests::reviews(14);
                engine.warm_up_reviews(answers.clone(), false).unwrap();
                let mut query = answers[0].clone();
                query.is_query = true;
                query.ease = None;
                query.duration_millis = None;
                let before = engine
                    .predict_retrievability_many_from_warm_up(vec![query.clone()])
                    .unwrap();
                let mut restored = load(model, precision);
                restored
                    .restore_warm_up_snapshot(engine.warm_up_snapshot().unwrap())
                    .unwrap();
                restored.restore_cache_state(&engine.cache_state()).unwrap();
                let after = restored
                    .predict_retrievability_many_from_warm_up(vec![query])
                    .unwrap();
                assert_eq!(before, after, "{model:?} {precision:?}");
            }
        }
        assert!(ModelKind::parse("unknown").is_err());
    }

    #[test]
    fn rwkv_runtime_feeds_leak_free_inputs_to_leak_free_models() {
        assert_eq!(ModelKind::parse("sm72").unwrap(), ModelKind::Sm72);
        assert_eq!(ModelKind::parse("sm72e20").unwrap(), ModelKind::Sm72e20);
        assert_eq!(ModelKind::parse("sm72e25").unwrap(), ModelKind::Sm72e25);
        assert_eq!(ModelKind::parse("sm92").unwrap(), ModelKind::Sm92);
        assert_eq!(ModelKind::parse("sm106").unwrap(), ModelKind::Sm106);
        // The same checkpoint without the runtime's input rule.
        let unadjusted = |model: ModelKind| {
            let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(match model {
                ModelKind::Legacy => "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin",
                ModelKind::Sm56 => "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
                ModelKind::Sm72e20 => "../qt/aqt/rwkv_inference/sm72e20/weights.f32.bin",
                ModelKind::Sm72e25 => "../qt/aqt/rwkv_inference/sm72e25/weights.f32.bin",
                ModelKind::Sm92 => "../qt/aqt/rwkv_inference/sm92/weights.f32.bin",
                ModelKind::Sm106 => "../qt/aqt/rwkv_inference/sm106/weights.f32.bin",
                _ => "../qt/aqt/rwkv_inference/sm72/weights.f32.bin",
            });
            // Legacy runs on the big engine, the small models on the small one.
            if super::super::SMALL_MODEL == model.small() {
                RwkvRuntime::Primary(Box::new(
                    super::super::RwkvInference::load(path, 0.9, 36_500).unwrap(),
                ))
            } else {
                RwkvRuntime::Alternate(Box::new(
                    super::super::alternate::RwkvInference::load(path, 0.9, 36_500).unwrap(),
                ))
            }
        };
        let predict = |mut engine: RwkvRuntime, missing_ids: bool| {
            let mut answers = crate::rwkv::precision_tests::reviews(14);
            if missing_ids {
                // Identities absent from the history set the missing-ID flags.
                for input in &mut answers {
                    input.note_id = None;
                    input.deck_id = None;
                    input.preset_id = None;
                }
            }
            let mut query = answers[0].clone();
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            engine.warm_up_reviews(answers, false).unwrap();
            engine
                .predict_retrievability_many_from_warm_up(vec![query])
                .unwrap()
        };
        for model in [
            ModelKind::Legacy,
            ModelKind::Sm56,
            ModelKind::Sm72,
            ModelKind::Sm72e20,
            ModelKind::Sm72e25,
            ModelKind::Sm92,
            ModelKind::Sm106,
        ] {
            assert_eq!(model.leak_free_inputs(), model != ModelKind::Sm56);
            for missing_ids in [false, true] {
                let runtime = predict(load(model, StatePrecision::F32), missing_ids);
                let raw = predict(unadjusted(model), missing_ids);
                // Only the leak-free models with missing identities see different inputs.
                assert_eq!(
                    runtime == raw,
                    !(model.leak_free_inputs() && missing_ids),
                    "{model:?} missing_ids={missing_ids}: {runtime:?} vs {raw:?}"
                );
            }
        }
        // Only the earlier small models keep the leak-input rule.
        for model in [ModelKind::Sm40, ModelKind::Sm36, ModelKind::Sm55] {
            assert!(!model.leak_free_inputs());
        }
    }

    /// The answer-time head is optional: a model folder without
    /// `time_head.f32.bin` loads as before and predicts no answer time, and
    /// loading the head changes no grade output.
    #[test]
    fn rwkv_runtime_answer_time_head_is_optional_and_leaves_grades_unchanged() {
        for (model, folder) in [(ModelKind::Sm72e25, "sm72e25"), (ModelKind::Sm92, "sm92")] {
            check_answer_time_head_is_optional(model, folder);
        }
        // Legacy never has a head; nor has the Tiny two-head model (SM106).
        assert!(!load(ModelKind::Legacy, StatePrecision::F32).has_answer_time_head());
        assert!(!load(ModelKind::Sm106, StatePrecision::F32).has_answer_time_head());
    }

    fn check_answer_time_head_is_optional(model: ModelKind, folder: &str) {
        let dir = tempfile::tempdir().unwrap();
        let bare = dir.path().join("weights.f32.bin");
        std::fs::copy(
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join(format!("../qt/aqt/rwkv_inference/{folder}/weights.f32.bin")),
            &bare,
        )
        .unwrap();
        let answers = crate::rwkv::precision_tests::reviews(21);
        let mut query = answers[0].clone();
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        query.current_elapsed_days = Some(3);
        query.current_elapsed_seconds = Some(3 * 86_400);
        let mut same_day = query.clone();
        same_day.current_elapsed_days = Some(0);
        same_day.current_elapsed_seconds = Some(600);
        let mut history = super::super::AnswerTimeHistory::new();
        for answer in &answers {
            history.push(
                answer.card_id,
                answer.ease.unwrap() as i64,
                answer.duration_millis.unwrap(),
            );
        }
        let features = history.features(query.card_id, 3);
        let run = |mut engine: RwkvRuntime| {
            engine.warm_up_reviews(answers.clone(), false).unwrap();
            let request = |input: &ReviewInput| ReviewPredictionRequest {
                input: input.clone(),
                state: engine.warm_up_state(input).unwrap(),
            };
            let requests = vec![request(&query), request(&same_day)];
            let again = vec![request(&query), request(&same_day)];
            let grades = engine.predict_many(requests).unwrap();
            let times = engine
                .predict_answer_times(again, vec![features; 2])
                .unwrap();
            let grades: Vec<_> = grades
                .into_iter()
                .map(|output| {
                    (
                        output.retrievability.to_bits(),
                        output.curve_retrievability.map(f32::to_bits),
                        output.button_probabilities.map(f32::to_bits),
                        output.intervals.map(|v| v.map(f32::to_bits)),
                        output.s90s.map(|v| v.map(f32::to_bits)),
                        output.current_interval,
                        output.current_s90.map(f32::to_bits),
                    )
                })
                .collect();
            (engine.has_answer_time_head(), grades, times)
        };
        let (with_head, grades, times) = run(load(model, StatePrecision::F32));
        let (without_head, bare_grades, bare_times) =
            run(
                RwkvRuntime::load_with_precision(bare, 0.9, 36_500, StatePrecision::F32, model)
                    .unwrap(),
            );
        assert!(with_head && !without_head);
        assert_eq!(grades, bare_grades);
        assert_eq!(bare_times, vec![None, None]);
        let prediction = times[0]
            .as_ref()
            .expect("a later-day query has an answer time");
        assert!(times[1].is_none(), "same-day queries have no answer time");
        for grade in 0..4 {
            let total: f32 = prediction.probabilities[grade].iter().sum();
            assert!((total - 1.0).abs() < 1e-5);
            let median = prediction.median_seconds(grade);
            assert!(median > 0.25 && median <= 60.0, "{median}");
        }
        assert_eq!(
            prediction.button_probabilities.map(f32::to_bits),
            grades[0].2,
            "the head's trunk pass gives the grade path's button probabilities"
        );
    }

    fn model_path(folder: &str) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join(format!("../qt/aqt/rwkv_inference/{folder}/weights.f32.bin"))
    }

    /// The instant models the paired runtime accepts beside the release:
    /// the show-time instant-only model, stage 2 (SM98, the Small choice's
    /// RWKV-Instant model), the smaller instant-only model (SM101, the Tiny
    /// choice's, one card layer), the show-time retrain (SM92) and the
    /// instant-only model (SM57).
    const INSTANT_MODELS: [(ModelKind, &str); 4] = [
        (ModelKind::Sm98, "sm98"),
        (ModelKind::Sm101, "sm101"),
        (ModelKind::Sm92, "sm92"),
        (ModelKind::Sm57, "sm57"),
    ];

    /// The instant-only models: never a full model alone.
    const INSTANT_ONLY_MODELS: [(ModelKind, &str); 3] = [
        (ModelKind::Sm57, "sm57"),
        (ModelKind::Sm98, "sm98"),
        (ModelKind::Sm101, "sm101"),
    ];

    /// A model key loads only weights with its card layers: the tiny model's
    /// key refuses two-layer weights and every other small key refuses the
    /// tiny weights, alone or in a pair.
    #[test]
    fn rwkv_runtime_refuses_weights_of_another_layer_plan() {
        if !super::super::SMALL_MODEL {
            return;
        }
        for (model, folder) in [
            (ModelKind::Sm101, "sm98"),
            (ModelKind::Sm98, "sm101"),
            (ModelKind::Sm72e25, "sm101"),
            (ModelKind::Sm106, "sm92"),
            (ModelKind::Sm92, "sm106"),
            (ModelKind::Sm72e25, "sm106"),
        ] {
            assert!(
                RwkvRuntime::load_with_precision(
                    model_path(folder),
                    0.9,
                    36_500,
                    StatePrecision::F32,
                    model,
                )
                .is_err(),
                "{model:?} with {folder}"
            );
        }
        assert!(RwkvRuntime::load_paired(
            model_path("sm72e25"),
            model_path("sm98"),
            0.9,
            36_500,
            StatePrecision::F32,
            ModelKind::Sm72e25,
            ModelKind::Sm101,
        )
        .is_err());
        assert!(RwkvRuntime::load_paired(
            model_path("sm101"),
            model_path("sm101"),
            0.9,
            36_500,
            StatePrecision::F32,
            ModelKind::Sm72e25,
            ModelKind::Sm101,
        )
        .is_err());
    }

    /// The Tiny two-head model (SM106) alone is a full runtime on the tiny
    /// plan: curve recall, intervals and S90 beside instant recall and
    /// buttons, one card layer, no answer-time head, at every state
    /// precision.
    #[test]
    fn rwkv_sm106_alone_is_a_full_tiny_model() {
        assert!(!ModelKind::Sm106.instant_only() && ModelKind::Sm106.leak_free_inputs());
        assert!(ModelKind::Sm106.small() && ModelKind::Sm106.card_layers() == Some(1));
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let mut engine = load(ModelKind::Sm106, precision);
            assert!(!engine.is_paired() && engine.has_curve(), "{precision:?}");
            assert!(!engine.has_answer_time_head(), "{precision:?}");
            let answers = crate::rwkv::precision_tests::reviews(14);
            engine.warm_up_reviews(answers.clone(), false).unwrap();
            let review = review_with_state(&mut engine, &query_of(&answers[0], 2));
            assert!(
                review.intervals.iter().all(Option::is_some),
                "{precision:?}"
            );
            assert!(review.s90s.iter().all(Option::is_some), "{precision:?}");
            assert!(review.curve_retrievability.is_some(), "{precision:?}");
            assert!(
                (0.0..=1.0).contains(&review.retrievability)
                    && review.button_probabilities.iter().all(|p| p.is_finite()),
                "{precision:?}"
            );
        }
    }

    /// An instant model as a plain engine (for SM57 with its raw, untrained
    /// curve outputs): the reference for the paired runtime's instant outputs.
    fn instant_alone_of(
        precision: StatePrecision,
        (model, folder): (ModelKind, &str),
    ) -> RwkvRuntime {
        RwkvRuntime::Primary(Box::new(
            RwkvRuntime::load_primary(model_path(folder), 0.9, 36_500, precision, model).unwrap(),
        ))
    }

    fn paired_with(precision: StatePrecision, (model, folder): (ModelKind, &str)) -> RwkvRuntime {
        RwkvRuntime::load_paired(
            model_path("sm72e25"),
            model_path(folder),
            0.9,
            36_500,
            precision,
            ModelKind::Sm72e25,
            model,
        )
        .unwrap()
    }

    fn paired(precision: StatePrecision) -> RwkvRuntime {
        paired_with(precision, (ModelKind::Sm57, "sm57"))
    }

    fn query_of(answer: &ReviewInput, days: i64) -> ReviewInput {
        let mut query = answer.clone();
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        query.current_elapsed_days = Some(days);
        query.current_elapsed_seconds = Some(days * 86_400);
        query
    }

    /// An answer after the whole test history (`reviews` ends on day 1003):
    /// replaying an earlier day would move time backwards.
    fn later(answer: &ReviewInput) -> ReviewInput {
        let mut answer = answer.clone();
        answer.day_offset = Some(1010);
        answer.current_elapsed_days = Some(7);
        answer.current_elapsed_seconds = Some(7 * 86_400);
        answer
    }

    fn bits(values: &[f32]) -> Vec<u32> {
        values.iter().map(|value| value.to_bits()).collect()
    }

    fn prediction_bits(predictions: &[(usize, f32)]) -> Vec<(usize, u32)> {
        predictions
            .iter()
            .map(|(index, value)| (*index, value.to_bits()))
            .collect()
    }

    type OutputBits = (
        u32,
        [u32; 4],
        Option<u32>,
        [Option<u32>; 4],
        [Option<u32>; 4],
        Option<u32>,
        Option<u32>,
    );

    fn output_bits(outputs: &[ReviewPredictionOutput]) -> Vec<OutputBits> {
        outputs
            .iter()
            .map(|output| {
                (
                    output.retrievability.to_bits(),
                    output.button_probabilities.map(f32::to_bits),
                    output.curve_retrievability.map(f32::to_bits),
                    output.intervals.map(|v| v.map(f32::to_bits)),
                    output.s90s.map(|v| v.map(f32::to_bits)),
                    output.current_interval,
                    output.current_s90.map(f32::to_bits),
                )
            })
            .collect()
    }

    fn requests(engine: &RwkvRuntime, queries: &[ReviewInput]) -> Vec<ReviewPredictionRequest> {
        queries
            .iter()
            .map(|input| ReviewPredictionRequest {
                input: input.clone(),
                state: engine.warm_up_state(input).unwrap(),
            })
            .collect()
    }

    /// Everything a paired runtime outputs, compared bit for bit with the
    /// two models run alone: instant recall and button probabilities from
    /// the instant-only model, the rest from the release.
    fn check_paired_outputs(
        pair: &mut RwkvRuntime,
        release: &mut RwkvRuntime,
        instant: &mut RwkvRuntime,
        queries: &[ReviewInput],
        label: &str,
    ) {
        let pair_r = pair
            .predict_retrievability_many_from_warm_up(queries.to_vec())
            .unwrap();
        let instant_r = instant
            .predict_retrievability_many_from_warm_up(queries.to_vec())
            .unwrap();
        assert_eq!(bits(&pair_r), bits(&instant_r), "{label}: resident R");
        let release_r = release
            .predict_retrievability_many_from_warm_up(queries.to_vec())
            .unwrap();
        assert_ne!(bits(&pair_r), bits(&release_r), "{label}: models differ");

        let pair_out = output_bits(&pair.predict_many(requests(pair, queries)).unwrap());
        let release_out = output_bits(&release.predict_many(requests(release, queries)).unwrap());
        let instant_out = output_bits(&instant.predict_many(requests(instant, queries)).unwrap());
        for ((pair, release), instant) in pair_out.iter().zip(&release_out).zip(&instant_out) {
            assert_eq!(
                (pair.0, pair.1),
                (instant.0, instant.1),
                "{label}: instant outputs"
            );
            assert_eq!(
                (pair.2, pair.3, pair.4, pair.5, pair.6),
                (release.2, release.3, release.4, release.5, release.6),
                "{label}: curve outputs"
            );
        }
        assert_eq!(
            bits(
                &pair
                    .predict_retrievability_many(requests(pair, queries))
                    .unwrap()
            ),
            bits(
                &instant
                    .predict_retrievability_many(requests(instant, queries))
                    .unwrap()
            ),
            "{label}: request R"
        );

        let history = vec![[0.25; super::super::ANSWER_TIME_FEATURES]; queries.len()];
        // The pair answers time questions exactly as its release does (the Big
        // release has no answer-time head).
        assert_eq!(pair.has_answer_time_head(), release.has_answer_time_head());
        assert_eq!(
            pair.predict_answer_times(requests(pair, queries), history.clone())
                .unwrap(),
            release
                .predict_answer_times(requests(release, queries), history)
                .unwrap(),
            "{label}: answer time stays on the release state"
        );
    }

    fn review_with_state(engine: &mut RwkvRuntime, input: &ReviewInput) -> ReviewOutput {
        let state = engine.warm_up_state(input).unwrap();
        engine
            .review(input.clone(), paired::as_review_state(&state))
            .unwrap()
    }

    #[test]
    fn rwkv_paired_runtime_matches_each_model_alone() {
        if !super::super::SMALL_MODEL {
            // Without the small-model feature, small models run on the
            // alternate engine, which has no paired or instant-only mode.
            assert!(RwkvRuntime::load_paired(
                model_path("sm72e25"),
                model_path("sm57"),
                0.9,
                36_500,
                StatePrecision::F32,
                ModelKind::Sm72e25,
                ModelKind::Sm57,
            )
            .is_err());
            return;
        }
        for (model, name) in INSTANT_ONLY_MODELS {
            assert!(model.instant_only() && model.leak_free_inputs());
            assert_eq!(ModelKind::parse(name).unwrap(), model);
        }
        for (release_model, instant_model) in [
            (ModelKind::Sm72e25, ModelKind::Sm72e25),
            (ModelKind::Sm92, ModelKind::Sm92),
            (ModelKind::Sm72e25, ModelKind::Legacy),
            (ModelKind::Sm72e25, ModelKind::Sm56),
            (ModelKind::Sm57, ModelKind::Sm92),
            (ModelKind::Sm57, ModelKind::Sm57),
            (ModelKind::Legacy, ModelKind::Sm57),
            (ModelKind::Sm56, ModelKind::Sm57),
            // The show-time instant-only model, stage 2 (SM98): only beside a
            // full leak-free release, never as the release side.
            (ModelKind::Sm98, ModelKind::Sm98),
            (ModelKind::Sm98, ModelKind::Sm72e25),
            (ModelKind::Sm98, ModelKind::Sm57),
            (ModelKind::Legacy, ModelKind::Sm98),
            (ModelKind::Sm56, ModelKind::Sm98),
            // The smaller instant-only model (SM101): the same rule.
            (ModelKind::Sm101, ModelKind::Sm101),
            (ModelKind::Sm101, ModelKind::Sm72e25),
            (ModelKind::Sm101, ModelKind::Sm98),
            (ModelKind::Sm98, ModelKind::Sm101),
            (ModelKind::Legacy, ModelKind::Sm101),
            (ModelKind::Sm56, ModelKind::Sm101),
        ] {
            assert!(RwkvRuntime::load_paired(
                model_path("sm72e25"),
                model_path("sm57"),
                0.9,
                36_500,
                StatePrecision::F32,
                release_model,
                instant_model,
            )
            .is_err());
        }
        for (instant_model, precision) in INSTANT_MODELS.into_iter().flat_map(|model| {
            [
                StatePrecision::F32,
                StatePrecision::Int8,
                StatePrecision::Int4CardsInt8Notes,
            ]
            .map(|p| (model, p))
        }) {
            let label = format!("{} {precision:?}", instant_model.1);
            let pair = paired_with(precision, instant_model);
            let release = load(ModelKind::Sm72e25, precision);
            let instant = instant_alone_of(precision, instant_model);
            assert!(pair.is_paired() && pair.has_curve() && !release.is_paired());
            // Only the instant-only models (SM57, SM98, SM101) lack an
            // answer-time head.
            assert_eq!(
                instant.has_answer_time_head(),
                !instant_model.0.instant_only()
            );
            assert!(pair.has_answer_time_head() && release.has_answer_time_head());
            check_pair_matches_models(pair, release, instant, label);
        }
    }

    /// The pairs of the RWKV family release's Small and Tiny sizes (the
    /// family wiring (SM114)): the show-time retrain (SM92) beside the Small
    /// Instant-Only model (SM98), and the Tiny two-head model (SM106) beside
    /// the Tiny Instant-Only model (SM101). Instant recall and buttons come
    /// from the Instant-Only model, the curve, intervals, S90 and answer time
    /// from the two-head model, bit for bit, at every state precision. The
    /// Tiny pair has no answer-time head, like SM106 alone.
    #[test]
    fn rwkv_family_pairs_match_each_model_alone() {
        if !super::super::SMALL_MODEL {
            // Small models run on the alternate engine, which has no pairs.
            assert!(RwkvRuntime::load_paired(
                model_path("sm106"),
                model_path("sm101"),
                0.9,
                36_500,
                StatePrecision::F32,
                ModelKind::Sm106,
                ModelKind::Sm101,
            )
            .is_err());
            return;
        }
        // (two-head model, Instant-Only model, stored pair bytes per card and
        // note at int4 cards + int8 notes)
        for (release, instant, stored) in [
            ((ModelKind::Sm92, "sm92"), (ModelKind::Sm98, "sm98"), 7_098),
            (
                (ModelKind::Sm106, "sm106"),
                (ModelKind::Sm101, "sm101"),
                5_282,
            ),
        ] {
            for precision in [
                StatePrecision::F32,
                StatePrecision::Int8,
                StatePrecision::Int4CardsInt8Notes,
            ] {
                let label = format!("{}+{} {precision:?}", release.1, instant.1);
                let pair = || {
                    RwkvRuntime::load_paired(
                        model_path(release.1),
                        model_path(instant.1),
                        0.9,
                        36_500,
                        precision,
                        release.0,
                        instant.0,
                    )
                    .unwrap()
                };
                let alone = load(release.0, precision);
                let instant_alone = instant_alone_of(precision, instant);
                assert!(pair().is_paired() && pair().has_curve(), "{label}");
                let head = release.0 == ModelKind::Sm92;
                assert_eq!(pair().has_answer_time_head(), head, "{label}");
                assert_eq!(alone.has_answer_time_head(), head, "{label}");
                if precision == StatePrecision::Int4CardsInt8Notes {
                    let mut engine = pair();
                    let answers = crate::rwkv::precision_tests::reviews(14);
                    engine.warm_up_reviews(answers.clone(), false).unwrap();
                    let state = engine.warm_up_state(&answers[0]).unwrap();
                    assert_eq!(
                        state.card.unwrap().len() + state.note.unwrap().len(),
                        stored,
                        "{label}"
                    );
                }
                check_pair_matches_models(pair(), alone, instant_alone, label);
            }
        }
    }

    /// Replays answers, queries and reviews through a pair and through its two
    /// models alone: instant outputs from the instant model, the rest from the
    /// release, bit for bit.
    fn check_pair_matches_models(
        mut pair: RwkvRuntime,
        mut release: RwkvRuntime,
        mut instant: RwkvRuntime,
        label: String,
    ) {
        let answers = crate::rwkv::precision_tests::reviews(21);
        let (first, rest) = answers.split_at(14);
        let pair_predictions = pair.warm_up_reviews(first.to_vec(), true).unwrap();
        release.warm_up_reviews(first.to_vec(), true).unwrap();
        let instant_predictions = instant.warm_up_reviews(first.to_vec(), true).unwrap();
        assert_eq!(
            prediction_bits(&pair_predictions),
            prediction_bits(&instant_predictions)
        );
        let queries: Vec<_> = answers[..7]
            .iter()
            .map(|answer| query_of(answer, 3))
            .collect();
        check_paired_outputs(&mut pair, &mut release, &mut instant, &queries, &label);

        // One answer through `review` with explicit states.
        let answer = rest[0].clone();
        let pair_review = review_with_state(&mut pair, &answer);
        let release_review = review_with_state(&mut release, &answer);
        let instant_review = review_with_state(&mut instant, &answer);
        assert_eq!(
            pair_review.retrievability.to_bits(),
            instant_review.retrievability.to_bits()
        );
        assert_eq!(
            pair_review.button_probabilities.map(f32::to_bits),
            instant_review.button_probabilities.map(f32::to_bits)
        );
        assert_eq!(
            pair_review.current_interval,
            release_review.current_interval
        );
        assert_eq!(
            pair_review.curve_retrievability.map(f32::to_bits),
            release_review.curve_retrievability.map(f32::to_bits)
        );
        assert_eq!(
            paired::split_state(&pair_review.card_state).unwrap(),
            (
                &release_review.card_state[..],
                &instant_review.card_state[..]
            )
        );
        // A query through `review`: intervals from the release.
        let query = query_of(&answer, 2);
        let pair_query = review_with_state(&mut pair, &query);
        let release_query = review_with_state(&mut release, &query);
        let instant_query = review_with_state(&mut instant, &query);
        assert_eq!(
            pair_query.retrievability.to_bits(),
            instant_query.retrievability.to_bits()
        );
        assert!(pair_query.intervals.iter().all(Option::is_some));
        assert_eq!(
            pair_query.intervals.map(|v| v.map(f32::to_bits)),
            release_query.intervals.map(|v| v.map(f32::to_bits))
        );
        assert_eq!(
            pair_query.s90s.map(|v| v.map(f32::to_bits)),
            release_query.s90s.map(|v| v.map(f32::to_bits))
        );

        // The other replay paths advance both models.
        let post = rest[1..4].to_vec();
        assert_eq!(
            prediction_bits(&pair.warm_up_reviews_post(post.clone()).unwrap()),
            prediction_bits(&instant.warm_up_reviews_post(post.clone()).unwrap())
        );
        release.warm_up_reviews(post, false).unwrap();
        let replay = rest[4..].to_vec();
        let replay_queries: Vec<_> = replay.iter().map(|answer| query_of(answer, 1)).collect();
        assert!(pair
            .warm_up_reviews_query(replay.clone(), replay_queries[..1].to_vec(), false)
            .is_err());
        assert_eq!(
            prediction_bits(
                &pair
                    .warm_up_reviews_query(replay.clone(), replay_queries.clone(), true)
                    .unwrap()
            ),
            prediction_bits(
                &instant
                    .warm_up_reviews_query(replay.clone(), replay_queries, true)
                    .unwrap()
            )
        );
        release.warm_up_reviews(replay, false).unwrap();
        check_paired_outputs(
            &mut pair,
            &mut release,
            &mut instant,
            &queries,
            &format!("{label} after review"),
        );

        // Future recall after a hypothetical answer, from a snapshot.
        let simulation_snapshot = |engine: &RwkvRuntime| {
            let snapshot = engine.warm_up_snapshot().unwrap();
            RwkvWorkloadSimulationSnapshot {
                card_states: snapshot.card_states,
                note_states: snapshot.note_states,
                deck_states: snapshot.deck_states,
                preset_states: snapshot.preset_states,
                global_state: snapshot.global_state,
                runtime_state: Some(engine.cache_state()),
            }
        };
        let future = |engine: &RwkvRuntime| {
            bits(
                &engine
                    .predict_retrievability_many_after_reviews(
                        vec![later(&answers[0])],
                        vec![query_of(&later(&answers[0]), 4)],
                        simulation_snapshot(engine),
                    )
                    .unwrap()
                    .concat(),
            )
        };
        assert_eq!(future(&pair), future(&instant), "{label}: future R");
    }

    /// The instant-only model alone (a profile with RWKV-Instant and no
    /// RWKV-Curve) gives its instant outputs and never a curve output.
    #[test]
    fn rwkv_instant_only_runtime_serves_no_curve() {
        if !super::super::SMALL_MODEL {
            // Without the small-model feature, small models run on the
            // alternate engine, which has no paired or instant-only mode.
            assert!(RwkvRuntime::load_paired(
                model_path("sm72e25"),
                model_path("sm57"),
                0.9,
                36_500,
                StatePrecision::F32,
                ModelKind::Sm72e25,
                ModelKind::Sm57,
            )
            .is_err());
            return;
        }
        // Each instant-only model alone loads in the instant-only mode, never
        // as a full model with its untrained curve.
        for (instant_model, precision) in INSTANT_ONLY_MODELS.into_iter().flat_map(|model| {
            [
                StatePrecision::F32,
                StatePrecision::Int8,
                StatePrecision::Int4CardsInt8Notes,
            ]
            .map(|p| (model, p))
        }) {
            let alone = RwkvRuntime::load_with_precision(
                model_path(instant_model.1),
                0.9,
                36_500,
                precision,
                instant_model.0,
            )
            .unwrap();
            let raw = instant_alone_of(precision, instant_model);
            assert!(!alone.has_curve() && !alone.is_paired() && raw.has_curve());
            check_instant_only_alone(alone, raw);
        }
    }

    #[test]
    fn instant_forecast_pulls_future_card_for_daily_minimum() {
        if super::super::SMALL_MODEL {
            check_instant_forecast_pulls_future_card(load(ModelKind::Sm57, StatePrecision::F32));
        }
        check_instant_forecast_pulls_future_card(load(ModelKind::Sm99, StatePrecision::F32));
        check_instant_forecast_pulls_future_card(big_pair(StatePrecision::F32));
    }

    fn check_instant_forecast_pulls_future_card(mut runtime: RwkvRuntime) {
        let answers = crate::rwkv::precision_tests::reviews(4);
        runtime.warm_up_reviews(answers.clone(), false).unwrap();
        let query = query_of(&answers[0], 3);
        let mut deck = crate::decks::Deck::new_normal();
        deck.id = crate::decks::DeckId(1);
        deck.name = crate::decks::NativeDeckName::from_human_name("Study");
        let mut preset = crate::deckconfig::DeckConfig {
            id: crate::deckconfig::DeckConfigId(1),
            ..Default::default()
        };
        preset.inner.rwkv_review_instant_order_enabled = true;
        preset.inner.rwkv_review_minimum_reviews_per_day = 1;
        preset.inner.reviews_per_day = 1;
        let output = runtime
            .forecast_instant_workload_from_warm_up(
                vec![RwkvInstantForecastCard {
                    input: RwkvWorkloadSimulationInput {
                        review_input: query,
                        interval_days: Some(30),
                        ease_factor: Some(2500),
                        reps: Some(3),
                        lapses: Some(0),
                    },
                    due_day: 30,
                    group: 1,
                    desired_retention: 0.01,
                    instant_enabled: true,
                    intervening_reviews: None,
                    last_kind_and_grade: None,
                    queue: 2,
                    learning_due_millis: 0,
                    remaining_steps: 0,
                    new_position: 0,
                    fsrs_memory: None,
                    fsrs_parameters: vec![],
                    fsrs_historical_retention: 0.9,
                    fsrs_enabled: false,
                }],
                RwkvInstantForecastConfig {
                    days: 1,
                    paths: 1,
                    scenario_sigma: None,
                    stratified_answers: false,
                    stratified_scale: 1.0,
                    stratified_bias_sd: 0.0,
                    stratified_review_variance: 0.0,
                    report_days: false,
                    seed: 0,
                    decks: vec![deck],
                    deck_configs: vec![preset],
                    study_deck_id: 1,
                    today: 0,
                    new_cards_ignore_review_limit: true,
                    fsrs_learning_queues_disabled: false,
                    fsrs_short_term_with_steps_enabled: true,
                    learn_ahead_secs: 0,
                    timing_patterns: vec![vec![(10_000, 2_000), (15_000, 3_000)]],
                    timing_pattern_success_shares: vec![[0.0, 0.0, 1.0, 0.0]],
                    review_time_model: None,
                    now_millis: 0,
                    same_day_allowed: false,
                    min_intervening_reviews: 0,
                    min_elapsed_secs: 0,
                    refresh_interval: 1,
                    approximate_refresh_answers: 0,
                    candidate_refresh_enabled: false,
                    candidate_refresh_limit: 64,
                    precompute_next_card: true,
                    review_order:
                        anki_proto::deck_config::deck_config::config::ReviewCardOrder::Day,
                    review_model: RwkvWorkloadReviewModel {
                        grade_seconds: [1.0; 4],
                        bucket_probabilities: Vec::new(),
                    },
                },
                &mut |_, _| Ok(()),
                &mut |_, _, _| Ok(()),
            )
            .unwrap();
        assert_eq!(output.days[0].done.mean, 1.0);
    }

    /// An instant-only runtime against the same weights as a plain engine:
    /// the same instant outputs and states, no curve output, no simulation.
    fn check_instant_only_alone(mut alone: RwkvRuntime, mut raw: RwkvRuntime) {
        let answers = crate::rwkv::precision_tests::reviews(21);
        assert_eq!(
            prediction_bits(&alone.warm_up_reviews(answers.clone(), true).unwrap()),
            prediction_bits(&raw.warm_up_reviews(answers.clone(), true).unwrap())
        );
        let queries: Vec<_> = answers[..7]
            .iter()
            .map(|answer| query_of(answer, 3))
            .collect();
        let alone_out = output_bits(&alone.predict_many(requests(&alone, &queries)).unwrap());
        let raw_out = output_bits(&raw.predict_many(requests(&raw, &queries)).unwrap());
        for (alone, raw) in alone_out.iter().zip(&raw_out) {
            assert_eq!((alone.0, alone.1), (raw.0, raw.1));
            assert_eq!(
                (alone.2, alone.3, alone.4, alone.5, alone.6),
                (None, [None; 4], [None; 4], None, None)
            );
        }
        let query = review_with_state(&mut alone, &queries[0]);
        assert!(query.curve_retrievability.is_none() && query.intervals == [None; 4]);
        assert_eq!(
            query.retrievability.to_bits(),
            review_with_state(&mut raw, &queries[0])
                .retrievability
                .to_bits()
        );
        // Answers through `review` advance the state as the raw engine does.
        let answer = later(&answers[3]);
        let alone_answer = review_with_state(&mut alone, &answer);
        let raw_answer = review_with_state(&mut raw, &answer);
        assert_eq!(alone_answer.card_state, raw_answer.card_state);
        assert_eq!(alone_answer.global_state, raw_answer.global_state);
        assert_eq!(
            bits(
                &alone
                    .predict_retrievability_many_from_warm_up(queries.clone())
                    .unwrap()
            ),
            bits(
                &raw.predict_retrievability_many_from_warm_up(queries)
                    .unwrap()
            )
        );
        assert!(!alone.has_answer_time_head());
        let config = || RwkvWorkloadSimulationConfig {
            min_dr: 80,
            max_dr: 90,
            target_dr_step: 5,
            days_to_simulate: 1,
            review_limit: 10,
            new_limit: 0,
            new_cards_ignore_review_limit: false,
            max_interval: 36_500,
            review_order: anki_proto::deck_config::deck_config::config::ReviewCardOrder::Day,
            suspend_after_lapses: None,
            state_update_interval: 1,
            review_model: RwkvWorkloadReviewModel {
                grade_seconds: [1.0; 4],
                bucket_probabilities: Vec::new(),
            },
        };
        assert!(alone
            .simulate_workload_from_warm_up(Vec::new(), config(), &mut |_, _| Ok(()))
            .is_err());
        let error = alone
            .forecast_workload_from_warm_up(
                Vec::new(),
                RwkvWorkloadForecastConfig {
                    days: 1,
                    paths: 1,
                    seed: 0,
                    groups: Vec::new(),
                    default_review_limit: 10,
                    default_new_limit: 0,
                    new_cards_ignore_review_limit: false,
                    max_interval: 36_500,
                    review_order:
                        anki_proto::deck_config::deck_config::config::ReviewCardOrder::Day,
                    suspend_after_lapses: None,
                    state_update_interval: 1,
                    single_step_answers: false,
                    review_model: RwkvWorkloadReviewModel {
                        grade_seconds: [1.0; 4],
                        bucket_probabilities: Vec::new(),
                    },
                },
                &mut |_, _| Ok(()),
            )
            .expect_err("an instant-only model cannot forecast with a curve");
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput);
        assert!(error.to_string().contains("RWKV-Curve"));
    }

    fn read_u32(bytes: &[u8], at: &mut usize) -> usize {
        let value = u32::from_le_bytes(bytes[*at..*at + 4].try_into().unwrap());
        *at += 4;
        value as usize
    }

    type SnapshotBody = (Vec<Vec<(i64, Vec<u8>)>>, Option<Vec<u8>>, Vec<u8>);

    /// The snapshot file layout that the desktop parses (`ARWKVSNAPSHOT12`
    /// body): four id-to-state maps, the global state and the runtime cache.
    fn parse_snapshot_body(bytes: &[u8]) -> SnapshotBody {
        let mut at = 0;
        let mut maps = Vec::new();
        for _ in 0..4 {
            let mut map = Vec::new();
            for _ in 0..read_u32(bytes, &mut at) {
                let identity = i64::from_le_bytes(bytes[at..at + 8].try_into().unwrap());
                at += 8;
                let len = read_u32(bytes, &mut at);
                map.push((identity, bytes[at..at + len].to_vec()));
                at += len;
            }
            maps.push(map);
        }
        let mut optional = || {
            let flag = bytes[at];
            at += 1;
            (flag == 1).then(|| {
                let len = read_u32(bytes, &mut at);
                at += len;
                bytes[at - len..at].to_vec()
            })
        };
        let global = optional();
        let cache = optional().unwrap();
        assert_eq!(at, bytes.len());
        (maps, global, cache)
    }

    #[test]
    fn rwkv_paired_runtime_state_round_trips() {
        if !super::super::SMALL_MODEL {
            // Without the small-model feature, small models run on the
            // alternate engine, which has no paired or instant-only mode.
            assert!(RwkvRuntime::load_paired(
                model_path("sm72e25"),
                model_path("sm57"),
                0.9,
                36_500,
                StatePrecision::F32,
                ModelKind::Sm72e25,
                ModelKind::Sm57,
            )
            .is_err());
            return;
        }
        let precision = StatePrecision::Int4CardsInt8Notes;
        check_pair_state_round_trips(|| paired(precision), load(ModelKind::Sm72e25, precision));
    }

    /// Snapshots, runtime caches, the binary snapshot, the delta store, undo
    /// and stashes of a pair; `single` (the release alone) never reads them.
    fn check_pair_state_round_trips(paired: impl Fn() -> RwkvRuntime, mut single: RwkvRuntime) {
        let answers = crate::rwkv::precision_tests::reviews(28);
        let queries: Vec<_> = answers[..7]
            .iter()
            .map(|answer| query_of(&later(answer), 2))
            .collect();
        let resident = |engine: &mut RwkvRuntime| {
            let r = engine
                .predict_retrievability_many_from_warm_up(queries.clone())
                .unwrap();
            let outputs = engine.predict_many(requests(engine, &queries)).unwrap();
            (bits(&r), output_bits(&outputs))
        };
        let mut pair = paired();
        pair.warm_up_reviews(answers[..14].to_vec(), false).unwrap();

        // In-memory snapshot plus runtime cache.
        let mut restored = paired();
        restored
            .restore_warm_up_snapshot(pair.warm_up_snapshot().unwrap())
            .unwrap();
        restored.restore_cache_state(&pair.cache_state()).unwrap();
        assert_eq!(resident(&mut restored), resident(&mut pair));
        assert!(single.restore_cache_state(&pair.cache_state()).is_err());
        assert!(single
            .restore_warm_up_snapshot(pair.warm_up_snapshot().unwrap())
            .is_err());
        assert!(pair.restore_cache_state(&single.cache_state()).is_err());
        assert!(pair.restore_state(&single.state_for_card(100)).is_err());

        // The binary snapshot holds the same paired states.
        let dir = tempfile::tempdir().unwrap();
        let snapshot_path = dir.path().join("snapshot.bin");
        std::fs::write(&snapshot_path, b"").unwrap();
        pair.append_warm_up_snapshot_binary(snapshot_path.clone())
            .unwrap();
        let (maps, global, cache) = parse_snapshot_body(&std::fs::read(&snapshot_path).unwrap());
        let memory = pair.warm_up_snapshot().unwrap();
        let mut expected = vec![
            memory.card_states,
            memory.note_states,
            memory.deck_states,
            memory.preset_states,
        ];
        for map in &mut expected {
            map.sort_by_key(|(identity, _)| *identity);
        }
        assert_eq!(maps, expected);
        assert_eq!(global, memory.global_state);
        assert_eq!(cache, pair.cache_state());

        // The delta store: a full segment, then a delta segment on top.
        let store = dir.path().join("state.sqlite3");
        let write = |engine: &mut RwkvRuntime, parent: Option<i64>, full: bool| {
            engine
                .write_warm_up_state_checkpoint(
                    store.clone(),
                    "generation",
                    parent,
                    1,
                    1,
                    "hash",
                    "replay",
                    b"",
                    b"",
                    b"",
                    full,
                    false,
                )
                .unwrap()
        };
        let first = write(&mut pair, None, true);
        pair.warm_up_reviews(answers[14..].to_vec(), false).unwrap();
        let second = write(&mut pair, Some(first), false);
        pair.finish_warm_up_state_checkpoints().unwrap();
        let mut from_store = paired();
        from_store
            .restore_warm_up_state_checkpoint(store.clone(), "generation", second)
            .unwrap();
        assert_eq!(resident(&mut from_store), resident(&mut pair));
        let mut from_first = paired();
        from_first
            .restore_warm_up_state_checkpoint(store.clone(), "generation", first)
            .unwrap();
        assert_eq!(resident(&mut from_first), resident(&mut restored));
        // A single-model runtime never reads a paired store.
        assert!(single
            .restore_warm_up_state_checkpoint(store.clone(), "generation", second)
            .is_err());

        // Undo: feature state plus the stream states before the answer.
        let answer = later(&answers[3]);
        let before = resident(&mut pair);
        let undo = pair.state_for_review(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
        );
        let states = pair.warm_up_state(&answer).unwrap();
        pair.warm_up_reviews(vec![answer.clone()], false).unwrap();
        assert_ne!(resident(&mut pair), before);
        pair.restore_state(&undo).unwrap();
        pair.restore_warm_up_state(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
            states,
        )
        .unwrap();
        assert_eq!(resident(&mut pair), before);

        // Stash in memory and on disk, then put the state back.
        pair.stash_warm_up_state().unwrap();
        pair.restore_stashed_warm_up_state().unwrap();
        assert_eq!(resident(&mut pair), before);
        let spill = dir.path().join("stash-spill.bin");
        pair.stash_warm_up_state_to_file(&spill).unwrap();
        assert!(spill.exists() && spill_path_for_instant(&spill).exists());
        pair.restore_stashed_warm_up_state().unwrap();
        assert!(!spill.exists() && !spill_path_for_instant(&spill).exists());
        assert_eq!(resident(&mut pair), before);
        pair.reset_warm_up_state();
        assert_ne!(resident(&mut pair), before);
    }

    fn big_path(model: ModelKind) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(match model {
            ModelKind::Legacy => "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin",
            ModelKind::Sm99 => "../qt/aqt/rwkv_inference/sm99/weights.f32.bin",
            _ => unreachable!("not a big model"),
        })
    }

    /// A big model as a plain engine of its build (for the instant-only big
    /// model (SM99) with its raw, untrained curve outputs).
    fn big_alone(precision: StatePrecision, model: ModelKind) -> RwkvRuntime {
        let path = big_path(model);
        if model.on_primary_engine() {
            RwkvRuntime::Primary(Box::new(
                RwkvRuntime::load_primary(path, 0.9, 36_500, precision, model).unwrap(),
            ))
        } else {
            RwkvRuntime::Alternate(Box::new(
                RwkvRuntime::load_alternate(path, 0.9, 36_500, precision, model).unwrap(),
            ))
        }
    }

    /// The Big pair: the show-time big model (SM91s2, `Legacy`) for the
    /// curve, the show-time instant-only big model (SM99) for RWKV-Instant.
    fn big_pair(precision: StatePrecision) -> RwkvRuntime {
        RwkvRuntime::load_paired(
            big_path(ModelKind::Legacy),
            big_path(ModelKind::Sm99),
            0.9,
            36_500,
            precision,
            ModelKind::Legacy,
            ModelKind::Sm99,
        )
        .unwrap()
    }

    /// The Big pair runs on the big engine of either build, the way the
    /// Small pair runs on the small engine: only Legacy beside SM99, never
    /// a big model beside a small one or SM99 as the release side.
    #[test]
    fn rwkv_big_pair_accepts_only_the_big_models() {
        assert_eq!(ModelKind::parse("sm99").unwrap(), ModelKind::Sm99);
        assert!(ModelKind::Sm99.instant_only() && ModelKind::Sm99.leak_free_inputs());
        assert!(!ModelKind::Sm99.small() && ModelKind::Sm99.card_layers().is_none());
        for (release_model, instant_model) in [
            (ModelKind::Sm99, ModelKind::Sm99),
            (ModelKind::Sm99, ModelKind::Legacy),
            (ModelKind::Legacy, ModelKind::Legacy),
            (ModelKind::Legacy, ModelKind::Sm98),
            (ModelKind::Sm72e25, ModelKind::Sm99),
            (ModelKind::Sm92, ModelKind::Sm99),
        ] {
            assert!(
                RwkvRuntime::load_paired(
                    big_path(ModelKind::Legacy),
                    big_path(ModelKind::Sm99),
                    0.9,
                    36_500,
                    StatePrecision::F32,
                    release_model,
                    instant_model,
                )
                .is_err(),
                "{release_model:?} + {instant_model:?}"
            );
        }
        // The big engine refuses small weights under a big key.
        assert!(RwkvRuntime::load_with_precision(
            model_path("sm98"),
            0.9,
            36_500,
            StatePrecision::F32,
            ModelKind::Sm99,
        )
        .is_err());
        let pair = big_pair(StatePrecision::F32);
        assert!(pair.is_paired() && pair.has_curve() && !pair.has_answer_time_head());
        assert_eq!(
            matches!(pair, RwkvRuntime::AlternatePaired(_)),
            super::super::SMALL_MODEL
        );
    }

    #[test]
    fn rwkv_big_pair_matches_each_model_alone() {
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let pair = big_pair(precision);
            let release = load(ModelKind::Legacy, precision);
            let instant = big_alone(precision, ModelKind::Sm99);
            assert!(!release.is_paired() && !instant.has_answer_time_head());
            check_pair_matches_models(pair, release, instant, format!("big {precision:?}"));
        }
    }

    /// The instant-only big model (SM99) alone (Big with RWKV-Instant and no
    /// RWKV-Curve) gives its instant outputs and never a curve output.
    #[test]
    fn rwkv_big_instant_only_runtime_serves_no_curve() {
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let alone = load(ModelKind::Sm99, precision);
            assert!(!alone.is_paired() && !alone.has_curve());
            assert_eq!(
                matches!(alone, RwkvRuntime::AlternateInstantOnly(_)),
                super::super::SMALL_MODEL
            );
            check_instant_only_alone(alone, big_alone(precision, ModelKind::Sm99));
        }
    }

    #[test]
    fn rwkv_big_pair_state_round_trips() {
        let precision = StatePrecision::Int4CardsInt8Notes;
        check_pair_state_round_trips(|| big_pair(precision), load(ModelKind::Legacy, precision));
    }

    /// A profile that switches between sizes, or between a pair, its release
    /// alone and its instant model alone, gets a different replay key (model
    /// key, instant model key and weights hash on the desktop), so its state
    /// is rebuilt from the review history. The runtime also refuses every
    /// saved runtime cache of another architecture or pairing (the desktop
    /// restores the cache first), and every undo state of another engine or
    /// pairing.
    #[test]
    fn rwkv_switching_sizes_never_reuses_another_layout_state() {
        let precision = StatePrecision::Int4CardsInt8Notes;
        let answers = crate::rwkv::precision_tests::reviews(14);
        // (name, small architecture, runtime)
        let mut layouts = vec![
            ("big pair", false, big_pair(precision)),
            ("big alone", false, load(ModelKind::Legacy, precision)),
            ("big instant alone", false, load(ModelKind::Sm99, precision)),
        ];
        if super::super::SMALL_MODEL {
            // The family release's Small and Tiny layouts (SM114).
            let family_pair = |release: (ModelKind, &str), instant: (ModelKind, &str)| {
                RwkvRuntime::load_paired(
                    model_path(release.1),
                    model_path(instant.1),
                    0.9,
                    36_500,
                    precision,
                    release.0,
                    instant.0,
                )
                .unwrap()
            };
            layouts.push((
                "small pair",
                true,
                family_pair((ModelKind::Sm92, "sm92"), (ModelKind::Sm98, "sm98")),
            ));
            layouts.push(("small alone", true, load(ModelKind::Sm92, precision)));
            layouts.push((
                "small instant alone",
                true,
                load(ModelKind::Sm98, precision),
            ));
            layouts.push((
                "tiny pair",
                true,
                family_pair((ModelKind::Sm106, "sm106"), (ModelKind::Sm101, "sm101")),
            ));
            layouts.push(("tiny alone", true, load(ModelKind::Sm106, precision)));
            layouts.push((
                "tiny instant alone",
                true,
                load(ModelKind::Sm101, precision),
            ));
        }
        for (_, _, engine) in &mut layouts {
            engine.warm_up_reviews(answers.clone(), false).unwrap();
        }
        let saved: Vec<_> = layouts
            .iter()
            .map(|(name, small, engine)| {
                (
                    *name,
                    *small,
                    engine.is_paired(),
                    engine.cache_state(),
                    engine.state_for_card(answers[0].card_id),
                )
            })
            .collect();
        for (name, small, engine) in &mut layouts {
            for (other, other_small, other_paired, cache, undo) in &saved {
                if *other == *name {
                    continue;
                }
                let label = format!("{other} into {name}");
                if *small != *other_small || engine.is_paired() != *other_paired {
                    assert!(
                        engine.restore_cache_state(cache).is_err(),
                        "{label}: runtime cache"
                    );
                }
                let same_kind = matches!(
                    (&*engine, undo),
                    (
                        RwkvRuntime::Primary(_) | RwkvRuntime::InstantOnly(_),
                        RwkvRuntimeState::Primary(_)
                    ) | (
                        RwkvRuntime::Alternate(_) | RwkvRuntime::AlternateInstantOnly(_),
                        RwkvRuntimeState::Alternate(_)
                    ) | (RwkvRuntime::Paired(_), RwkvRuntimeState::Paired(_))
                        | (
                            RwkvRuntime::AlternatePaired(_),
                            RwkvRuntimeState::AlternatePaired(_)
                        )
                );
                if !same_kind {
                    assert!(engine.restore_state(undo).is_err(), "{label}: undo");
                }
            }
        }
    }

    /// Memory of the Big pair: every card and note keeps one state of each
    /// big model (the pair header plus two big states), the stored state the
    /// desktop keeps per card and note.
    #[test]
    fn rwkv_big_pair_stores_two_big_states_per_card_and_note() {
        let answers = crate::rwkv::precision_tests::reviews(14);
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let sizes = |mut engine: RwkvRuntime| {
                engine.warm_up_reviews(answers.clone(), false).unwrap();
                let state = engine.warm_up_state(&answers[0]).unwrap();
                (state.card.unwrap().len(), state.note.unwrap().len())
            };
            let (pair_card, pair_note) = sizes(big_pair(precision));
            let (card, note) = sizes(load(ModelKind::Legacy, precision));
            assert_eq!(sizes(load(ModelKind::Sm99, precision)), (card, note));
            assert_eq!(pair_card, paired::paired_len(card, card));
            assert_eq!(pair_note, paired::paired_len(note, note));
            println!(
                "big pair {precision:?}: card {pair_card} B + note {pair_note} B = {} B \
                 (one big model: card {card} B + note {note} B = {} B)",
                pair_card + pair_note,
                card + note
            );
        }
    }
}
