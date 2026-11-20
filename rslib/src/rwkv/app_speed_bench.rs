// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Manual speed test of the RWKV model sizes on a real collection, along
//! the code paths the desktop app runs while the user waits:
//!
//! - `build`: the state build after a model-size switch or without a usable
//!   cache ("Building RWKV state cache..." / "Recovering RWKV state...",
//!   `qt/aqt/rwkv_scheduler.py::_warm_up_reviewer_backend`): the native
//!   full-history preparation (`Collection::rwkv_historical_review_inputs`, the
//!   same request `qt/aqt/rwkv_history.py::full_review_inputs` sends), the
//!   runtime load, the state-only replay in the app's 16,384-review chunks
//!   (`_RUST_STATE_ONLY_WARMUP_CHUNK_SIZE`), the full recovery checkpoint eight
//!   days before the last review and the final delta checkpoint into a
//!   temporary state store, as `_RwkvStateCacheCheckpointWriter` writes them.
//! - `load`: the profile-open restore of a valid cache ("Loading RWKV state
//!   cache..."): the history fingerprint check against the stored identity, the
//!   weights' SHA-256 (part of the cache key), the runtime load and
//!   `restore_warm_up_state_checkpoint` of the store `build` wrote.
//! - `score`: retrievability of every card with a review, from the resident
//!   state, in the app's R-only batches of 2,048 (deck counts and queue scoring
//!   after a build or load).
//!
//! Not covered (needs a built app): the Python side (turning 226k native
//! rows into dataclasses, packing them, progress callbacks, Qt), the cache
//! metadata JSON, and the deck-list refresh after the dialog closes.
//!
//! ```sh
//! ANKI_SPEED_COLLECTION=/path/to/copy/collection.anki2 \
//! ANKI_SPEED_RUNTIMES=big-pair,small-pair ANKI_SPEED_OPS=build \
//!   just rwkv-app-speed-native
//! ```
//!
//! Environment:
//! - `ANKI_SPEED_COLLECTION` (required): a COPY of a collection. It is opened
//!   read-write, as the app opens it; never point it at a live profile.
//! - `ANKI_SPEED_RUNTIMES`: comma list of named runtimes (see `NAMED`) or
//!   custom specs `name=kind[@path][+kind[@path]]`, e.g.
//!   `tiny-one-new=sm101@D:/models/new/weights.f32.bin` or
//!   `x=sm72e25+sm101@D:/w.bin` (main model, then the RWKV-Instant model beside
//!   it). `kind` is a `ModelKind` key; a path replaces the bundled weights of
//!   that kind. Default `big-pair,small-pair`; `tiny-pair` = SM106 + SM101.
//! - `ANKI_SPEED_OPS`: comma list of `build`, `load`, `score`, `state` (default
//!   `build`). `load` and `score` reuse the store / state of a `build` earlier
//!   in the process, else the store a previous process left in the work folder,
//!   else they run an untimed build first. `state` reports the stored card and
//!   note state bytes (it serializes a snapshot, so keep it out of RAM runs).
//! - `ANKI_SPEED_ITERATIONS` (default 1): runs of each op in this process;
//!   iteration 0 of a fresh process is the cold run, later ones are warm.
//! - `ANKI_SPEED_PRECISION` (default `cards_int4_notes_int8`, the app's
//!   default), `ANKI_SPEED_CHUNK` (default 16384), `ANKI_SPEED_WORK_DIR`
//!   (default `<collection folder>/speed-work`), `ANKI_SPEED_OUT` (JSON lines
//!   appended per run).
//! - `ANKI_SPEED_DUMP_HISTORY`: write the native history response there
//!   (collection data; scratch folder only).
//! - `RAYON_NUM_THREADS`: leave unset to match the app (Rayon's default pool,
//!   one thread per logical CPU).

use std::fs;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;
use std::time::Instant;

use anki_proto::scheduler::RwkvHistoricalReviewFingerprintRequest;
use anki_proto::scheduler::RwkvHistoricalReviewIdentity;
use anki_proto::scheduler::RwkvHistoricalReviewInputsRequest;
use anki_proto::scheduler::RwkvHistoricalReviewInputsResponse;
use anki_proto::scheduler::RwkvHistoricalReviewMetadata;
use sha2::Digest;
use sha2::Sha256;

