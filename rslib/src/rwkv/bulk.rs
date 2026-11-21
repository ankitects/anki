// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Bulk sequence-mode historical replay.
//!
//! The per-review warm-up path runs the whole model one review at a time, so
//! a full replay is serial even though most of the math has no cross-review
//! dependency. This module processes a warm-up call layer-at-a-time over
//! per-scope streams instead: all pointwise work (norms, lerp mixes,
//! projections, loras, heads) runs in parallel across reviews, and only the
//! small per-stream recurrence sweep stays sequential, parallelized across
//! streams.
//!
//! Pointwise projection stages run through the blocked `Linear` kernel
//! (`Linear::apply_block_into`), which streams each weight column once per
//! block of rows while preserving the per-output-element operation sequence
//! of the per-review path exactly (bias init, ascending-column fused
//! accumulation, identical zero-column skip). Persisted answer states stay
//! bit-identical to per-review replay. Query rows use an algebraically
//! equivalent recurrence projection that avoids materializing their discarded
//! next state; its different reduction order can introduce tiny score deltas.
//!
//! Stream continuity across calls comes from `ReviewStateMaps`, exactly like
//! the per-review path, so the chunked calls issued by the desktop bridge
//! replay to the same states as one large call.

use std::collections::HashMap;
use std::sync::Mutex;

use super::*;

/// Rows per bulk replay chunk (the fork: 8,192). kuma3: a chunk holds the f32
/// states of every card and note it touches, so the big model uses a quarter
/// (a 203k-review replay on a PC peaked at 1.33 GB instead of 1.83 GB, 16.4 s
/// instead of 14.7 s). The small model's states are ~6x smaller and its
/// per-chunk work dominated at 2,048 rows: a 203k-review rebuild on a Pixel 8a
/// took 130 s, at 16,384 rows 50 s.
const BULK_CHUNK_ROWS: usize = if SMALL_MODEL { 16_384 } else { 2_048 };

#[derive(Clone, Copy)]
#[cfg_attr(not(test), allow(dead_code))]
enum QueryRecurrence {
    Exact,
    Fast,
    // The query reads the compacted state after the corresponding answer.
    FastAfter,
}

impl QueryRecurrence {
    fn fast(self) -> bool {
        matches!(self, Self::Fast | Self::FastAfter)
    }

    fn after(self) -> bool {
        matches!(self, Self::FastAfter)
    }
}

pub(super) fn warm_up_reviews_bulk(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    record_predictions: bool,
) -> io::Result<Vec<(usize, f32)>> {
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        record_predictions,
        BULK_CHUNK_ROWS,
        QueryRecurrence::Fast,
        None,
    )
}

/// Advance a simulation's private state without computing unused answer heads.
/// The feature prepass and stream recurrence preserve the recorded order.
pub(super) fn replay_simulation_background(
    inference: &mut RwkvInference,
    states: &mut ReviewStateMaps,
    inputs: &[ReviewInput],
) {
    if inputs.is_empty() {
        return;
    }
    let mut features = Vec::with_capacity(inputs.len() * CARD_FEATURES);
    for input in inputs {
        features.extend(inference.features.features_for(input));
        inference.features.store_review(input);
    }
    let model = inference.model.clone();
    let mut x = feature_mlp_rows(&model, &features, inputs.len());
    if SMALL_MODEL {
        run_interleaved(
            &model,
            inputs,
            states,
            &mut x,
            None,
            BULK_CHUNK_ROWS,
            QueryRecurrence::Exact,
        );
    } else {
        run_state_only_module_wavefront(&model, inputs, states, x, BULK_CHUNK_ROWS);
    }
}

pub(super) fn warm_up_reviews_post_bulk(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
) -> io::Result<Vec<(usize, f32)>> {
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        true,
        BULK_CHUNK_ROWS,
        QueryRecurrence::FastAfter,
        None,
    )
}

/// Replay actual answers, evaluating an independent query at each answer's
/// pre-answer or post-answer state. Validation precedes any state changes.
pub(super) fn warm_up_reviews_query_bulk(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    queries: Vec<ReviewInput>,
    after: bool,
) -> io::Result<Vec<(usize, f32)>> {
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
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "invalid replay queries",
        ));
    }
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        true,
        BULK_CHUNK_ROWS,
        if after {
            QueryRecurrence::FastAfter
        } else {
            QueryRecurrence::Fast
        },
        Some(&queries),
    )
}

#[cfg(test)]
pub(super) fn warm_up_reviews_post_bulk_chunked(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    chunk_rows: usize,
) -> io::Result<Vec<(usize, f32)>> {
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        true,
        chunk_rows.max(1),
        QueryRecurrence::FastAfter,
        None,
    )
}

/// Test hook: a small chunk size exercises cross-chunk stream continuity
/// without needing thousands of reviews.
#[cfg(test)]
pub(super) fn warm_up_reviews_bulk_chunked(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    record_predictions: bool,
    chunk_rows: usize,
) -> io::Result<Vec<(usize, f32)>> {
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        record_predictions,
        chunk_rows.max(1),
        QueryRecurrence::Exact,
        None,
    )
}

#[cfg(test)]
pub(super) fn warm_up_reviews_bulk_fast_query_chunked(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    record_predictions: bool,
    chunk_rows: usize,
) -> io::Result<Vec<(usize, f32)>> {
    warm_up_reviews_bulk_impl(
        inference,
        reviews,
        record_predictions,
        chunk_rows.max(1),
        QueryRecurrence::Fast,
        None,
    )
}