use super::*;
use crate::collection::Collection;
use crate::collection::CollectionBuilder;

/// `_RWKV_STATE_CACHE_CHECKPOINT_MAX_AGE_MILLIS`.
const CHECKPOINT_MAX_AGE_MILLIS: i64 = 8 * 86_400_000;
/// The app's R-only scoring batch.
const SCORE_BATCH: usize = 2_048;

/// `(name, main model, RWKV-Instant model beside it)` as
/// `qt/aqt/rwkv_model.py::runtime_models` picks them since the family wiring
/// (SM114, 2026-11-13): each size runs only its own models.
const NAMED: [(&str, &str, Option<&str>); 12] = [
    // Big: the show-time big model (SM91s2, "legacy") with the show-time
    // instant-only big model (SM99); the one-model choice / RWKV-Curve only;
    // RWKV-Instant alone.
    ("big-pair", "legacy", Some("sm99")),
    ("big-one", "legacy", None),
    ("big-instant", "sm99", None),
    // Small: the show-time retrain (SM92) with the Small Instant-Only model
    // (SM98); the one-model choice / RWKV-Curve only (SM92); RWKV-Instant
    // alone (SM98).
    ("small-pair", "sm92", Some("sm98")),
    ("small-one", "sm92", None),
    ("small-curve", "sm92", None),
    ("small-instant", "sm98", None),
    // Tiny: the Tiny two-head model (SM106) with the Tiny Instant-Only model
    // (SM101); the one-model choice / RWKV-Curve only (SM106); RWKV-Instant
    // alone (SM101).
    ("tiny-pair", "sm106", Some("sm101")),
    ("tiny-one", "sm106", None),
    ("tiny-curve", "sm106", None),
    ("tiny-instant", "sm101", None),
    // The default small model (SM63) alone, for comparison with the old Small.
    ("sm63-alone", "sm72e25", None),
];

#[derive(Clone, Debug)]
struct ModelSpec {
    kind: String,
    path: PathBuf,
}

#[derive(Clone, Debug)]
struct RuntimeSpec {
    name: String,
    main: ModelSpec,
    instant: Option<ModelSpec>,
}

fn bundled_weights(kind: &str) -> PathBuf {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../qt/aqt/rwkv_inference");
    match kind {
        "legacy" => root.join("RWKV_trained_on_5000_10000.bin"),
        "sm40" => root.join("sm40v2/weights.f32.bin"),
        other => root.join(other).join("weights.f32.bin"),
    }
}

fn model_spec(text: &str) -> ModelSpec {
    match text.split_once('@') {
        Some((kind, path)) => ModelSpec {
            kind: kind.to_string(),
            path: PathBuf::from(path),
        },
        None => ModelSpec {
            kind: text.to_string(),
            path: bundled_weights(text),
        },
    }
}

fn runtime_spec(text: &str) -> RuntimeSpec {
    if let Some((name, models)) = text.split_once('=') {
        let (main, instant) = match models.split_once('+') {
            Some((main, instant)) => (main, Some(instant)),
            None => (models, None),
        };
        return RuntimeSpec {
            name: name.to_string(),
            main: model_spec(main),
            instant: instant.map(model_spec),
        };
    }
    let (name, main, instant) = NAMED
        .iter()
        .find(|(name, _, _)| *name == text)
        .unwrap_or_else(|| panic!("unknown runtime {text}"));
    RuntimeSpec {
        name: name.to_string(),
        main: model_spec(main),
        instant: instant.map(model_spec),
    }
}

impl RuntimeSpec {
    fn load(&self, precision: StatePrecision) -> RwkvRuntime {
        let kind = |spec: &ModelSpec| ModelKind::parse(&spec.kind).unwrap();
        match &self.instant {
            None => RwkvRuntime::load_with_precision(
                self.main.path.clone(),
                0.9,
                36_500,
                precision,
                kind(&self.main),
            ),
            Some(instant) => RwkvRuntime::load_paired(
                self.main.path.clone(),
                instant.path.clone(),
                0.9,
                36_500,
                precision,
                kind(&self.main),
                kind(instant),
            ),
        }
        .unwrap_or_else(|err| panic!("{}: {err}", self.name))
    }

    fn weight_files(&self) -> Vec<&Path> {
        let mut files = vec![self.main.path.as_path()];
        if let Some(instant) = &self.instant {
            files.push(instant.path.as_path());
        }
        files
    }
}

/// Process counters through kernel32 (no extra crate).
#[cfg(windows)]
mod counters {
    #[repr(C)]
    #[derive(Default)]
    struct ProcessMemoryCounters {
        cb: u32,
        page_fault_count: u32,
        peak_working_set_size: usize,
        working_set_size: usize,
        quota_peak_paged_pool_usage: usize,
        quota_paged_pool_usage: usize,
        quota_peak_non_paged_pool_usage: usize,
        quota_non_paged_pool_usage: usize,
        pagefile_usage: usize,
        peak_pagefile_usage: usize,
    }

    #[repr(C)]
    #[derive(Default, Clone, Copy)]
    struct FileTime {
        low: u32,
        high: u32,
    }

    impl FileTime {
        fn seconds(self) -> f64 {
            ((u64::from(self.high) << 32) | u64::from(self.low)) as f64 / 1e7
        }
    }

    #[link(name = "kernel32")]
    extern "system" {
        fn GetCurrentProcess() -> isize;
        fn K32GetProcessMemoryInfo(
            process: isize,
            counters: *mut ProcessMemoryCounters,
            cb: u32,
        ) -> i32;
        fn GetProcessTimes(
            process: isize,
            creation: *mut FileTime,
            exit: *mut FileTime,
            kernel: *mut FileTime,
            user: *mut FileTime,
        ) -> i32;
        fn GetSystemTimes(idle: *mut FileTime, kernel: *mut FileTime, user: *mut FileTime) -> i32;
    }

    /// (peak working set, peak private bytes, current working set) in bytes.
    pub fn memory() -> (u64, u64, u64) {
        let mut counters = ProcessMemoryCounters {
            cb: std::mem::size_of::<ProcessMemoryCounters>() as u32,
            ..Default::default()
        };
        unsafe {
            K32GetProcessMemoryInfo(GetCurrentProcess(), &mut counters, counters.cb);
        }
        (
            counters.peak_working_set_size as u64,
            counters.peak_pagefile_usage as u64,
            counters.working_set_size as u64,
        )
    }

    /// This process's CPU seconds (user + kernel).
    pub fn process_cpu() -> f64 {
        let mut times = [FileTime::default(); 4];
        unsafe {
            let [creation, exit, kernel, user] = &mut times;
            GetProcessTimes(GetCurrentProcess(), creation, exit, kernel, user);
        }
        times[2].seconds() + times[3].seconds()
    }

    /// (busy, total) CPU seconds of the whole machine, summed over CPUs.
    pub fn system_cpu() -> (f64, f64) {
        let mut times = [FileTime::default(); 3];
        unsafe {
            let [idle, kernel, user] = &mut times;
            GetSystemTimes(idle, kernel, user);
        }
        // Kernel time includes idle time.
        let total = times[1].seconds() + times[2].seconds();
        (total - times[0].seconds(), total)
    }
}

#[cfg(not(windows))]
mod counters {
    pub fn memory() -> (u64, u64, u64) {
        (0, 0, 0)
    }
    pub fn process_cpu() -> f64 {
        0.0
    }
    pub fn system_cpu() -> (f64, f64) {
        (0.0, 0.0)
    }
}

/// Wall time, this process's CPU and the machine's CPU over one run.
struct Meter {
    started: Instant,
    process_cpu: f64,
    system: (f64, f64),
}

impl Meter {
    fn start() -> Self {
        Self {
            started: Instant::now(),
            process_cpu: counters::process_cpu(),
            system: counters::system_cpu(),
        }
    }