fn warm_up_reviews_bulk_impl(
    inference: &mut RwkvInference,
    reviews: Vec<ReviewInput>,
    record_predictions: bool,
    chunk_size: usize,
    query_recurrence: QueryRecurrence,
    custom_queries: Option<&[ReviewInput]>,
) -> io::Result<Vec<(usize, f32)>> {
    // Feature prepass, in review order. Query features are assigned before
    // answer features for each review so first-seen ID encodings keep the
    // same benchmark-compatible order as the per-review path.
    let mut original_indices = Vec::new();
    let mut inputs = Vec::new();
    let mut answer_features: Vec<f32> = Vec::new();
    let mut query_features: Vec<f32> = Vec::new();
    for (index, input) in reviews.into_iter().enumerate() {
        if input.ease.is_none() {
            continue;
        }
        if record_predictions && !query_recurrence.after() {
            let mut query_input =
                custom_queries.map_or_else(|| input.clone(), |queries| queries[index].clone());
            query_input.is_query = true;
            query_input.ease = None;
            query_input.duration_millis = None;
            query_features.extend(inference.features.features_for(&query_input));
        }
        answer_features.extend(inference.features.features_for(&input));
        inference.features.store_review(&input);
        if record_predictions && query_recurrence.after() {
            let mut query_input =
                custom_queries.map_or_else(|| input.clone(), |queries| queries[index].clone());
            query_input.is_query = true;
            query_input.ease = None;
            query_input.duration_millis = None;
            if custom_queries.is_none() {
                query_input.current_elapsed_days = Some(0);
                query_input.current_elapsed_seconds = Some(0);
            }
            query_features.extend(inference.features.features_for(&query_input));
        }
        original_indices.push(index);
        inputs.push(input);
    }
    let rows = inputs.len();
    if rows == 0 {
        return Ok(vec![]);
    }

    let model = inference.model.clone();

    // Trunk activations for every row: answer lane, plus a query lane that
    // reads the same pre-review states but never advances them.
    #[cfg(test)]
    let profile_started = rwkv_warmup_profile_start();
    let mut x = feature_mlp_rows(&model, &answer_features, rows);
    let mut x_query = record_predictions.then(|| feature_mlp_rows(&model, &query_features, rows));
    #[cfg(test)]
    rwkv_warmup_profile_record(RwkvWarmupProfileBucket::FeatureMlp, profile_started);

    if SMALL_MODEL {
        run_interleaved(
            &model,
            &inputs,
            &mut inference.warm_up_states,
            &mut x,
            x_query.as_deref_mut(),
            chunk_size,
            query_recurrence,
        );
    } else if record_predictions {
        for (module_id, module) in model.modules.iter().enumerate() {
            #[cfg(test)]
            let profile_started = rwkv_warmup_profile_start();
            run_module(
                module,
                module_id,
                &inputs,
                &mut inference.warm_up_states,
                &mut x,
                x_query.as_deref_mut(),
                chunk_size,
                query_recurrence,
            );
            #[cfg(test)]
            rwkv_warmup_profile_record(module_profile_bucket(module_id), profile_started);
        }
    } else {
        x = run_state_only_module_wavefront(
            &model,
            &inputs,
            &mut inference.warm_up_states,
            x,
            chunk_size,
        );
    }

    // Recall curves: the per-review path stores one curve per answered
    // review, keyed by card, so only each card's chronologically last row
    // survives.
    #[cfg(test)]
    let profile_started = rwkv_warmup_profile_start();
    let mut last_row_by_card = HashMap::new();
    for (row, input) in inputs.iter().enumerate() {
        last_row_by_card.insert(input.card_id, row);
    }
    #[cfg(test)]
    if let Some(captured) = &mut inference.captured_curves {
        *captured = (0..rows)
            .into_par_iter()
            .map(|index| {
                let prehead = model.prehead_norm.apply(row(&x, index));
                model.curve_head(&prehead)
            })
            .collect();
    }
    let curves: Vec<(i64, ReviewCurve)> = last_row_by_card
        .into_par_iter()
        .map(|(card_id, row_index)| {
            let prehead = model.prehead_norm.apply(row(&x, row_index));
            (card_id, model.curve_head(&prehead))
        })
        .collect();
    for (card_id, curve) in curves {
        inference.curves.insert(card_id, curve);
    }
    for input in &inputs {
        inference.warm_up_states.mark_dirty(input);
    }

    let Some(x_query) = x_query else {
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Heads, profile_started);
        return Ok(vec![]);
    };
    let retrievabilities: Vec<f32> = (0..rows)
        .into_par_iter()
        .map(|row_index| {
            let prehead = model.prehead_norm.apply(row(&x_query, row_index));
            model.retrievability_head(&prehead)
        })
        .collect();
    #[cfg(test)]
    rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Heads, profile_started);
    Ok(original_indices.into_iter().zip(retrievabilities).collect())
}

#[cfg(test)]
pub(super) fn module_profile_bucket(module_id: usize) -> RwkvWarmupProfileBucket {
    match module_id {
        0 => RwkvWarmupProfileBucket::ModuleCard,
        1 => RwkvWarmupProfileBucket::ModuleDeck,
        2 => RwkvWarmupProfileBucket::ModuleNote,
        3 => RwkvWarmupProfileBucket::ModulePreset,
        _ => RwkvWarmupProfileBucket::ModuleGlobal,
    }
}

/// Preserve a separate layer-zero value residual for each interleaved stream.
#[allow(clippy::too_many_arguments)]
fn run_interleaved(
    model: &SrsModel,
    inputs: &[ReviewInput],
    states: &mut ReviewStateMaps,
    x: &mut [f32],
    mut x_query: Option<&mut [f32]>,
    chunk_size: usize,
    query_recurrence: QueryRecurrence,
) {
    let rows = inputs.len();
    let plans: Vec<_> = model
        .modules
        .iter()
        .enumerate()
        .map(|(id, module)| {
            let mut plan = ModulePlan::build(id, inputs);
            plan.precision = module.precision;
            plan
        })
        .collect();
    let mut layers: Vec<Vec<Vec<LayerState>>> = plans
        .iter()
        .enumerate()
        .map(|(id, plan)| {
            plan.streams
                .iter()
                .map(|stream| {
                    layer_states(
                        stream
                            .key
                            .and_then(|key| take_stream_state(states, id, key)),
                        model.modules[id].layers.len(),
                    )
                })
                .collect()
        })
        .collect();
    let mut v0: [Vec<f32>; 5] = std::array::from_fn(|_| vec![0.0; rows * D_MODEL]);
    let mut v0_query: [Option<Vec<f32>>; 5] =
        std::array::from_fn(|_| x_query.as_ref().map(|_| vec![0.0; rows * D_MODEL]));
    for &(id, layer_id) in model.layer_plan.steps {
        run_layer_chunks(LayerChunks {
            layer: &model.modules[id].layers[layer_id],
            layer_id,
            plan: &plans[id],
            stream_layers: &mut layers[id],
            rows,
            chunk_size,
            x,
            v0: &mut v0[id],
            x_query: x_query.as_deref_mut(),
            v0_query: v0_query[id].as_deref_mut(),
            query_recurrence,
        });
        if layer_id + 1 == model.modules[id].layers.len() {
            for (stream, layers) in plans[id]
                .streams
                .iter()
                .zip(std::mem::take(&mut layers[id]))
            {
                if let Some(key) = stream.key {
                    put_stream_state(
                        states,
                        id,
                        key,
                        ModuleState {
                            layers,
                            packed: None,
                        }
                        .compact(model.modules[id].precision),
                    );
                }
            }
            v0[id] = Vec::new();
            v0_query[id] = None;
        }
    }
}

fn feature_mlp_rows(model: &SrsModel, features: &[f32], rows: usize) -> Vec<f32> {
    let mut out = vec![0.0; rows * D_MODEL];
    out.par_chunks_mut(D_MODEL)
        .enumerate()
        .for_each(|(row_index, out_row)| {
            let features = &features[row_index * CARD_FEATURES..(row_index + 1) * CARD_FEATURES];
            out_row.copy_from_slice(&model.feature_mlp(features));
        });
    out
}

fn row(buffer: &[f32], index: usize) -> &[f32] {
    &buffer[index * D_MODEL..(index + 1) * D_MODEL]
}