    fn json(&self) -> String {
        let wall = self.started.elapsed().as_secs_f64();
        let process_cpu = counters::process_cpu() - self.process_cpu;
        let (busy, total) = counters::system_cpu();
        let (busy, total) = (busy - self.system.0, total - self.system.1);
        let cpus = std::thread::available_parallelism().map_or(1, |n| n.get()) as f64;
        let pct = |seconds: f64| {
            if total > 0.0 {
                100.0 * seconds / total
            } else {
                0.0
            }
        };
        let (peak_ws, peak_private, ws) = counters::memory();
        format!(
            "\"wall_ms\":{:.1},\"process_cpu_s\":{process_cpu:.2},\"process_cores\":{:.2},\
             \"machine_busy_pct\":{:.1},\"other_busy_pct\":{:.1},\"cpus\":{cpus},\
             \"peak_working_set_mb\":{:.1},\"peak_private_mb\":{:.1},\"working_set_mb\":{:.1}",
            wall * 1000.0,
            if wall > 0.0 { process_cpu / wall } else { 0.0 },
            pct(busy),
            pct((busy - process_cpu).max(0.0)),
            peak_ws as f64 / 1048576.0,
            peak_private as f64 / 1048576.0,
            ws as f64 / 1048576.0,
        )
    }
}

fn ms(started: Instant) -> f64 {
    started.elapsed().as_secs_f64() * 1000.0
}

/// `_stable_preset_id` hashes add-on preset ids with BLAKE2b-64; rslib has no
/// BLAKE2, so this uses SHA-256. Only the stream keys' values differ, not
/// the work.
fn stable_preset_id(preset_id: &str) -> i64 {
    if let Ok(id) = preset_id.parse::<i64>() {
        return id;
    }
    let digest = Sha256::digest(preset_id.as_bytes());
    i64::from_be_bytes(digest[..8].try_into().unwrap()) & i64::MAX
}

/// `qt/aqt/rwkv_history.py::history_fingerprint_request` for this collection.
fn fingerprint_request(col: &Collection) -> RwkvHistoricalReviewFingerprintRequest {
    let mut stable_preset_ids = HashMap::new();
    if let Some(overlay) = col.get_config_optional::<serde_json::Value, _>("fsrsPresetOverlay") {
        for (key, id_field) in [
            ("presets", "id"),
            ("rules", "preset_id"),
            ("simulator_rules", "preset_id"),
        ] {
            for item in overlay
                .get(key)
                .and_then(|items| items.as_array())
                .into_iter()
                .flatten()
            {
                if let Some(id) = item.get(id_field).and_then(|id| id.as_str()) {
                    if !id.is_empty() {
                        stable_preset_ids.insert(id.to_string(), stable_preset_id(id));
                    }
                }
            }
        }
    }
    let first_review_uses_creation_by_config_id = col
        .storage
        .all_deck_config()
        .unwrap()
        .into_iter()
        // `_rwkv_review_first_review_elapsed_from_card_creation` is always true.
        .map(|config| (config.id.0, true))
        .collect();
    RwkvHistoricalReviewFingerprintRequest {
        ignored_review_ids: vec![],
        // The collection-wide setting overrides the presets' own flag.
        dynamic_preset_replay: col
            .get_config_optional::<bool, _>("rwkvDynamicPresetReplay")
            .unwrap_or(false),
        stable_preset_ids,
        first_review_uses_creation_by_config_id,
        expected_identity: None,
    }
}

fn history_request(col: &Collection) -> RwkvHistoricalReviewInputsRequest {
    RwkvHistoricalReviewInputsRequest {
        history: Some(fingerprint_request(col)),
        preserved_learning_start_cutoffs: HashMap::new(),
        first_review_uses_creation: None,
        recovery_checkpoint_max_age_millis: Some(CHECKPOINT_MAX_AGE_MILLIS),
    }
}

/// The row `full_review_inputs` builds and `_packed_review_input_row` packs.
fn replay_inputs(response: &RwkvHistoricalReviewInputsResponse) -> Vec<ReviewInput> {
    response
        .reviews
        .iter()
        .map(|row| ReviewInput {
            card_id: row.card_id,
            note_id: Some(row.note_id),
            deck_id: Some(row.deck_id),
            preset_id: Some(row.preset_id),
            is_query: false,
            ease: Some(row.ease as u8),
            duration_millis: Some(row.duration_millis),
            card_type: Some(row.card_type),
            day_offset: Some(row.day_offset),
            current_elapsed_days: Some(row.elapsed_days),
            current_elapsed_seconds: Some(row.elapsed_seconds),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        })
        .collect()
}

/// `_encode_int_map_binary`: u32 count, then sorted (i64 key, i64 value),
/// little endian.
fn int_map<V: Copy + Into<i64>>(values: &HashMap<i64, V>) -> Vec<u8> {
    let mut items: Vec<_> = values.iter().map(|(k, v)| (*k, (*v).into())).collect();
    items.sort_unstable();
    let mut out = Vec::with_capacity(4 + items.len() * 16);
    out.extend_from_slice(&(items.len() as u32).to_le_bytes());
    for (key, value) in items {
        out.extend_from_slice(&key.to_le_bytes());
        out.extend_from_slice(&value.to_le_bytes());
    }
    out
}

/// What `load` needs from a previous `build`, kept beside the store.
#[derive(Clone, Debug, PartialEq)]
struct StoreInfo {
    path: PathBuf,
    generation: String,
    head_segment_id: i64,
    identity: RwkvHistoricalReviewIdentity,
}

impl StoreInfo {
    fn meta_path(store: &Path) -> PathBuf {
        store.with_extension("speed.json")
    }

    fn save(&self) {
        let json = serde_json::json!({
            "generation": self.generation,
            "head_segment_id": self.head_segment_id,
            "last_review_id": self.identity.last_review_id,
            "review_count": self.identity.review_count,
            "history_hash": self.identity.history_hash,
        });
        fs::write(Self::meta_path(&self.path), json.to_string()).unwrap();
    }

    fn read(store: &Path) -> Option<Self> {
        let json: serde_json::Value =
            serde_json::from_slice(&fs::read(Self::meta_path(store)).ok()?).ok()?;
        store.exists().then_some(())?;
        Some(Self {
            path: store.to_path_buf(),
            generation: json["generation"].as_str()?.to_string(),
            head_segment_id: json["head_segment_id"].as_i64()?,
            identity: RwkvHistoricalReviewIdentity {
                last_review_id: json["last_review_id"].as_i64()?,
                review_count: json["review_count"].as_u64()?,
                history_hash: json["history_hash"].as_str()?.to_string(),
            },
        })
    }
}

struct Built {
    runtime: RwkvRuntime,
    response: RwkvHistoricalReviewInputsResponse,
    store: StoreInfo,
}

fn write_checkpoint(
    runtime: &mut RwkvRuntime,
    path: &Path,
    generation: &str,
    parent: Option<i64>,
    metadata: &RwkvHistoricalReviewMetadata,
    full: bool,
) -> i64 {
    let identity = metadata.identity.as_ref().unwrap();
    runtime
        .write_warm_up_state_checkpoint(
            path.to_path_buf(),
            generation,
            parent,
            identity.last_review_id,
            identity.review_count as i64,
            &identity.history_hash,
            "speed-test",
            &int_map(&metadata.previous_review_id_by_card),
            &int_map(&metadata.previous_interval_days_by_card),
            &int_map(&metadata.review_count_by_card),
            full,
            // The rebuild writes a temporary store, not durable.
            false,
        )
        .unwrap()
}