/// Runs state-only replay as a dependency-safe wavefront over review chunks
/// and state-scope modules. Node `(chunk, module)` depends only on the previous
/// module for the same chunk and the previous chunk for the same module, so all
/// nodes on one anti-diagonal can execute in parallel.
fn run_state_only_module_wavefront(
    model: &SrsModel,
    inputs: &[ReviewInput],
    states: &mut ReviewStateMaps,
    mut x: Vec<f32>,
    chunk_size: usize,
) -> Vec<f32> {
    debug_assert_eq!(model.modules.len(), 5);
    let chunk_size = chunk_size.max(1);
    let chunks: Vec<Mutex<&mut [f32]>> =
        x.chunks_mut(chunk_size * D_MODEL).map(Mutex::new).collect();
    let module_states: Vec<Mutex<ReviewStateMaps>> = (0..model.modules.len())
        .map(|module_id| Mutex::new(take_module_states(states, module_id)))
        .collect();

    for diagonal in 0..chunks.len() + model.modules.len() - 1 {
        (0..model.modules.len())
            .into_par_iter()
            .for_each(|module_id| {
                let Some(chunk_index) = diagonal.checked_sub(module_id) else {
                    return;
                };
                if chunk_index >= chunks.len() {
                    return;
                }

                let input_start = chunk_index * chunk_size;
                let input_end = (input_start + chunk_size).min(inputs.len());
                let mut module_states = module_states[module_id]
                    .lock()
                    .expect("RWKV module state lock poisoned");
                let mut chunk = chunks[chunk_index]
                    .lock()
                    .expect("RWKV replay chunk lock poisoned");
                #[cfg(test)]
                let profile_started = rwkv_warmup_profile_start();
                run_module(
                    &model.modules[module_id],
                    module_id,
                    &inputs[input_start..input_end],
                    &mut module_states,
                    &mut chunk,
                    None,
                    chunk_size,
                    QueryRecurrence::Exact,
                );
                #[cfg(test)]
                rwkv_warmup_profile_record(module_profile_bucket(module_id), profile_started);
            });
    }

    for (module_id, module_states) in module_states.into_iter().enumerate() {
        put_module_states(
            states,
            module_id,
            module_states
                .into_inner()
                .expect("RWKV module state lock poisoned"),
        );
    }

    drop(chunks);
    x
}

fn take_module_states(states: &mut ReviewStateMaps, module_id: usize) -> ReviewStateMaps {
    let mut module_states = ReviewStateMaps::default();
    match module_id {
        0 => module_states.card = std::mem::take(&mut states.card),
        1 => module_states.deck = std::mem::take(&mut states.deck),
        2 => module_states.note = std::mem::take(&mut states.note),
        3 => module_states.preset = std::mem::take(&mut states.preset),
        4 => module_states.global = states.global.take(),
        _ => unreachable!("unexpected RWKV module {module_id}"),
    }
    module_states
}

fn put_module_states(
    states: &mut ReviewStateMaps,
    module_id: usize,
    mut module_states: ReviewStateMaps,
) {
    match module_id {
        0 => states.card = std::mem::take(&mut module_states.card),
        1 => states.deck = std::mem::take(&mut module_states.deck),
        2 => states.note = std::mem::take(&mut module_states.note),
        3 => states.preset = std::mem::take(&mut module_states.preset),
        4 => states.global = module_states.global.take(),
        _ => unreachable!("unexpected RWKV module {module_id}"),
    }
}

/// The recurrent-state scope a module's streams are keyed by. `None` means
/// the review carries no id for this scope: it sees fresh state and nothing
/// is persisted, matching `ReviewStateMaps::{state_ref, store}`.
fn stream_key(module_id: usize, input: &ReviewInput) -> Option<i64> {
    match module_id {
        0 => Some(input.card_id),
        1 => input.deck_id,
        2 => input.note_id,
        3 => input.preset_id,
        _ => Some(0),
    }
}

fn take_stream_state(
    states: &mut ReviewStateMaps,
    module_id: usize,
    key: i64,
) -> Option<ModuleState> {
    match module_id {
        0 => states.card.take(&key),
        1 => states.deck.remove(&key),
        2 => states.note.take(&key),
        3 => states.preset.remove(&key),
        _ => states.global.take(),
    }
}

fn put_stream_state(states: &mut ReviewStateMaps, module_id: usize, key: i64, state: ModuleState) {
    match module_id {
        0 => {
            states.card.put(key, state);
        }
        1 => {
            states.deck.insert(key, state);
        }
        2 => {
            states.note.put(key, state);
        }
        3 => {
            states.preset.insert(key, state);
        }
        _ => states.global = Some(state),
    }
}

struct StreamPlan {
    key: Option<i64>,
    rows: Vec<u32>,
}

struct ModulePlan {
    precision: StatePrecision,
    streams: Vec<StreamPlan>,
    stream_of_row: Vec<u32>,
    prev_row: Vec<Option<u32>>,
}

impl ModulePlan {
    fn build(module_id: usize, inputs: &[ReviewInput]) -> Self {
        let mut streams: Vec<StreamPlan> = Vec::new();
        let mut stream_of_row = Vec::with_capacity(inputs.len());
        let mut prev_row = Vec::with_capacity(inputs.len());
        let mut stream_by_key: HashMap<i64, u32> = HashMap::new();
        for (row, input) in inputs.iter().enumerate() {
            match stream_key(module_id, input) {
                Some(key) => {
                    let stream_index = *stream_by_key.entry(key).or_insert_with(|| {
                        streams.push(StreamPlan {
                            key: Some(key),
                            rows: Vec::new(),
                        });
                        (streams.len() - 1) as u32
                    });
                    let stream = &mut streams[stream_index as usize];
                    prev_row.push(stream.rows.last().copied());
                    stream.rows.push(row as u32);
                    stream_of_row.push(stream_index);
                }
                None => {
                    streams.push(StreamPlan {
                        key: None,
                        rows: vec![row as u32],
                    });
                    prev_row.push(None);
                    stream_of_row.push((streams.len() - 1) as u32);
                }
            }
        }
        Self {
            precision: StatePrecision::F32,
            streams,
            stream_of_row,
            prev_row,
        }
    }
}

fn layer_states(initial: Option<ModuleState>, layer_count: usize) -> Vec<LayerState> {
    let mut layers = initial
        .map(|state| state.into_decoded().layers)
        .unwrap_or_default();
    layers.truncate(layer_count);
    while layers.len() < layer_count {
        layers.push(LayerState {
            time: None,
            channel_shift: None,
        });
    }
    layers
}

/// One module's streams over a run of rows: their decoded layer states and
/// the rows' value residuals.
struct ModuleWork {
    plan: ModulePlan,
    stream_layers: Vec<Vec<LayerState>>,
    v0: Vec<f32>,
    v0_query: Option<Vec<f32>>,
}

impl ModuleWork {
    fn take(
        module: &RwkvModule,
        module_id: usize,
        inputs: &[ReviewInput],
        states: &mut ReviewStateMaps,
        query: bool,
    ) -> Self {
        let rows = inputs.len();
        let mut plan = ModulePlan::build(module_id, inputs);
        plan.precision = module.precision;
        let layer_count = module.layers.len();
        let stream_layers = plan
            .streams
            .iter()
            .map(|stream| {
                let initial = stream
                    .key
                    .and_then(|key| take_stream_state(states, module_id, key));
                layer_states(initial, layer_count)
            })
            .collect();
        Self {
            plan,
            stream_layers,
            v0: vec![0.0; rows * D_MODEL],
            v0_query: query.then(|| vec![0.0; rows * D_MODEL]),
        }
    }

    fn run_layer(
        &mut self,
        layer: &RwkvLayer,
        layer_id: usize,
        x: &mut [f32],
        x_query: Option<&mut [f32]>,
        chunk_size: usize,
        query_recurrence: QueryRecurrence,
    ) {
        run_layer_chunks(LayerChunks {
            layer,
            layer_id,
            plan: &self.plan,
            stream_layers: &mut self.stream_layers,
            rows: self.plan.stream_of_row.len(),
            chunk_size,
            x,
            v0: &mut self.v0,
            x_query,
            v0_query: self.v0_query.as_deref_mut(),
            query_recurrence,
        });
    }

    fn put(self, module_id: usize, states: &mut ReviewStateMaps) {
        for (stream, layers) in self.plan.streams.iter().zip(self.stream_layers) {
            if let Some(key) = stream.key {
                put_stream_state(
                    states,
                    module_id,
                    key,
                    ModuleState {
                        layers,
                        packed: None,
                    }
                    .compact(self.plan.precision),
                );
            }
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn run_module(
    module: &RwkvModule,
    module_id: usize,
    inputs: &[ReviewInput],
    states: &mut ReviewStateMaps,
    x: &mut [f32],
    mut x_query: Option<&mut [f32]>,
    chunk_size: usize,
    query_recurrence: QueryRecurrence,
) {
    let mut work = ModuleWork::take(module, module_id, inputs, states, x_query.is_some());
    for (layer_id, layer) in module.layers.iter().enumerate() {
        work.run_layer(
            layer,
            layer_id,
            x,
            x_query.as_deref_mut(),
            chunk_size,
            query_recurrence,
        );
    }
    work.put(module_id, states);
}

struct LayerChunks<'a> {
    layer: &'a RwkvLayer,
    layer_id: usize,
    plan: &'a ModulePlan,
    stream_layers: &'a mut [Vec<LayerState>],
    rows: usize,
    chunk_size: usize,
    x: &'a mut [f32],
    v0: &'a mut [f32],
    x_query: Option<&'a mut [f32]>,
    v0_query: Option<&'a mut [f32]>,
    query_recurrence: QueryRecurrence,
}

fn run_layer_chunks(chunks: LayerChunks<'_>) {
    let LayerChunks {
        layer,
        layer_id,
        plan,
        stream_layers,
        rows,
        chunk_size,
        x,
        v0,
        mut x_query,
        mut v0_query,
        query_recurrence,
    } = chunks;

    let mut time_shifts = initial_time_shifts(stream_layers, layer_id);
    let mut chunk_start = 0;
    let mut current = Some(prepare_layer_time_stage(LayerTimeStageInput {
        layer,
        plan,
        time_shifts: &time_shifts,
        chunk_start,
        chunk_rows: chunk_size.min(rows),
        x_snapshot: &x[..chunk_size.min(rows) * D_MODEL],
        v0_snapshot: &v0[..chunk_size.min(rows) * D_MODEL],
        x_query_snapshot: x_query
            .as_deref()
            .map(|x_query| &x_query[..chunk_size.min(rows) * D_MODEL]),
        v0_query_snapshot: v0_query
            .as_deref()
            .map(|v0_query| &v0_query[..chunk_size.min(rows) * D_MODEL]),
        approximate_query_projections: query_recurrence.fast(),
        query_after: query_recurrence.after(),
    }));

    while chunk_start < rows {
        let chunk_end = (chunk_start + chunk_size).min(rows);
        let chunk_layout = ChunkStreamLayout::build(plan, chunk_start, chunk_end);
        let stage = current.take().expect("prepared layer stage");
        update_time_shifts_for_chunk(&mut time_shifts, &chunk_layout, &stage.x_norm);

        let next_chunk_start = chunk_end;
        if next_chunk_start < rows {
            let next_chunk_end = (next_chunk_start + chunk_size).min(rows);
            let next_chunk_rows = next_chunk_end - next_chunk_start;
            let next_time_shifts = time_shifts.clone();
            let x_next = x[next_chunk_start * D_MODEL..next_chunk_end * D_MODEL].to_vec();
            let v0_next = v0[next_chunk_start * D_MODEL..next_chunk_end * D_MODEL].to_vec();
            let x_query_next = x_query.as_deref().map(|x_query| {
                x_query[next_chunk_start * D_MODEL..next_chunk_end * D_MODEL].to_vec()
            });
            let v0_query_next = v0_query.as_deref().map(|v0_query| {
                v0_query[next_chunk_start * D_MODEL..next_chunk_end * D_MODEL].to_vec()
            });

            let (next, ()) = rayon::join(
                || {
                    prepare_layer_time_stage(LayerTimeStageInput {
                        layer,
                        plan,
                        time_shifts: &next_time_shifts,
                        chunk_start: next_chunk_start,
                        chunk_rows: next_chunk_rows,
                        x_snapshot: &x_next,
                        v0_snapshot: &v0_next,
                        x_query_snapshot: x_query_next.as_deref(),
                        v0_query_snapshot: v0_query_next.as_deref(),
                        approximate_query_projections: query_recurrence.fast(),
                        query_after: query_recurrence.after(),
                    })
                },
                || {
                    finish_layer_time_stage(LayerTimeStageOutput {
                        layer,
                        layer_id,
                        plan,
                        stream_layers,
                        chunk_layout: &chunk_layout,
                        stage,
                        x,
                        v0,
                        x_query: x_query.as_deref_mut(),
                        v0_query: v0_query.as_deref_mut(),
                        query_recurrence,
                    });
                },
            );
            current = Some(next);
        } else {
            finish_layer_time_stage(LayerTimeStageOutput {
                layer,
                layer_id,
                plan,
                stream_layers,
                chunk_layout: &chunk_layout,
                stage,
                x,
                v0,
                x_query: x_query.as_deref_mut(),
                v0_query: v0_query.as_deref_mut(),
                query_recurrence,
            });
        }

        chunk_start = chunk_end;
    }
}

struct ChunkStreamLayout<'a> {
    chunk_start: usize,
    chunk_rows: usize,
    rows_by_stream: Vec<&'a [u32]>,
    streams: Vec<usize>,
}

impl<'a> ChunkStreamLayout<'a> {
    fn build(plan: &'a ModulePlan, chunk_start: usize, chunk_end: usize) -> Self {
        let mut rows_by_stream: Vec<&[u32]> = vec![&[]; plan.streams.len()];
        let mut streams = Vec::new();
        for (stream_index, stream) in plan.streams.iter().enumerate() {
            let low = stream
                .rows
                .partition_point(|&row| (row as usize) < chunk_start);
            let high = stream
                .rows
                .partition_point(|&row| (row as usize) < chunk_end);
            if low < high {
                rows_by_stream[stream_index] = &stream.rows[low..high];
                streams.push(stream_index);
            }
        }
        Self {
            chunk_start,
            chunk_rows: chunk_end - chunk_start,
            rows_by_stream,
            streams,
        }
    }
}

struct LayerTimeStage {
    chunk_start: usize,
    chunk_rows: usize,
    x_norm: Vec<f32>,
    parts: BulkTimeMixParts,
    parts_query: Option<BulkTimeMixParts>,
}

struct LayerTimeStageInput<'a> {
    layer: &'a RwkvLayer,
    plan: &'a ModulePlan,
    time_shifts: &'a [Option<Vec<f32>>],
    chunk_start: usize,
    chunk_rows: usize,
    x_snapshot: &'a [f32],
    v0_snapshot: &'a [f32],
    x_query_snapshot: Option<&'a [f32]>,
    v0_query_snapshot: Option<&'a [f32]>,
    approximate_query_projections: bool,
    query_after: bool,
}