/// One state build as the app runs it; returns the runtime and a JSON
/// fragment of the phase times.
fn build(
    col: &mut Collection,
    spec: &RuntimeSpec,
    precision: StatePrecision,
    chunk: usize,
    store: &Path,
) -> (Built, String) {
    let temp = store.with_extension("tmp.sqlite3");
    for suffix in ["", "-wal", "-shm", "-journal"] {
        let _ = fs::remove_file(format!("{}{suffix}", temp.display()));
    }
    let started = Instant::now();
    let response = col
        .rwkv_historical_review_inputs(history_request(col))
        .unwrap();
    let history_ms = ms(started);
    if let Ok(path) = std::env::var("ANKI_SPEED_DUMP_HISTORY") {
        // The bytes the desktop receives, for the Python-side measurement
        // (`qt/tools/rwkv_speed_python_overhead.py`). Collection data: keep
        // it in the scratch folder, never commit it.
        fs::write(path, prost::Message::encode_to_vec(&response)).unwrap();
    }
    let phase = Instant::now();
    let reviews = replay_inputs(&response);
    let convert_ms = ms(phase);
    let phase = Instant::now();
    let mut runtime = spec.load(precision);
    let load_ms = ms(phase);

    let metadata = response.metadata.clone().unwrap();
    let checkpoint = response.recovery_checkpoint.clone();
    let checkpoint_count = checkpoint
        .as_ref()
        .and_then(|c| c.identity.as_ref())
        .map(|identity| identity.review_count as usize);
    let generation = format!("{:032x}", rand::random::<u128>());
    let mut endpoints: Vec<usize> = checkpoint_count.into_iter().collect();
    if !reviews.is_empty() {
        endpoints.push(reviews.len());
    }
    let mut head = None;
    let mut replay_ms = 0.0;
    let mut checkpoint_ms = 0.0;
    let mut processed = 0;
    let mut endpoint = 0;
    while processed < reviews.len() {
        let end = (processed + chunk)
            .min(reviews.len())
            .min(endpoints[endpoint]);
        let phase = Instant::now();
        runtime
            .warm_up_reviews(reviews[processed..end].to_vec(), false)
            .unwrap();
        replay_ms += ms(phase);
        processed = end;
        if processed == endpoints[endpoint] {
            let phase = Instant::now();
            let full = Some(processed) == checkpoint_count;
            let segment_metadata = if full {
                checkpoint.as_ref().unwrap()
            } else {
                &metadata
            };
            head = Some(write_checkpoint(
                &mut runtime,
                &temp,
                &generation,
                if full { None } else { head },
                segment_metadata,
                full,
            ));
            checkpoint_ms += ms(phase);
            endpoint += 1;
        }
    }
    let phase = Instant::now();
    runtime.finish_warm_up_state_checkpoints().unwrap();
    for suffix in ["-wal", "-shm"] {
        let _ = fs::remove_file(format!("{}{suffix}", store.display()));
    }
    fs::rename(&temp, store).unwrap();
    let finish_ms = ms(phase) + checkpoint_ms;
    let total_ms = ms(started);
    let info = StoreInfo {
        path: store.to_path_buf(),
        generation,
        head_segment_id: head.unwrap(),
        identity: metadata.identity.clone().unwrap(),
    };
    info.save();
    let store_bytes = fs::metadata(store).map_or(0, |m| m.len());
    let json = format!(
        "\"reviews\":{},\"total_ms\":{total_ms:.1},\"history_ms\":{history_ms:.1},\
         \"convert_ms\":{convert_ms:.1},\"runtime_load_ms\":{load_ms:.1},\
         \"replay_ms\":{replay_ms:.1},\"save_ms\":{finish_ms:.1},\
         \"replay_reviews_per_s\":{:.0},\"total_reviews_per_s\":{:.0},\
         \"checkpoint_review_count\":{},\"store_bytes\":{store_bytes}",
        reviews.len(),
        reviews.len() as f64 / (replay_ms / 1000.0),
        reviews.len() as f64 / (total_ms / 1000.0),
        checkpoint_count.unwrap_or(0),
    );
    (
        Built {
            runtime,
            response,
            store: info,
        },
        json,
    )
}

/// The profile-open restore of a valid cache.
fn load(
    col: &mut Collection,
    spec: &RuntimeSpec,
    precision: StatePrecision,
    store: &StoreInfo,
) -> (RwkvRuntime, String) {
    let started = Instant::now();
    let mut request = fingerprint_request(col);
    request.expected_identity = Some(store.identity.clone());
    let fingerprint = col.rwkv_historical_review_fingerprint(request).unwrap();
    assert!(
        fingerprint.history_is_valid,
        "stored history no longer matches"
    );
    let fingerprint_ms = ms(started);
    let phase = Instant::now();
    for file in spec.weight_files() {
        let digest = Sha256::digest(fs::read(file).unwrap());
        std::hint::black_box(digest);
    }
    let model_key_ms = ms(phase);
    let phase = Instant::now();
    let mut runtime = spec.load(precision);
    let runtime_load_ms = ms(phase);
    let phase = Instant::now();
    runtime
        .restore_warm_up_state_checkpoint(
            store.path.clone(),
            &store.generation,
            store.head_segment_id,
        )
        .unwrap();
    let restore_ms = ms(phase);
    let json = format!(
        "\"reviews\":{},\"total_ms\":{:.1},\"fingerprint_ms\":{fingerprint_ms:.1},\
         \"model_key_ms\":{model_key_ms:.1},\"runtime_load_ms\":{runtime_load_ms:.1},\
         \"restore_ms\":{restore_ms:.1},\"store_bytes\":{}",
        fingerprint.review_count,
        ms(started),
        fs::metadata(&store.path).map_or(0, |m| m.len()),
    );
    (runtime, json)
}