fn prepare_layer_time_stage(input: LayerTimeStageInput<'_>) -> LayerTimeStage {
    let LayerTimeStageInput {
        layer,
        plan,
        time_shifts,
        chunk_start,
        chunk_rows,
        x_snapshot,
        v0_snapshot,
        x_query_snapshot,
        v0_query_snapshot,
        approximate_query_projections,
        query_after,
    } = input;
    let mixer = &layer.time_mixer;

    let (x_norm, x_norm_query) = if let Some(x_query) = x_query_snapshot {
        let (answer, query) = rayon::join(
            || normed_rows_from(&mixer.layer_norm, x_snapshot, chunk_rows),
            || normed_rows_from(&mixer.layer_norm, x_query, chunk_rows),
        );
        (answer, Some(query))
    } else {
        (
            normed_rows_from(&mixer.layer_norm, x_snapshot, chunk_rows),
            None,
        )
    };

    let answer_parts = || {
        time_parts(
            mixer,
            plan,
            time_shifts,
            chunk_start,
            chunk_rows,
            &x_norm,
            &x_norm,
            v0_snapshot,
            false,
            false,
        )
    };
    let (parts, parts_query) = if let Some(x_norm_query) = x_norm_query.as_ref() {
        let (answer, query) = rayon::join(answer_parts, || {
            time_parts(
                mixer,
                plan,
                time_shifts,
                chunk_start,
                chunk_rows,
                x_norm_query,
                &x_norm,
                v0_query_snapshot.expect("query v0 buffer"),
                approximate_query_projections,
                query_after,
            )
        });
        (answer, Some(query))
    } else {
        (answer_parts(), None)
    };

    LayerTimeStage {
        chunk_start,
        chunk_rows,
        x_norm,
        parts,
        parts_query,
    }
}

struct LayerTimeStageOutput<'a> {
    layer: &'a RwkvLayer,
    layer_id: usize,
    plan: &'a ModulePlan,
    stream_layers: &'a mut [Vec<LayerState>],
    chunk_layout: &'a ChunkStreamLayout<'a>,
    stage: LayerTimeStage,
    x: &'a mut [f32],
    v0: &'a mut [f32],
    x_query: Option<&'a mut [f32]>,
    v0_query: Option<&'a mut [f32]>,
    query_recurrence: QueryRecurrence,
}

fn finish_layer_time_stage(output: LayerTimeStageOutput<'_>) {
    let LayerTimeStageOutput {
        layer,
        layer_id,
        plan,
        stream_layers,
        chunk_layout,
        stage,
        x,
        v0,
        mut x_query,
        v0_query,
        query_recurrence,
    } = output;
    let LayerTimeStage {
        chunk_start,
        chunk_rows,
        x_norm,
        parts,
        parts_query,
    } = stage;
    debug_assert_eq!(chunk_start, chunk_layout.chunk_start);
    debug_assert_eq!(chunk_rows, chunk_layout.chunk_rows);

    if layer_id == 0 {
        fill_v0(
            &mut v0[chunk_start * D_MODEL..(chunk_start + chunk_rows) * D_MODEL],
            &parts,
        );
        if let (Some(v0_query), Some(parts_query)) = (v0_query, &parts_query) {
            fill_v0(
                &mut v0_query[chunk_start * D_MODEL..(chunk_start + chunk_rows) * D_MODEL],
                parts_query,
            );
        }
    }

    let mixer = &layer.time_mixer;

    // Recurrence sweep: sequential within a stream, parallel across streams.
    // Query rows read the pre-review state; only answer rows advance it.
    let mut recurrence = vec![0.0; chunk_rows * D_MODEL];
    let mut recurrence_query = parts_query
        .as_ref()
        .map(|_| vec![0.0; chunk_rows * D_MODEL]);
    let sweep_outs: Vec<Option<(Vec<f32>, Vec<f32>)>> = stream_layers
        .par_iter_mut()
        .enumerate()
        .map(|(stream_index, layers)| {
            let stream_rows = chunk_layout.rows_by_stream[stream_index];
            if stream_rows.is_empty() {
                return None;
            }
            let layer_state = &mut layers[layer_id];
            let mut matrix = match layer_state.time.take() {
                Some(time) => time.matrix,
                None => vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE],
            };
            let mut packed_matrix = None;
            // Separate source/destination matrices let the recurrence kernel
            // vectorize, while swapping reuses both allocations per stream.
            let mut next_matrix = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
            let mut query_next_matrix =
                if parts_query.is_some() && matches!(query_recurrence, QueryRecurrence::Exact) {
                    vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE]
                } else {
                    Vec::new()
                };
            let mut outs = vec![0.0; stream_rows.len() * D_MODEL];
            let mut outs_query = if parts_query.is_some() {
                vec![0.0; stream_rows.len() * D_MODEL]
            } else {
                Vec::new()
            };
            for (position, &stream_row) in stream_rows.iter().enumerate() {
                let index = stream_row as usize - chunk_start;
                let out = &mut outs[position * D_MODEL..(position + 1) * D_MODEL];
                if let Some(parts_query) =
                    parts_query.as_ref().filter(|_| !query_recurrence.after())
                {
                    let out_query = &mut outs_query[position * D_MODEL..(position + 1) * D_MODEL];
                    match query_recurrence {
                        QueryRecurrence::Exact => single_timestep_into(
                            row(&parts_query.r, index),
                            row(&parts_query.k, index),
                            row(&parts_query.v, index),
                            row(&parts_query.w, index),
                            row(&parts_query.a, index),
                            row(&parts_query.k_deformed, index),
                            &matrix,
                            out_query,
                            &mut query_next_matrix,
                        ),
                        QueryRecurrence::Fast | QueryRecurrence::FastAfter => {
                            single_timestep_query_fast_into(
                                row(&parts_query.r, index),
                                row(&parts_query.k, index),
                                row(&parts_query.v, index),
                                row(&parts_query.w, index),
                                row(&parts_query.a, index),
                                row(&parts_query.k_deformed, index),
                                Some(&matrix),
                                out_query,
                            )
                        }
                    }
                }
                single_timestep_into(
                    row(&parts.r, index),
                    row(&parts.k, index),
                    row(&parts.v, index),
                    row(&parts.w, index),
                    row(&parts.a, index),
                    row(&parts.k_deformed, index),
                    &matrix,
                    out,
                    &mut next_matrix,
                );
                std::mem::swap(&mut matrix, &mut next_matrix);
                plan.precision.round_matrix(&mut matrix, &mut packed_matrix);
                if query_recurrence.after() {
                    if let Some(parts_query) = &parts_query {
                        let out_query =
                            &mut outs_query[position * D_MODEL..(position + 1) * D_MODEL];
                        single_timestep_query_fast_into(
                            row(&parts_query.r, index),
                            row(&parts_query.k, index),
                            row(&parts_query.v, index),
                            row(&parts_query.w, index),
                            row(&parts_query.a, index),
                            row(&parts_query.k_deformed, index),
                            plan.streams[stream_index].key.map(|_| matrix.as_slice()),
                            out_query,
                        );
                    }
                }
            }
            if plan.streams[stream_index].key.is_some() {
                let last = *stream_rows.last().unwrap() as usize - chunk_start;
                layer_state.time = Some(TimeState {
                    x_shift: row(&x_norm, last).to_vec(),
                    matrix,
                    packed_matrix,
                });
            }
            Some((outs, outs_query))
        })
        .collect();
    for &stream_index in &chunk_layout.streams {
        let Some((outs, outs_query)) = &sweep_outs[stream_index] else {
            continue;
        };
        for (position, &stream_row) in chunk_layout.rows_by_stream[stream_index].iter().enumerate()
        {
            let index = stream_row as usize - chunk_start;
            recurrence[index * D_MODEL..(index + 1) * D_MODEL]
                .copy_from_slice(&outs[position * D_MODEL..(position + 1) * D_MODEL]);
            if let Some(recurrence_query) = &mut recurrence_query {
                recurrence_query[index * D_MODEL..(index + 1) * D_MODEL]
                    .copy_from_slice(&outs_query[position * D_MODEL..(position + 1) * D_MODEL]);
            }
        }
    }

    // Time mixer, pointwise post-recurrence stage.
    let time_out = time_outputs(
        mixer,
        &parts,
        &recurrence,
        x,
        chunk_start,
        chunk_rows,
        false,
    );
    let time_out_query = parts_query.as_ref().map(|parts_query| {
        time_outputs(
            mixer,
            parts_query,
            recurrence_query.as_ref().expect("query recurrence"),
            x_query.as_deref().expect("query trunk"),
            chunk_start,
            chunk_rows,
            query_recurrence.fast(),
        )
    });

    // Channel mixer. Shift states must stay pre-chunk until both lanes have
    // resolved their shifts.
    let Some(cm) = &layer.channel_mixer else {
        x[chunk_start * D_MODEL..(chunk_start + chunk_rows) * D_MODEL].copy_from_slice(&time_out);
        if let (Some(x_query), Some(out)) = (x_query, time_out_query) {
            x_query[chunk_start * D_MODEL..(chunk_start + chunk_rows) * D_MODEL]
                .copy_from_slice(&out);
        }
        return;
    };
    let cm_norm = normed_rows_from(&cm.layer_norm, &time_out, chunk_rows);
    let cm_norm_query = time_out_query
        .as_ref()
        .map(|time_out_query| normed_rows_from(&cm.layer_norm, time_out_query, chunk_rows));

    channel_outputs(
        cm,
        layer_id,
        plan,
        stream_layers,
        chunk_start,
        chunk_rows,
        &cm_norm,
        &cm_norm,
        &time_out,
        x,
        false,
        false,
    );
    if let Some(cm_norm_query) = &cm_norm_query {
        channel_outputs(
            cm,
            layer_id,
            plan,
            stream_layers,
            chunk_start,
            chunk_rows,
            cm_norm_query,
            &cm_norm,
            time_out_query.as_ref().expect("query time out"),
            x_query.take().expect("query trunk"),
            query_recurrence.fast(),
            query_recurrence.after(),
        );
    }

    for &stream_index in &chunk_layout.streams {
        if plan.streams[stream_index].key.is_none() {
            continue;
        }
        let stream_rows = chunk_layout.rows_by_stream[stream_index];
        let last = *stream_rows.last().unwrap() as usize - chunk_start;
        stream_layers[stream_index][layer_id].channel_shift = Some(row(&cm_norm, last).to_vec());
    }
}

fn initial_time_shifts(
    stream_layers: &[Vec<LayerState>],
    layer_id: usize,
) -> Vec<Option<Vec<f32>>> {
    stream_layers
        .iter()
        .map(|layers| {
            layers[layer_id]
                .time
                .as_ref()
                .map(|time| time.x_shift.clone())
        })
        .collect()
}

fn update_time_shifts_for_chunk(
    time_shifts: &mut [Option<Vec<f32>>],
    chunk_layout: &ChunkStreamLayout<'_>,
    x_norm: &[f32],
) {
    for &stream_index in &chunk_layout.streams {
        let stream_rows = chunk_layout.rows_by_stream[stream_index];
        if stream_rows.is_empty() {
            continue;
        }
        let last = *stream_rows.last().unwrap() as usize - chunk_layout.chunk_start;
        time_shifts[stream_index] = Some(row(x_norm, last).to_vec());
    }
}

fn normed_rows(norm: &Norm, buffer: &[f32], chunk_start: usize, chunk_rows: usize) -> Vec<f32> {
    let mut out = vec![0.0; chunk_rows * D_MODEL];
    out.par_chunks_mut(D_MODEL)
        .enumerate()
        .for_each(|(index, out_row)| {
            norm.apply_into(row(buffer, chunk_start + index), out_row);
        });
    out
}

fn normed_rows_from(norm: &Norm, buffer: &[f32], chunk_rows: usize) -> Vec<f32> {
    normed_rows(norm, buffer, 0, chunk_rows)
}

/// Per-chunk struct-of-arrays buffers for the time mixer's pre-recurrence
/// parts. Each field holds `rows * D_MODEL` values, row-major, so the blocked
/// projection kernels can write rows contiguously.
struct BulkTimeMixParts {
    r: Vec<f32>,
    k: Vec<f32>,
    v: Vec<f32>,
    w: Vec<f32>,
    a: Vec<f32>,
    k_deformed: Vec<f32>,
    g: Vec<f32>,
    next_v0: Vec<f32>,
}

impl BulkTimeMixParts {
    fn zeroed(rows: usize) -> Self {
        Self {
            r: vec![0.0; rows * D_MODEL],
            k: vec![0.0; rows * D_MODEL],
            v: vec![0.0; rows * D_MODEL],
            w: vec![0.0; rows * D_MODEL],
            a: vec![0.0; rows * D_MODEL],
            k_deformed: vec![0.0; rows * D_MODEL],
            g: vec![0.0; rows * D_MODEL],
            next_v0: vec![0.0; rows * D_MODEL],
        }
    }
}

/// One `LINEAR_BLOCK_ROWS`-row block's mutable view over every
/// `BulkTimeMixParts` field.
struct PartsBlockMut<'a> {
    r: &'a mut [f32],
    k: &'a mut [f32],
    v: &'a mut [f32],
    w: &'a mut [f32],
    a: &'a mut [f32],
    k_deformed: &'a mut [f32],
    g: &'a mut [f32],
    next_v0: &'a mut [f32],
}

fn fill_v0(v0: &mut [f32], parts: &BulkTimeMixParts) {
    v0.copy_from_slice(&parts.next_v0);
}

/// Resolves the one-step shift value for a row: the previous same-stream row
/// in this chunk, the persisted stream state, or the row's own normed value
/// when the stream has no state yet.
fn resolve_shift<'a>(
    plan: &ModulePlan,
    row_index: usize,
    chunk_start: usize,
    previous_chunk_values: &'a [f32],
    own: &'a [f32],
    stream_state: impl FnOnce(usize) -> Option<&'a [f32]>,
) -> &'a [f32] {
    match plan.prev_row[row_index] {
        Some(previous) if (previous as usize) >= chunk_start => {
            row(previous_chunk_values, previous as usize - chunk_start)
        }
        _ => {
            let stream_index = plan.stream_of_row[row_index] as usize;
            stream_state(stream_index).unwrap_or(own)
        }
    }
}