/// Query inputs for every card with a review, at the collection's current
/// day and time.
fn score_inputs(
    col: &mut Collection,
    response: &RwkvHistoricalReviewInputsResponse,
) -> Vec<ReviewInput> {
    let timing = col.timing_today().unwrap();
    let today = i64::from(timing.days_elapsed);
    let now = timing.now.0;
    let mut last = HashMap::new();
    for row in &response.reviews {
        last.insert(row.card_id, row);
    }
    let mut cards: Vec<_> = last.into_values().collect();
    cards.sort_unstable_by_key(|row| row.card_id);
    cards
        .into_iter()
        .map(|row| {
            let elapsed_days = (today - row.day_offset).max(0);
            ReviewInput {
                card_id: row.card_id,
                note_id: Some(row.note_id),
                deck_id: Some(row.deck_id),
                preset_id: Some(row.preset_id),
                is_query: true,
                ease: None,
                duration_millis: None,
                card_type: Some(2),
                day_offset: Some(today),
                current_elapsed_days: Some(elapsed_days),
                current_elapsed_seconds: Some((now - row.review_id / 1000).max(0)),
                target_retentions: [Some(0.9); 4],
                enforce_grade_order: true,
            }
        })
        .collect()
}

fn score(runtime: &mut RwkvRuntime, inputs: &[ReviewInput]) -> String {
    let started = Instant::now();
    let mut checksum = 0.0_f64;
    for batch in inputs.chunks(SCORE_BATCH) {
        let recall = runtime
            .predict_retrievability_many_from_warm_up(batch.to_vec())
            .unwrap();
        checksum += recall.iter().map(|&r| f64::from(r)).sum::<f64>();
    }
    format!(
        "\"cards\":{},\"total_ms\":{:.1},\"checksum\":{checksum:.4}",
        inputs.len(),
        ms(started)
    )
}

fn state_bytes(runtime: &RwkvRuntime) -> String {
    let snapshot = runtime.warm_up_snapshot().unwrap();
    let bytes = |states: &[(i64, Vec<u8>)]| states.iter().map(|(_, s)| s.len()).sum::<usize>();
    let (cards, notes) = (snapshot.card_states.len(), snapshot.note_states.len());
    let (card_bytes, note_bytes) = (bytes(&snapshot.card_states), bytes(&snapshot.note_states));
    format!(
        "\"cards\":{cards},\"notes\":{notes},\"card_state_bytes\":{card_bytes},\
         \"note_state_bytes\":{note_bytes},\"bytes_per_card\":{:.0},\"bytes_per_note\":{:.0},\
         \"deck_states\":{},\"preset_states\":{}",
        card_bytes as f64 / cards.max(1) as f64,
        note_bytes as f64 / notes.max(1) as f64,
        snapshot.deck_states.len(),
        snapshot.preset_states.len(),
    )
}

fn env_list(name: &str, default: &str) -> Vec<String> {
    std::env::var(name)
        .unwrap_or_else(|_| default.to_string())
        .split(',')
        .map(str::trim)
        .filter(|item| !item.is_empty())
        .map(str::to_string)
        .collect()
}

fn is_profile_path(path: &Path) -> bool {
    let normalized = path.to_string_lossy().replace('\\', "/").to_lowercase();
    [
        "/appdata/",
        "/library/application support/anki2/",
        "/.local/share/anki2/",
    ]
    .iter()
    .any(|part| normalized.contains(part))
}

#[test]
fn benchmark_rejects_standard_profile_paths() {
    for path in [
        r"C:\Users\user\AppData\Roaming\Anki2\Profile\collection.anki2",
        "/Users/user/Library/Application Support/Anki2/Profile/collection.anki2",
        "/home/user/.local/share/Anki2/Profile/collection.anki2",
    ] {
        assert!(is_profile_path(Path::new(path)), "{path}");
    }
    assert!(!is_profile_path(Path::new(
        "/private/tmp/anki-backup/collection.anki2"
    )));
}