/// Fresh streams mix against their own unrounded activation. Only a previous
/// answer's persisted shift is truncated, including answers in this chunk.
fn compact_shift<'a>(
    plan: &ModulePlan,
    row_index: usize,
    shift: &'a [f32],
    rounded: &'a mut [f32; D_MODEL],
) -> &'a [f32] {
    if plan.precision == StatePrecision::F32 || plan.prev_row[row_index].is_none() {
        // A stream may have state from an earlier outer call even with no
        // preceding row here. Those shifts were already packed on that call.
        return shift;
    }
    rounded.copy_from_slice(shift);
    plan.precision.round_shift(rounded);
    rounded
}

#[allow(clippy::too_many_arguments)]
fn time_parts(
    mixer: &TimeMixer,
    plan: &ModulePlan,
    time_shifts: &[Option<Vec<f32>>],
    chunk_start: usize,
    chunk_rows: usize,
    x_norm: &[f32],
    x_norm_answer: &[f32],
    v0: &[f32],
    approximate_projections: bool,
    query_after: bool,
) -> BulkTimeMixParts {
    let mut parts = BulkTimeMixParts::zeroed(chunk_rows);
    let block_size = LINEAR_BLOCK_ROWS * D_MODEL;
    parts
        .r
        .par_chunks_mut(block_size)
        .zip(parts.k.par_chunks_mut(block_size))
        .zip(parts.v.par_chunks_mut(block_size))
        .zip(parts.w.par_chunks_mut(block_size))
        .zip(parts.a.par_chunks_mut(block_size))
        .zip(parts.k_deformed.par_chunks_mut(block_size))
        .zip(parts.g.par_chunks_mut(block_size))
        .zip(parts.next_v0.par_chunks_mut(block_size))
        .enumerate()
        .for_each(
            |(block_index, (((((((r, k), v), w), a), k_deformed), g), next_v0))| {
                time_parts_block(
                    mixer,
                    plan,
                    time_shifts,
                    chunk_start,
                    block_index * LINEAR_BLOCK_ROWS,
                    x_norm,
                    x_norm_answer,
                    v0,
                    approximate_projections,
                    query_after,
                    PartsBlockMut {
                        r,
                        k,
                        v,
                        w,
                        a,
                        k_deformed,
                        g,
                        next_v0,
                    },
                );
            },
        );
    parts
}

/// Computes the time-mix parts for one block of rows. Every projection runs
/// through the blocked `Linear` kernel, which performs the identical
/// per-output-element operation sequence as the per-row path, so results stay
/// bit-identical; only the loop order over rows changes.
#[allow(clippy::too_many_arguments)]
fn time_parts_block(
    mixer: &TimeMixer,
    plan: &ModulePlan,
    time_shifts: &[Option<Vec<f32>>],
    chunk_start: usize,
    block_start: usize,
    x_norm: &[f32],
    x_norm_answer: &[f32],
    v0: &[f32],
    approximate_projections: bool,
    query_after: bool,
    block: PartsBlockMut<'_>,
) {
    let rows = block.r.len() / D_MODEL;
    debug_assert!(rows <= LINEAR_BLOCK_ROWS);

    // The shift source per row is fixed for the whole layer, so resolve it
    // once and reuse it for every lerp mix.
    let mut shifts: [&[f32]; LINEAR_BLOCK_ROWS] = [&[]; LINEAR_BLOCK_ROWS];
    let mut rounded_shifts = [[0.0; D_MODEL]; LINEAR_BLOCK_ROWS];
    for (index, (shift, rounded)) in shifts
        .iter_mut()
        .zip(&mut rounded_shifts)
        .enumerate()
        .take(rows)
    {
        let own = row(x_norm, block_start + index);
        if query_after {
            let row_index = chunk_start + block_start + index;
            let has_state = plan.streams[plan.stream_of_row[row_index] as usize]
                .key
                .is_some();
            *shift = if has_state {
                row(x_norm_answer, block_start + index)
            } else {
                own
            };
            if has_state && plan.precision != StatePrecision::F32 {
                rounded.copy_from_slice(shift);
                plan.precision.round_shift(rounded);
                *shift = rounded;
            }
        } else {
            *shift = resolve_shift(
                plan,
                chunk_start + block_start + index,
                chunk_start,
                x_norm_answer,
                own,
                |stream_index| time_shifts[stream_index].as_deref(),
            );
            *shift = compact_shift(plan, chunk_start + block_start + index, shift, rounded);
        }
    }
    let fill_mixed = |mix_id: usize, mixed: &mut [f32]| {
        for index in 0..rows {
            let own = row(x_norm, block_start + index);
            fill_time_mixed(
                &mixer.rkvdag_lerp,
                mix_id,
                own,
                shifts[index],
                &mut mixed[index * D_MODEL..(index + 1) * D_MODEL],
            );
        }
    };

    let mut mixed = [0.0f32; LINEAR_BLOCK_ROWS * D_MODEL];
    let mixed = &mut mixed[..rows * D_MODEL];

    fill_mixed(0, mixed);
    mixer
        .w_r
        .apply_block_approx_into(mixed, block.r, rows, approximate_projections);

    fill_mixed(1, mixed);
    mixer
        .w_k
        .apply_block_approx_into(mixed, block.k, rows, approximate_projections);

    fill_mixed(6, mixed);
    let mut k_scale = [0.0f32; LINEAR_BLOCK_ROWS * HEADS];
    let k_scale = &mut k_scale[..rows * HEADS];
    mixer.k_scale_linear.apply_block_into(mixed, k_scale, rows);
    sigmoid_in_place(k_scale);

    fill_mixed(7, mixed);
    let mut v_scale = [0.0f32; LINEAR_BLOCK_ROWS * HEADS];
    let v_scale = &mut v_scale[..rows * HEADS];
    mixer.v_scale_linear.apply_block_into(mixed, v_scale, rows);
    sigmoid_in_place(v_scale);

    fill_mixed(2, mixed);
    if mixer.layer_id == 0 {
        mixer
            .w_v
            .apply_block_approx_into(mixed, block.v, rows, approximate_projections);
        block.next_v0.copy_from_slice(block.v);
    } else {
        mixer.v_lora.apply_sigmoid_block_into(mixed, block.v, rows);
        let mut w_v = [0.0f32; LINEAR_BLOCK_ROWS * D_MODEL];
        let w_v = &mut w_v[..rows * D_MODEL];
        mixer
            .w_v
            .apply_block_approx_into(mixed, w_v, rows, approximate_projections);
        for index in 0..rows {
            let range = index * D_MODEL..(index + 1) * D_MODEL;
            let v0_row = row(v0, block_start + index);
            for ((value, &w_v_value), &v0_value) in block.v[range.clone()]
                .iter_mut()
                .zip(&w_v[range.clone()])
                .zip(v0_row)
            {
                *value = lerp(w_v_value, v0_value, *value);
            }
            block.next_v0[range].copy_from_slice(v0_row);
        }
    }

    fill_mixed(4, mixed);
    mixer.a_lora.apply_sigmoid_block_into(mixed, block.a, rows);

    fill_mixed(5, mixed);
    let mut g_hidden = [0.0f32; LINEAR_BLOCK_ROWS * 16];
    let g_hidden = &mut g_hidden[..rows * mixer.lora_a_g.output];
    mixer.lora_a_g.apply_block_into(mixed, g_hidden, rows);
    sigmoid_in_place(g_hidden);
    mixer.lora_b_g.apply_block_into(g_hidden, block.g, rows);

    fill_mixed(3, mixed);
    mixer.d_lora.apply_tanh_block_into(mixed, block.w, rows);
    for value in block.w.iter_mut() {
        let d = -0.5 - softplus(-*value);
        *value = (-d.exp()).exp();
    }

    for index in 0..rows {
        let range = index * D_MODEL..(index + 1) * D_MODEL;
        let k_row = &mut block.k[range.clone()];
        normalize_heads_in_place(k_row);
        for head in 0..HEADS {
            let scale = k_scale[index * HEADS + head];
            for i in 0..HEAD_SIZE {
                k_row[head * HEAD_SIZE + i] *= scale;
            }
        }

        let v_row = &mut block.v[range.clone()];
        normalize_heads_in_place(v_row);
        for head in 0..HEADS {
            let scale = v_scale[index * HEADS + head];
            for i in 0..HEAD_SIZE {
                v_row[head * HEAD_SIZE + i] *= scale;
            }
        }

        block.k_deformed[range.clone()].copy_from_slice(&block.k[range.clone()]);
        for channel in range {
            block.k[channel] *= block.a[channel];
        }
    }
}