#[test]
#[ignore]
fn rwkv_app_speed_benchmark() {
    let Ok(collection) = std::env::var("ANKI_SPEED_COLLECTION") else {
        println!("set ANKI_SPEED_COLLECTION to a collection copy");
        return;
    };
    let collection = fs::canonicalize(&collection).expect("collection copy does not exist");
    assert!(
        !is_profile_path(&collection),
        "point ANKI_SPEED_COLLECTION at a copy, never at a live profile"
    );
    let runtimes: Vec<_> = env_list("ANKI_SPEED_RUNTIMES", "big-pair,small-pair")
        .iter()
        .map(|text| runtime_spec(text))
        .collect();
    let ops = env_list("ANKI_SPEED_OPS", "build");
    let iterations = std::env::var("ANKI_SPEED_ITERATIONS")
        .ok()
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or(1)
        .max(1);
    let precision = StatePrecision::parse(
        &std::env::var("ANKI_SPEED_PRECISION").unwrap_or("cards_int4_notes_int8".into()),
    )
    .unwrap();
    let chunk = std::env::var("ANKI_SPEED_CHUNK")
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(16_384);
    let work_dir = std::env::var("ANKI_SPEED_WORK_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| collection.parent().unwrap().join("speed-work"));
    fs::create_dir_all(&work_dir).unwrap();
    let mut out = std::env::var("ANKI_SPEED_OUT").ok().map(|path| {
        fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .unwrap()
    });
    let label = std::env::var("ANKI_SPEED_LABEL").unwrap_or_default();
    let threads = std::env::var("RAYON_NUM_THREADS").unwrap_or_default();

    let meter = Meter::start();
    let mut col = CollectionBuilder::new(&collection).build().unwrap();
    println!("{{\"op\":\"open\",{}}}", meter.json());

    for spec in &runtimes {
        let store = work_dir.join(format!("{}.state-v12.sqlite3", spec.name));
        let mut resident: Option<(RwkvRuntime, Option<RwkvHistoricalReviewInputsResponse>)> = None;
        for op in &ops {
            for iteration in 0..iterations {
                let meter = Meter::start();
                let fragment = match op.as_str() {
                    "build" => {
                        // The app drops the previous runtime before the new one.
                        drop(resident.take());
                        let (built, json) = build(&mut col, spec, precision, chunk, &store);
                        resident = Some((built.runtime, Some(built.response)));
                        json
                    }
                    "load" => {
                        drop(resident.take());
                        let info = StoreInfo::read(&store).unwrap_or_else(|| {
                            build(&mut col, spec, precision, chunk, &store).0.store
                        });
                        let (runtime, json) = load(&mut col, spec, precision, &info);
                        resident = Some((runtime, None));
                        json
                    }
                    "score" | "state" => {
                        if resident.is_none() {
                            let (built, _) = build(&mut col, spec, precision, chunk, &store);
                            resident = Some((built.runtime, Some(built.response)));
                        }
                        let (runtime, response) = resident.as_mut().unwrap();
                        if response.is_none() {
                            *response = Some(
                                col.rwkv_historical_review_inputs(history_request(&col))
                                    .unwrap(),
                            );
                        }
                        if op == "score" {
                            let inputs = score_inputs(&mut col, response.as_ref().unwrap());
                            let meter = Meter::start();
                            let json = score(runtime, &inputs);
                            let line = format!(
                                "{{\"label\":\"{label}\",\"runtime\":\"{}\",\"op\":\"score\",\
                                 \"iteration\":{iteration},\"rayon_threads\":\"{threads}\",\
                                 {json},{}}}",
                                spec.name,
                                meter.json()
                            );
                            emit(&mut out, &line);
                            continue;
                        }
                        state_bytes(runtime)
                    }
                    other => panic!("unknown op {other}"),
                };
                let line = format!(
                    "{{\"label\":\"{label}\",\"runtime\":\"{}\",\"op\":\"{op}\",\
                     \"iteration\":{iteration},\"rayon_threads\":\"{threads}\",\
                     \"precision\":\"{precision:?}\",{fragment},{}}}",
                    spec.name,
                    meter.json()
                );
                emit(&mut out, &line);
            }
        }
    }
}

fn emit(out: &mut Option<fs::File>, line: &str) {
    println!("{line}");
    if let Some(file) = out {
        writeln!(file, "{line}").unwrap();
    }
}