fn time_outputs(
    mixer: &TimeMixer,
    parts: &BulkTimeMixParts,
    recurrence: &[f32],
    trunk: &[f32],
    chunk_start: usize,
    chunk_rows: usize,
    approximate_projections: bool,
) -> Vec<f32> {
    let mut out = vec![0.0; chunk_rows * D_MODEL];
    out.par_chunks_mut(LINEAR_BLOCK_ROWS * D_MODEL)
        .enumerate()
        .for_each(|(block_index, out_block)| {
            let block_start = block_index * LINEAR_BLOCK_ROWS;
            let rows = out_block.len() / D_MODEL;
            let mut gated = [0.0f32; LINEAR_BLOCK_ROWS * D_MODEL];
            let gated = &mut gated[..rows * D_MODEL];
            for index in 0..rows {
                let chunk_index = block_start + index;
                let gated_row = &mut gated[index * D_MODEL..(index + 1) * D_MODEL];
                mixer
                    .out_group_norm
                    .apply_into(row(recurrence, chunk_index), gated_row);

                let r_row = row(&parts.r, chunk_index);
                let k_row = row(&parts.k, chunk_index);
                let v_row = row(&parts.v, chunk_index);
                let g_row = row(&parts.g, chunk_index);
                for head in 0..HEADS {
                    let base = head * HEAD_SIZE;
                    let mut bonus_scale = 0.0;
                    for i in 0..HEAD_SIZE {
                        bonus_scale += r_row[base + i] * mixer.bonus[base + i] * k_row[base + i];
                    }
                    for i in 0..HEAD_SIZE {
                        let channel = base + i;
                        gated_row[channel] =
                            g_row[channel] * (gated_row[channel] + bonus_scale * v_row[channel]);
                    }
                }
            }
            mixer
                .w_o
                .apply_block_approx_into(gated, out_block, rows, approximate_projections);
            for index in 0..rows {
                let input = row(trunk, chunk_start + block_start + index);
                for channel in 0..D_MODEL {
                    out_block[index * D_MODEL + channel] += input[channel];
                }
            }
        });
    out
}

#[allow(clippy::too_many_arguments)]
fn channel_outputs(
    cm: &ChannelMixer,
    layer_id: usize,
    plan: &ModulePlan,
    stream_layers: &[Vec<LayerState>],
    chunk_start: usize,
    chunk_rows: usize,
    cm_norm: &[f32],
    cm_norm_answer: &[f32],
    time_out: &[f32],
    trunk: &mut [f32],
    approximate_projections: bool,
    query_after: bool,
) {
    trunk[chunk_start * D_MODEL..(chunk_start + chunk_rows) * D_MODEL]
        .par_chunks_mut(LINEAR_BLOCK_ROWS * D_MODEL)
        .enumerate()
        .for_each(|(block_index, trunk_block)| {
            let block_start = block_index * LINEAR_BLOCK_ROWS;
            let rows = trunk_block.len() / D_MODEL;
            let mut mixed = [0.0f32; LINEAR_BLOCK_ROWS * D_MODEL];
            let mixed = &mut mixed[..rows * D_MODEL];
            for index in 0..rows {
                let chunk_index = block_start + index;
                let own = row(cm_norm, chunk_index);
                let mut rounded_shift = [0.0; D_MODEL];
                let x_shift = if query_after {
                    let row_index = chunk_start + chunk_index;
                    let has_state = plan.streams[plan.stream_of_row[row_index] as usize]
                        .key
                        .is_some();
                    let shift = if has_state {
                        row(cm_norm_answer, chunk_index)
                    } else {
                        own
                    };
                    if has_state && plan.precision != StatePrecision::F32 {
                        rounded_shift.copy_from_slice(shift);
                        plan.precision.round_shift(&mut rounded_shift);
                        &rounded_shift
                    } else {
                        shift
                    }
                } else {
                    let shift = resolve_shift(
                        plan,
                        chunk_start + chunk_index,
                        chunk_start,
                        cm_norm_answer,
                        own,
                        |stream_index| {
                            stream_layers[stream_index][layer_id]
                                .channel_shift
                                .as_deref()
                        },
                    );
                    compact_shift(plan, chunk_start + chunk_index, shift, &mut rounded_shift)
                };
                let out = &mut mixed[index * D_MODEL..(index + 1) * D_MODEL];
                for channel in 0..D_MODEL {
                    out[channel] = lerp(own[channel], x_shift[channel], cm.lerp_k[channel]);
                }
            }

            let channel_dim = cm.w_k.output;
            debug_assert!(channel_dim <= 256);
            let mut k = [0.0f32; LINEAR_BLOCK_ROWS * 256];
            let k = &mut k[..rows * channel_dim];
            cm.w_k
                .apply_block_approx_into(mixed, k, rows, approximate_projections);
            for value in k.iter_mut() {
                *value = value.max(0.0).powi(2);
            }
            cm.w_v
                .apply_block_approx_into(k, trunk_block, rows, approximate_projections);
            for index in 0..rows {
                let input = row(time_out, block_start + index);
                for channel in 0..D_MODEL {
                    trunk_block[index * D_MODEL + channel] += input[channel];
                }
            }
        });
}

fn fill_time_mixed(
    lerp_values: &[f32],
    mix_id: usize,
    x: &[f32],
    x_shift: &[f32],
    mixed: &mut [f32],
) {
    debug_assert_eq!(mixed.len(), D_MODEL);
    let lerp_offset = mix_id * D_MODEL;
    for channel in 0..D_MODEL {
        mixed[channel] = lerp(
            x[channel],
            x_shift[channel],
            lerp_values[lerp_offset + channel],
        );
    }
}
