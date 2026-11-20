// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fs;
use std::io;
use std::mem;
use std::path::Path;
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::sync::Mutex;

use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
use answer_time_items::AnswerTimePrediction as AnswerTimeOutput;
use rayon::prelude::*;
use rusqlite::blob::Blob;
use rusqlite::params;
use rusqlite::Connection;
use rusqlite::OpenFlags;
use rusqlite::OptionalExtension;
use rusqlite::Transaction;
use rusqlite::MAIN_DB;

use crate::card::CardType;
use crate::rwkv::answer_time as answer_time_items;
use crate::rwkv::answer_time::set_answer_time_elapsed;
use crate::rwkv::answer_time::ANSWER_TIME_FORMAT_VERSION;
use crate::scheduler::rwkv::relative_overdueness;

#[path = "architecture.rs"]
mod architecture;
#[path = "bulk.rs"]
mod bulk;
use architecture::*;
#[path = "instant_forecast_queue.rs"]
mod instant_forecast_queue;
#[path = "instant_forecast_sampling.rs"]
mod instant_forecast_sampling;
#[path = "instant_workload_forecast.rs"]
mod instant_workload_forecast;
#[cfg(test)]
#[path = "model_bench.rs"]
mod model_bench;
#[path = "precision.rs"]
mod precision;
#[path = "workload_comparison.rs"]
mod workload_comparison;
#[path = "workload_forecast.rs"]
mod workload_forecast;
pub use precision::StatePrecision;
#[cfg(test)]
#[path = "collection_benchmark.rs"]
mod collection_benchmark;
#[cfg(test)]
#[path = "fused_packed.rs"]
mod fused_packed;
#[cfg(test)]
#[path = "history_ablation_bench.rs"]
mod history_ablation_bench;
#[cfg(target_os = "macos")]
#[path = "matmul.rs"]
mod matmul;
#[cfg(test)]
#[path = "packed_weights.rs"]
mod packed_weights;
#[cfg(test)]
#[path = "precision_tests.rs"]
mod precision_tests;
#[cfg(target_arch = "aarch64")]
#[path = "query_kernels.rs"]
mod query_kernels;
#[cfg(test)]
#[path = "query_math_bench.rs"]
mod query_math_bench;
#[cfg(test)]
#[path = "review_order_bench.rs"]
mod review_order_bench;
#[cfg(all(test, unix))]
#[path = "runtime_precision_bench.rs"]
mod runtime_precision_bench;
#[cfg(test)]
#[path = "state_scope_bench.rs"]
mod state_scope_bench;
#[cfg(test)]
#[path = "weight_precision_bench.rs"]
mod weight_precision_bench;

const CARD_FEATURES: usize = 92;
const HEADS: usize = 4;
const HEAD_SIZE: usize = D_MODEL / HEADS;
const ANSWER_EASES: [u8; 4] = [1, 2, 3, 4];
const S90_TARGET_RETENTION: f32 = 0.9;
const STATE_CACHE_STORE_SCHEMA_VERSION: i64 = 5;
const STATE_CACHE_STORE_PAGE_SIZE: i64 = 64 * 1024;
const STATE_CACHE_DELTA_MAGIC: &[u8] = b"ARWKVSTATEDELTA1\0";
const WARM_UP_STATE_SPILL_MAGIC: &[u8] = b"ARWKVSTASH1\0";
const STATE_CACHE_DELTA_CHUNK_BYTES: usize = 64 * 1024 * 1024;
/// Restores read state deltas through a buffer of this size. On a cold
/// 1.3 GB store, 8 KiB reads took 1.9 s, 1 MiB 0.67 s and 16 MiB 0.41 s.
#[cfg(not(test))]
const STATE_CACHE_READ_BUFFER_BYTES: usize = 16 * 1024 * 1024;
/// Small enough that tests refill and seek across chunk boundaries.
#[cfg(test)]
const STATE_CACHE_READ_BUFFER_BYTES: usize = 4 * 1024;

/// Rows per block in the bulk replay's blocked projection kernels. Bounds the
/// stack scratch used per block; larger blocks amortize weight-matrix streaming
/// over more rows.
const LINEAR_BLOCK_ROWS: usize = 32;
/// Upper bound on the recurrent state copied for workload-simulation targets
/// that run concurrently. A sampled simulation needs a few tens of MB per
/// target; one over a whole collection falls back to one target at a time.
const WORKLOAD_TARGET_STATE_BUDGET_BYTES: usize = 1024 * 1024 * 1024;

#[cfg(test)]
#[derive(Clone)]
struct RwkvScanCapturedStep {
    r: Vec<f32>,
    k: Vec<f32>,
    v: Vec<f32>,
    w: Vec<f32>,
    a: Vec<f32>,
    k_deformed: Vec<f32>,
}

#[cfg(test)]
static RWKV_SINGLE_TIMESTEP_PROFILE_ENABLED: std::sync::atomic::AtomicBool =
    std::sync::atomic::AtomicBool::new(false);
#[cfg(test)]
static RWKV_SINGLE_TIMESTEP_PROFILE_CALLS: std::sync::atomic::AtomicU64 =
    std::sync::atomic::AtomicU64::new(0);
#[cfg(test)]
static RWKV_SINGLE_TIMESTEP_PROFILE_NANOS: std::sync::atomic::AtomicU64 =
    std::sync::atomic::AtomicU64::new(0);

#[cfg(test)]
const RWKV_WARMUP_PROFILE_BUCKETS: usize = 16;

#[cfg(test)]
#[derive(Clone, Copy)]
enum RwkvWarmupProfileBucket {
    FeaturesFor = 0,
    FeaturesStore = 1,
    ModelReview = 2,
    FeatureMlp = 3,
    ModuleCard = 4,
    ModuleDeck = 5,
    ModuleNote = 6,
    ModulePreset = 7,
    ModuleGlobal = 8,
    Heads = 9,
    ModuleRun = 10,
    TimeMixer = 11,
    ChannelMixer = 12,
    Linear = 13,
    Norm = 14,
    SingleTimestep = 15,
}

#[cfg(test)]
static RWKV_WARMUP_PROFILE_ENABLED: std::sync::atomic::AtomicBool =
    std::sync::atomic::AtomicBool::new(false);
#[cfg(test)]
static RWKV_WARMUP_PROFILE_CALLS: [std::sync::atomic::AtomicU64; RWKV_WARMUP_PROFILE_BUCKETS] =
    [const { std::sync::atomic::AtomicU64::new(0) }; RWKV_WARMUP_PROFILE_BUCKETS];
#[cfg(test)]
static RWKV_WARMUP_PROFILE_NANOS: [std::sync::atomic::AtomicU64; RWKV_WARMUP_PROFILE_BUCKETS] =
    [const { std::sync::atomic::AtomicU64::new(0) }; RWKV_WARMUP_PROFILE_BUCKETS];

#[cfg(test)]
fn start_rwkv_warmup_profile() {
    for index in 0..RWKV_WARMUP_PROFILE_BUCKETS {
        RWKV_WARMUP_PROFILE_CALLS[index].store(0, std::sync::atomic::Ordering::Relaxed);
        RWKV_WARMUP_PROFILE_NANOS[index].store(0, std::sync::atomic::Ordering::Relaxed);
    }
    RWKV_WARMUP_PROFILE_ENABLED.store(true, std::sync::atomic::Ordering::Relaxed);
}

#[cfg(test)]
fn stop_rwkv_warmup_profile() -> Vec<(&'static str, u64, u64)> {
    RWKV_WARMUP_PROFILE_ENABLED.store(false, std::sync::atomic::Ordering::Relaxed);
    [
        ("features_for", RwkvWarmupProfileBucket::FeaturesFor),
        ("features_store", RwkvWarmupProfileBucket::FeaturesStore),
        ("model_review", RwkvWarmupProfileBucket::ModelReview),
        ("feature_mlp", RwkvWarmupProfileBucket::FeatureMlp),
        ("module_card", RwkvWarmupProfileBucket::ModuleCard),
        ("module_deck", RwkvWarmupProfileBucket::ModuleDeck),
        ("module_note", RwkvWarmupProfileBucket::ModuleNote),
        ("module_preset", RwkvWarmupProfileBucket::ModulePreset),
        ("module_global", RwkvWarmupProfileBucket::ModuleGlobal),
        ("heads", RwkvWarmupProfileBucket::Heads),
        ("module_run", RwkvWarmupProfileBucket::ModuleRun),
        ("time_mixer", RwkvWarmupProfileBucket::TimeMixer),
        ("channel_mixer", RwkvWarmupProfileBucket::ChannelMixer),
        ("linear", RwkvWarmupProfileBucket::Linear),
        ("norm", RwkvWarmupProfileBucket::Norm),
        ("single_timestep", RwkvWarmupProfileBucket::SingleTimestep),
    ]
    .into_iter()
    .map(|(name, bucket)| {
        let index = bucket as usize;
        (
            name,
            RWKV_WARMUP_PROFILE_CALLS[index].load(std::sync::atomic::Ordering::Relaxed),
            RWKV_WARMUP_PROFILE_NANOS[index].load(std::sync::atomic::Ordering::Relaxed),
        )
    })
    .collect()
}

#[cfg(test)]
fn rwkv_warmup_profile_start() -> Option<std::time::Instant> {
    RWKV_WARMUP_PROFILE_ENABLED
        .load(std::sync::atomic::Ordering::Relaxed)
        .then(std::time::Instant::now)
}

#[cfg(test)]
fn rwkv_warmup_profile_record(
    bucket: RwkvWarmupProfileBucket,
    started: Option<std::time::Instant>,
) {
    if let Some(started) = started {
        let index = bucket as usize;
        RWKV_WARMUP_PROFILE_CALLS[index].fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        RWKV_WARMUP_PROFILE_NANOS[index].fetch_add(
            started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64,
            std::sync::atomic::Ordering::Relaxed,
        );
    }
}

#[cfg(test)]
fn start_rwkv_single_timestep_profile() {
    RWKV_SINGLE_TIMESTEP_PROFILE_CALLS.store(0, std::sync::atomic::Ordering::Relaxed);
    RWKV_SINGLE_TIMESTEP_PROFILE_NANOS.store(0, std::sync::atomic::Ordering::Relaxed);
    RWKV_SINGLE_TIMESTEP_PROFILE_ENABLED.store(true, std::sync::atomic::Ordering::Relaxed);
}

#[cfg(test)]
fn stop_rwkv_single_timestep_profile() -> (u64, u64) {
    RWKV_SINGLE_TIMESTEP_PROFILE_ENABLED.store(false, std::sync::atomic::Ordering::Relaxed);
    (
        RWKV_SINGLE_TIMESTEP_PROFILE_CALLS.load(std::sync::atomic::Ordering::Relaxed),
        RWKV_SINGLE_TIMESTEP_PROFILE_NANOS.load(std::sync::atomic::Ordering::Relaxed),
    )
}

#[cfg(test)]
struct RwkvScanCaptureState {
    module_id: usize,
    layer_id: usize,
    steps: Vec<RwkvScanCapturedStep>,
}

#[cfg(test)]
std::thread_local! {
    static RWKV_SCAN_CAPTURE: std::cell::RefCell<Option<RwkvScanCaptureState>> =
        const { std::cell::RefCell::new(None) };
}

#[cfg(test)]
fn start_rwkv_scan_capture(module_id: usize, layer_id: usize) {
    RWKV_SCAN_CAPTURE.with(|capture| {
        *capture.borrow_mut() = Some(RwkvScanCaptureState {
            module_id,
            layer_id,
            steps: Vec::new(),
        });
    });
}

#[cfg(test)]
fn rwkv_scan_capture_active() -> bool {
    RWKV_SCAN_CAPTURE.with(|capture| capture.borrow().is_some())
}

#[cfg(test)]
fn take_rwkv_scan_capture() -> Vec<RwkvScanCapturedStep> {
    RWKV_SCAN_CAPTURE.with(|capture| {
        capture
            .borrow_mut()
            .take()
            .map(|capture| capture.steps)
            .unwrap_or_default()
    })
}

#[cfg(test)]
#[allow(clippy::too_many_arguments)]
fn record_rwkv_scan_step(
    module_id: usize,
    layer_id: usize,
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
) {
    RWKV_SCAN_CAPTURE.with(|capture| {
        let mut capture = capture.borrow_mut();
        let Some(capture) = capture.as_mut() else {
            return;
        };
        if capture.module_id != module_id || capture.layer_id != layer_id {
            return;
        }
        capture.steps.push(RwkvScanCapturedStep {
            r: r.to_vec(),
            k: k.to_vec(),
            v: v.to_vec(),
            w: w.to_vec(),
            a: a.to_vec(),
            k_deformed: k_deformed.to_vec(),
        });
    });
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
const MAX_CHANNEL_MIXER_DIM: usize = 256;
#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
const MAX_LORA_RANK: usize = 16;
#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
const RETRIEVABILITY_GEMM_BATCH_SIZE: usize = 128;
const ID_PLACEHOLDER: i64 = 314_159_265_358_979_323;
/// Inputs `note_id_is_nan`, `deck_id_is_nan` and `preset_id_is_nan`.
const MISSING_ID_FLAG_FEATURES: std::ops::Range<usize> = 13..16;
const ID_SPLIT: u64 = 4;
const TORCH_ID_RNG_STATE_LEN: usize = 624;
const TORCH_ID_RNG_INITIAL_INDEX: usize = 4;
const TORCH_ID_RNG_INITIAL_STATE: [u32; TORCH_ID_RNG_STATE_LEN] = [
    1682285388, 1851481669, 966820414, 1804407443, 3932621889, 3075156175, 1736360318, 934395819,
    4040256764, 1428315994, 3735821200, 3330369387, 3669296551, 4124277305, 1875917743, 3028165727,
    2848364260, 455193708, 1084726271, 4219366876, 4052358395, 3170678824, 468221764, 161941628,
    2640816207, 267628614, 4070986878, 1225344559, 697494497, 1246641310, 1864086393, 2343787891,
    956379933, 132227689, 1804503562, 801719826, 2866035087, 3697914572, 3618475839, 3714856595,
    3045796150, 2653947806, 695225985, 3299305861, 2299901543, 282374836, 2677848525, 308337505,
    1649403860, 3241582120, 1641323885, 211358346, 1648589179, 3479631618, 11568893, 2867282711,
    4042451176, 611732436, 3506334995, 3562753239, 1234760878, 2196749548, 3668376444, 2428528228,
    631426791, 4017895429, 3026146201, 4191008964, 1932380089, 3380014424, 3547882387, 3952077360,
    386896944, 3997977197, 1529608344, 4132999748, 2534739147, 4182207012, 1114211877, 2611407325,
    442230326, 2804113464, 4095607840, 2203942301, 3208806807, 4217725735, 851085756, 3111179814,
    2893561443, 1737762208, 3364884061, 3768266261, 1951940754, 1219713113, 661792618, 2429297141,
    2854706310, 2555881125, 1720186105, 2231784162, 1816231541, 2459472524, 2303395945, 2802874606,
    164142194, 3722278385, 965390944, 1789460144, 2277056390, 2785283897, 557026319, 1822107998,
    2156805627, 1809760430, 751565145, 1320417968, 760565420, 3675458714, 266229863, 3071308558,
    2588875244, 520697952, 404211066, 3757472951, 1592302302, 1301622844, 283875151, 2264576214,
    1513690933, 3186056547, 503942925, 3452110716, 4068977508, 1386120901, 35467672, 3736425491,
    3479928582, 4057588406, 1156892529, 2666030005, 1947426597, 1513426209, 2298519457, 951648,
    2662765076, 1819076585, 991609795, 3445620136, 4263402744, 683674655, 125305024, 400363870,
    4175058246, 345274265, 1393354662, 4168368724, 1992205233, 333760424, 430638818, 2038831240,
    2393249574, 2277698148, 3834699795, 854369076, 1949138442, 879235983, 2687356990, 74489205,
    675756224, 1108887942, 3850167999, 3202550759, 346927610, 1219004248, 1933710611, 4232192948,
    127380890, 3413854308, 4259402585, 1947897797, 2281478841, 1250526173, 3584776535, 3962965149,
    2436415337, 3700599595, 2305091483, 441956968, 98602741, 1004357398, 2171103081, 3555585131,
    25494344, 474746862, 2168816300, 1445677778, 2762834911, 167135277, 1609410667, 342499195,
    2658948465, 96705736, 307223780, 2817802860, 1095652744, 1458155099, 2129035584, 3380124286,
    4277476542, 3149780470, 201762377, 3878283276, 2943856629, 1484419446, 536631262, 3610340050,
    2021510328, 405479479, 126321448, 112807861, 206360937, 2329863092, 2176182667, 761094335,
    3055987424, 3047673301, 1636351146, 730123282, 1603106892, 3241403281, 64478013, 2355203790,
    3571782552, 1977824032, 3103684787, 1440206087, 3529256035, 4029036380, 1727403531, 2250763223,
    2609572110, 301200603, 2499812360, 1079194345, 1873534699, 592458645, 3367330921, 2157823439,
    2377315769, 2124247689, 3501423240, 44290279, 2658883840, 3187367635, 2223108434, 621774141,
    2862251779, 58506969, 1447924885, 3417741267, 3295650570, 2061828420, 656716143, 722185440,
    2516683235, 2575345773, 2104327887, 3735805058, 1317382073, 297471524, 1841093725, 3008896502,
    106767431, 125566800, 2298510351, 1201930425, 907367885, 3447227635, 647146338, 926864998,
    673001384, 2537138065, 1291689589, 1065989187, 640479966, 2169443668, 1796563022, 3530758358,
    566177121, 1707919249, 1474913925, 3765619977, 3740101935, 3130400739, 2400651968, 1474401929,
    526695285, 943720479, 874038331, 330689749, 3219805999, 3116943707, 2803955215, 2165155639,
    4244261781, 2888630181, 252759016, 3783988093, 1530768853, 2944465398, 303528941, 3002511155,
    2323759006, 3019190001, 2122243812, 1121230145, 1730608278, 741152631, 1508537030, 3728973132,
    3150372859, 1696219691, 580153578, 2686308765, 1065154441, 582354200, 1629450702, 3804950383,
    1377430830, 3231855250, 716684766, 2347913152, 3723768936, 3995049093, 1640930380, 2518706901,
    3418463630, 541497465, 3219798303, 1706475941, 306647767, 1641899345, 1570832171, 1052204431,
    3913625992, 215023677, 1412164449, 110620318, 1060606607, 702131657, 1847887127, 209232170,
    3730056702, 1139625814, 1887242045, 3850643319, 3750397920, 2071715462, 3479548451, 1555884857,
    4190680751, 1832210452, 2962479459, 3822065681, 4014873921, 2886096746, 2233784512, 2238930674,
    3655246617, 927454496, 148905975, 1631004036, 96233864, 1481847180, 2120974892, 2577516900,
    1638964877, 1481137923, 1414769434, 3161093534, 112755530, 1764064957, 4190363343, 3887127551,
    1834924570, 83845051, 1194548396, 4079088278, 2045362678, 1769415013, 4005488543, 3853058044,
    3236458945, 2957047901, 1695491053, 3109347023, 1966942432, 1450132000, 2200494752, 608196473,
    3106709220, 4028691397, 1194244113, 3728413990, 3069222360, 3780538931, 2072954337, 3718173202,
    4049735703, 309723989, 3035905309, 1497948243, 1014496292, 3798948403, 2803094444, 342979147,
    1189029508, 1965636522, 3735884288, 851551988, 763723908, 4250649925, 1551115212, 2057541610,
    3495494782, 637069736, 2304408148, 815858117, 2236554473, 1483594123, 1278501755, 2394803515,
    1955783100, 1792990840, 2122870956, 530734808, 3030700278, 723763396, 2270626039, 2629780941,
    2984465025, 3688570684, 2776316291, 900046415, 2828563145, 1683929076, 3635512397, 2298110775,
    218175329, 1069560764, 2297081356, 2401672963, 1007365472, 421703457, 3609147592, 3680957553,
    2021586893, 3380432468, 3986237638, 1178822340, 3062404559, 1622982873, 3690602096, 2439524694,
    3587851041, 2408352887, 2166555969, 3451949389, 1782985112, 1721002099, 2722187334, 3644256530,
    3166709237, 666319864, 3937101570, 2322768459, 2640925986, 3091708170, 176155768, 2070897063,
    31962327, 1980355715, 1579825601, 2980116859, 1296346288, 3253021476, 1566345931, 1333641409,
    1466299323, 3535059214, 3755906045, 2436710697, 2624047850, 2773027064, 2735441585, 2397006833,
    2403155593, 2322419556, 3211067371, 2902277310, 855808633, 2088710794, 2365540733, 3598761409,
    96395577, 2793454657, 293134636, 997092999, 1516229869, 3830922510, 1456537668, 2543557055,
    2250567838, 47738566, 3310116596, 429974987, 2597497913, 3570401473, 3554123533, 3733732981,
    65117535, 89824483, 467063829, 2980094347, 459068879, 288615762, 505566088, 161491799,
    1950807394, 4134005883, 832113435, 383539149, 3289684623, 984364633, 3767074980, 349818067,
    3687375566, 2799114350, 945518296, 1410152104, 3876984338, 2571021970, 3352118476, 4178487553,
    1203153759, 611118555, 3259506420, 2891465405, 3445709652, 3949641380, 2875186838, 2779811328,
    2510853343, 4275646002, 2491038814, 2422179298, 1650323772, 3000898162, 3627492445, 1678944205,
    4261066493, 2595842431, 3948513109, 2750405154, 2564814475, 2584030305, 2613484438, 3805313548,
    3123055455, 1702940891, 1364814166, 3578222670, 1736412364, 2853289958, 182827375, 3335617072,
    3347856282, 771668471, 2881257662, 1618901303, 2911271774, 2283475553, 1640165049, 3561675959,
    384011161, 1071788681, 2189107920, 2737255457, 1011242690, 1067383324, 2768630076, 35989731,
    3477113433, 3502877655, 3126304174, 2125983443, 1065189709, 3085270280, 2313355736, 2406873069,
    1095216321, 1458394816, 1881192803, 2616673852, 425572748, 1377770342, 4183972521, 154875079,
    1436212463, 1213363850, 2416318273, 3309606622, 3757797616, 936646994, 3996477797, 836380753,
    1177367651, 2904456246, 600155443, 2732456183, 2305820104, 1912638872, 271951098, 119535942,
];
const DAY_OFFSET_ENCODE_PERIODS: [f32; 7] = [3.0, 7.0, 30.0, 100.0, 365.0, 3650.0, 36500.0];
const SECONDS_PER_DAY: i64 = 86_400;

const ELAPSED_DAYS_MEAN: f32 = 1.51;
const ELAPSED_DAYS_STD: f32 = 1.62;
const ELAPSED_DAYS_CUMULATIVE_MEAN: f32 = 2.14;
const ELAPSED_DAYS_CUMULATIVE_STD: f32 = 2.25;
const ELAPSED_SECONDS_MEAN: f32 = 9.96;
const ELAPSED_SECONDS_STD: f32 = 5.21;
const ELAPSED_SECONDS_CUMULATIVE_MEAN: f32 = 10.86;
const ELAPSED_SECONDS_CUMULATIVE_STD: f32 = 5.8;
const DURATION_MEAN: f32 = 8.9;
const DURATION_STD: f32 = 1.07;
const DIFF_NEW_CARDS_MEAN: f32 = 2.945;
const DIFF_NEW_CARDS_STD: f32 = 2.011;
const DIFF_REVIEWS_MEAN: f32 = 4.64;
const DIFF_REVIEWS_STD: f32 = 2.59;
const CUM_NEW_CARDS_TODAY_MEAN: f32 = 2.55;
const CUM_NEW_CARDS_TODAY_STD: f32 = 1.41;
const CUM_REVIEWS_TODAY_MEAN: f32 = 4.59;
const CUM_REVIEWS_TODAY_STD: f32 = 1.30;

pub use crate::rwkv::api::*;

pub struct RwkvInference {
    #[cfg(test)]
    captured_curves: Option<Vec<ReviewCurve>>,
    model: Arc<SrsModel>,
    /// Optional answer-time head (`time_head.*.bin` next to the weights).
    answer_time_head: Option<Arc<AnswerTimeHead>>,
    features: FeatureState,
    curves: HashMap<i64, ReviewCurve>,
    warm_up_states: ReviewStateMaps,
    stashed_warm_up_state: Option<StashedWarmUpState>,
    state_cache_store: Option<StateCacheStoreWriter>,
    target_retention: f32,
    max_interval_days: u32,
}

/// Resident state moved aside while a temporary replay uses the runtime.
struct StashedWarmUpState {
    features: FeatureState,
    curves: HashMap<i64, ReviewCurve>,
    warm_up_states: ReviewStateMaps,
    spill: Option<WarmUpStateSpill>,
}

struct WarmUpStateSpill {
    path: PathBuf,
    counts: [usize; 2],
    max_state_bytes: usize,
    hash: blake3::Hash,
}

impl WarmUpStateSpill {
    fn write(path: &Path, states: &ReviewStateMaps) -> io::Result<Self> {
        let path = std::path::absolute(path)?;
        let mut file = anki_io::new_tempfile_in_parent_of(&path).map_err(io::Error::other)?;
        let mut out = io::BufWriter::new(&mut file);
        let mut hasher = blake3::Hasher::new();
        let mut max_state_bytes = 0;
        io::Write::write_all(&mut out, WARM_UP_STATE_SPILL_MAGIC)?;
        hasher.update(WARM_UP_STATE_SPILL_MAGIC);
        let counts = [states.card.len(), states.note.len()];
        for states in [&states.card, &states.note] {
            write_snapshot_u32(&mut out, states.len())?;
            hasher.update(&(states.len() as u32).to_le_bytes());
            // Only one serialized module is held at a time. Do not build a
            // serialized snapshot or drain the maps before publishing the file.
            for (id, state) in states.iter() {
                let bytes = serialize_module_state(state);
                max_state_bytes = max_state_bytes.max(bytes.len());
                io::Write::write_all(&mut out, &id.to_le_bytes())?;
                hasher.update(&id.to_le_bytes());
                write_snapshot_u32(&mut out, bytes.len())?;
                hasher.update(&(bytes.len() as u32).to_le_bytes());
                io::Write::write_all(&mut out, &bytes)?;
                hasher.update(&bytes);
            }
        }
        io::Write::flush(&mut out)?;
        drop(out);
        anki_io::atomic_rename(file, &path, false).map_err(io::Error::other)?;
        Ok(Self {
            path,
            counts,
            max_state_bytes,
            hash: hasher.finalize(),
        })
    }

    fn read(&self) -> io::Result<[HashMap<i64, ModuleState>; 2]> {
        let file = anki_io::open_file(&self.path).map_err(io::Error::other)?;
        let mut input = io::BufReader::new(file);
        let mut hasher = blake3::Hasher::new();
        let mut magic = vec![0; WARM_UP_STATE_SPILL_MAGIC.len()];
        io::Read::read_exact(&mut input, &mut magic)?;
        hasher.update(&magic);
        if magic != WARM_UP_STATE_SPILL_MAGIC {
            return Err(Self::invalid_data());
        }
        let mut maps = [HashMap::new(), HashMap::new()];
        let mut bytes = Vec::new();
        for (states, expected_count) in maps.iter_mut().zip(self.counts) {
            let count = read_state_cache_u32(&mut input)?;
            hasher.update(&count.to_le_bytes());
            if count as usize != expected_count {
                return Err(Self::invalid_data());
            }
            for _ in 0..count {
                let id = read_state_cache_i64(&mut input)?;
                let len = read_state_cache_u32(&mut input)?;
                hasher.update(&id.to_le_bytes());
                hasher.update(&len.to_le_bytes());
                if len as usize > self.max_state_bytes
                    || (len as usize) < b"ARWKVMODSTATE1".len() + 4
                {
                    return Err(Self::invalid_data());
                }
                bytes.resize(len as usize, 0);
                io::Read::read_exact(&mut input, &mut bytes)?;
                hasher.update(&bytes);
                // Both formats validate layer counts before allocating, even
                // if a corrupt record has a valid length.
                let state = deserialize_module_state(Some(&bytes))?.unwrap();
                if states.insert(id, state).is_some() {
                    return Err(Self::invalid_data());
                }
            }
        }
        let mut trailing = [0];
        if io::Read::read(&mut input, &mut trailing)? != 0 || hasher.finalize() != self.hash {
            return Err(Self::invalid_data());
        }
        Ok(maps)
    }

    fn invalid_data() -> io::Error {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "invalid RWKV resident state spill",
        )
    }
}

/// Frees a replaced resident state off the caller's thread. A full
/// collection's state is a few hundred thousand allocations, and freeing
/// them took about 90ms that the caller would otherwise wait for.
fn drop_in_background<T: Send + 'static>(value: T) {
    // If the thread can't start, the closure and `value` are dropped here.
    let _ = std::thread::Builder::new()
        .name("rwkv-state-drop".into())
        .spawn(move || {
            drop(value);
            release_free_memory_to_os();
        });
}

/// Returns unused allocator pages after large RWKV jobs have freed their state.
pub fn release_free_memory_to_os() {
    let start = std::time::Instant::now();
    #[cfg(all(target_os = "linux", target_env = "gnu"))]
    {
        // libc is only a dev-dependency of this crate; declare the glibc symbol
        // directly so production builds do not need an additional dependency.
        unsafe extern "C" {
            fn malloc_trim(pad: usize) -> std::ffi::c_int;
        }
        // SAFETY: malloc_trim only releases unused allocator pages; zero requests
        // no extra padding, and the allocator synchronizes concurrent access.
        unsafe {
            malloc_trim(0);
        }
    }
    #[cfg(target_os = "macos")]
    {
        unsafe extern "C" {
            fn malloc_zone_pressure_relief(zone: *mut std::ffi::c_void, goal: usize) -> usize;
        }
        // SAFETY: a null zone visits all zones, and a zero goal releases as
        // much unused memory as possible. Live allocations are unaffected.
        unsafe {
            malloc_zone_pressure_relief(std::ptr::null_mut(), 0);
        }
    }
    tracing::debug!(
        elapsed_ms = start.elapsed().as_secs_f64() * 1000.0,
        "released RWKV free memory to OS"
    );
}

struct StateCacheStoreWriter {
    path: PathBuf,
    generation: String,
    durable: bool,
    delta_chunk_bytes: usize,
    connection: Mutex<Connection>,
}

impl StateCacheStoreWriter {
    fn open(path: PathBuf, generation: &str, durable: bool) -> io::Result<Self> {
        let connection = open_state_cache_store(&path, generation, durable)?;
        Ok(Self {
            path,
            generation: generation.to_owned(),
            durable,
            delta_chunk_bytes: STATE_CACHE_DELTA_CHUNK_BYTES,
            connection: Mutex::new(connection),
        })
    }

    fn matches(&self, path: &PathBuf, generation: &str, durable: bool) -> bool {
        self.path == *path && self.generation == generation && self.durable == durable
    }
}

#[derive(Clone)]
pub struct RwkvInferenceState {
    feature_state: FeatureStateForCard,
    card_id: i64,
    curve: Option<ReviewCurve>,
}

struct RwkvWorkloadQueryPrediction {
    retrievability: f32,
    current_interval: Option<u32>,
    current_s90: Option<f32>,
}

struct ReviewPredictionWorkItem {
    features: Vec<f32>,
    state: SrsStateOwned,
}

struct ReviewPredictionBorrowedWorkItem<'a> {
    features: Vec<f32>,
    state: SrsStateRef<'a>,
}

#[derive(Clone, Copy)]
struct ReviewPredictionQueryRef<'a> {
    features: &'a [f32],
    state: SrsStateRef<'a>,
}

impl ReviewPredictionQueryRef<'_> {
    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn layer_state(&self, module_id: usize, layer_id: usize) -> Option<&LayerState> {
        self.state
            .module(module_id)
            .and_then(|state| state.layers.get(layer_id))
    }
}

#[derive(Clone)]
struct RwkvSimulationCard {
    review_input: RwkvWorkloadSimulationInput,
    due_day: i64,
    last_review_day: i64,
    reps: i64,
    lapses: i64,
    interval_days: i64,
    ease_factor: i64,
    is_new: bool,
    suspended: bool,
}

struct RwkvWorkloadTargetConfig<'a> {
    dr: u32,
    days_to_simulate: i64,
    review_limit: usize,
    new_limit: usize,
    new_cards_ignore_review_limit: bool,
    max_interval: u32,
    review_order: ReviewCardOrder,
    suspend_after_lapses: Option<u32>,
    state_update_interval: u32,
    review_model: &'a RwkvWorkloadReviewModel,
}

struct RwkvWorkloadTargetSweep<'a> {
    inputs: Vec<RwkvWorkloadSimulationInput>,
    base_state: &'a ReviewStateMaps,
    /// Upper bound of the states one target can copy from `base_state`.
    target_state_len: usize,
    target_drs: Vec<u32>,
    days_to_simulate: i64,
    review_limit: usize,
    new_limit: usize,
    new_cards_ignore_review_limit: bool,
    max_interval: u32,
    review_order: ReviewCardOrder,
    suspend_after_lapses: Option<u32>,
    state_update_interval: u32,
    review_model: RwkvWorkloadReviewModel,
    base_features: FeatureState,
    total_steps: u32,
}

impl RwkvInference {
    pub fn load(path: PathBuf, target_retention: f32, max_interval_days: u32) -> io::Result<Self> {
        Self::load_with_precision(
            path,
            target_retention,
            max_interval_days,
            StatePrecision::F32,
        )
    }

    pub fn load_with_precision(
        path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
    ) -> io::Result<Self> {
        let mut engine = Self::from_model(
            SrsModel::load(&path)?,
            target_retention,
            max_interval_days,
            precision,
        );
        engine.answer_time_head = AnswerTimeHead::load_beside(&path)?.map(Arc::new);
        Ok(engine)
    }

    /// Whether the optional answer-time head was loaded with the model.
    pub fn has_answer_time_head(&self) -> bool {
        self.answer_time_head.is_some()
    }

    /// Card layers of the loaded checkpoint: 1 for the tiny model (the
    /// smaller instant-only model, SM101), 2 for the other small models.
    pub fn card_layers(&self) -> usize {
        self.model.card_layers()
    }

    /// Predicts the answer time of each query from the frozen trunk and the
    /// history features of [`crate::rwkv::AnswerTimeHistory::features`]
    /// (computed after every review before the query; their elapsed-days
    /// entries are replaced by the query's own). `None` without the head and
    /// for queries on the day of the card's previous review or on a new card:
    /// the head was trained only on reviews on a later day. Grade outputs are
    /// computed elsewhere and never change.
    pub fn predict_answer_times(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
        history: Vec<[f32; answer_time_items::ANSWER_TIME_FEATURES]>,
    ) -> io::Result<Vec<Option<AnswerTimeOutput>>> {
        if requests.len() != history.len() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV answer-time history count mismatch",
            ));
        }
        let Some(head) = self.answer_time_head.clone() else {
            return Ok(vec![None; requests.len()]);
        };
        let mut out = Vec::with_capacity(requests.len());
        for (request, mut explicit) in requests.into_iter().zip(history) {
            if !request.input.is_query {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV answer-time prediction only supports query inputs",
                ));
            }
            let elapsed = elapsed_days(&request.input, elapsed_seconds(&request.input));
            if elapsed < 1 {
                out.push(None);
                continue;
            }
            set_answer_time_elapsed(&mut explicit, elapsed);
            let item = self.prediction_work_item(&request.input, &request.state)?;
            let (frozen, button_probabilities) = self
                .model
                .answer_time_inputs(&item.features, item.state.as_ref());
            out.push(Some(head.predict(&frozen, &explicit, button_probabilities)));
        }
        Ok(out)
    }

    /// Feeds the checkpoint leak-free inputs: the missing note, deck and
    /// preset flags are always zero, as in its training data. Call before
    /// the runtime is shared.
    pub fn with_leak_free_inputs(mut self) -> io::Result<Self> {
        Arc::get_mut(&mut self.model)
            .ok_or_else(|| io::Error::other("RWKV model is already shared"))?
            .leak_free_inputs = true;
        Ok(self)
    }

    fn from_model(
        mut model: SrsModel,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
    ) -> Self {
        let (card_precision, note_precision) = match precision {
            StatePrecision::Int4CardsInt8Notes => (StatePrecision::Int4, StatePrecision::Int8),
            precision => (precision, precision),
        };
        model.modules[0].precision = card_precision;
        model.modules[2].precision = note_precision;
        Self {
            #[cfg(test)]
            captured_curves: None,
            model: Arc::new(model),
            answer_time_head: None,
            features: FeatureState::default(),
            curves: HashMap::new(),
            warm_up_states: ReviewStateMaps::default(),
            stashed_warm_up_state: None,
            state_cache_store: None,
            target_retention,
            max_interval_days,
        }
    }

    pub fn review(
        &mut self,
        input: ReviewInput,
        state: ReviewState<'_>,
    ) -> io::Result<ReviewOutput> {
        let heads = self.review_heads(&input, &state)?;

        if !input.is_query {
            self.features.store_review(&input);
            self.curves.insert(input.card_id, heads.curve.clone());
        }

        let answer_heads = if input.is_query {
            Some(self.answer_heads(&input, &state)?)
        } else {
            None
        };
        let (intervals, s90s) = answer_heads
            .as_ref()
            .map(|heads| self.answer_intervals(&input, heads))
            .unwrap_or(([None; 4], [None; 4]));
        let (current_interval, current_s90) = self.current_intervals(&input, &heads);
        let curve_retrievability = current_curve_retrievability(&input, &heads.curve);

        let card_state = serialize_module_state(&heads.next_state.card);
        let deck_state = serialize_module_state(&heads.next_state.deck);
        let note_state = serialize_module_state(&heads.next_state.note);
        let preset_state = serialize_module_state(&heads.next_state.preset);
        let global_state = serialize_module_state(&heads.next_state.global);

        if !input.is_query {
            self.warm_up_states.store(&input, heads.next_state);
        }

        Ok(ReviewOutput {
            retrievability: heads.retrievability,
            curve_retrievability,
            button_probabilities: heads.button_probabilities,
            current_interval,
            current_s90,
            intervals,
            s90s,
            card_state,
            deck_state,
            note_state,
            preset_state,
            global_state,
        })
    }

    pub fn predict_many(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
    ) -> io::Result<Vec<ReviewPredictionOutput>> {
        let mut work_items = Vec::with_capacity(requests.len() * (1 + ANSWER_EASES.len()));
        for request in &requests {
            if !request.input.is_query {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV batched prediction only supports query inputs",
                ));
            }

            work_items.push(self.prediction_work_item(&request.input, &request.state)?);
            for ease in ANSWER_EASES {
                work_items.push(self.prediction_work_item(
                    &simulated_answer_input(&request.input, ease),
                    &request.state,
                )?);
            }
        }

        let heads = self.model.review_many(&work_items);
        let chunk_size = 1 + ANSWER_EASES.len();
        Ok(heads
            .chunks_exact(chunk_size)
            .zip(requests)
            .map(|(heads, request)| {
                let query_heads = &heads[0];
                let answer_heads = &heads[1..];
                let (intervals, s90s) = self.answer_intervals(&request.input, answer_heads);
                let (current_interval, current_s90) =
                    self.current_intervals(&request.input, query_heads);
                ReviewPredictionOutput {
                    retrievability: query_heads.retrievability,
                    curve_retrievability: current_curve_retrievability(
                        &request.input,
                        &query_heads.curve,
                    ),
                    button_probabilities: query_heads.button_probabilities,
                    current_interval,
                    current_s90,
                    intervals,
                    s90s,
                }
            })
            .collect())
    }

    pub fn predict_retrievability_many(
        &mut self,
        requests: Vec<ReviewPredictionRequest>,
    ) -> io::Result<Vec<f32>> {
        let mut work_items = Vec::with_capacity(requests.len());
        for request in &requests {
            if !request.input.is_query {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV batched retrievability prediction only supports query inputs",
                ));
            }

            work_items.push(self.prediction_work_item(&request.input, &request.state)?);
        }

        self.model.review_retrievability_many(&work_items)
    }

    pub fn predict_retrievability_many_from_warm_up(
        &mut self,
        inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<f32>> {
        for input in &inputs {
            if !input.is_query {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV batched retrievability prediction only supports query inputs",
                ));
            }
        }

        let features = inputs
            .iter()
            .map(|input| self.features.features_for(input))
            .collect::<Vec<_>>();
        // Compact card and note states are decoded a chunk at a time, not all of
        // a big collection's at once.
        let mut retrievabilities = Vec::with_capacity(inputs.len());
        for (inputs, features) in inputs
            .chunks(STATE_VIEW_CHUNK)
            .zip(features.chunks(STATE_VIEW_CHUNK))
        {
            let views = inputs
                .par_iter()
                .map(|input| self.warm_up_states.state_view(input))
                .collect::<Vec<_>>();
            let items = features
                .iter()
                .zip(&views)
                .map(|(features, view)| ReviewPredictionQueryRef {
                    features,
                    state: view.as_ref(),
                })
                .collect::<Vec<_>>();
            retrievabilities.extend(self.model.review_retrievability_query_refs(&items)?);
        }
        Ok(retrievabilities)
    }

    pub fn predict_retrievability_many_after_review(
        &self,
        answer: ReviewInput,
        query_inputs: Vec<ReviewInput>,
        snapshot: RwkvWorkloadSimulationSnapshot,
    ) -> io::Result<Vec<f32>> {
        if answer.is_query || answer.ease.is_none() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV future prediction requires an answered review input",
            ));
        }
        if query_inputs.iter().any(|input| !input.is_query) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV future prediction only supports query inputs",
            ));
        }

        let Some(runtime_state) = snapshot.runtime_state.as_deref() else {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV future prediction requires a runtime state snapshot",
            ));
        };
        let mut worker = self.worker_from_cache_state(runtime_state)?;
        let state_maps = ReviewStateMaps::from_serialized(
            &snapshot.card_states,
            &snapshot.note_states,
            &snapshot.deck_states,
            &snapshot.preset_states,
            snapshot.global_state.as_deref(),
        )?;
        let mut states = SimulationStates::new(&state_maps);
        worker.apply_simulation_answer(&answer, &mut states)?;

        Ok(worker
            .workload_query_predictions_for_inputs(&query_inputs, &states)
            .into_iter()
            .map(|prediction| prediction.retrievability)
            .collect())
    }

    pub fn predict_retrievability_many_after_reviews(
        &self,
        answers: Vec<ReviewInput>,
        query_inputs: Vec<ReviewInput>,
        snapshot: RwkvWorkloadSimulationSnapshot,
    ) -> io::Result<Vec<Vec<f32>>> {
        for answer in &answers {
            if answer.is_query || answer.ease.is_none() {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV future prediction requires answered review inputs",
                ));
            }
        }
        if query_inputs.iter().any(|input| !input.is_query) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV future prediction only supports query inputs",
            ));
        }

        let Some(runtime_state) = snapshot.runtime_state.as_deref() else {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV future prediction requires a runtime state snapshot",
            ));
        };
        let mut base_features = read_runtime_feature_state(runtime_state)?;
        let base_state_maps = ReviewStateMaps::from_serialized(
            &snapshot.card_states,
            &snapshot.note_states,
            &snapshot.deck_states,
            &snapshot.preset_states,
            snapshot.global_state.as_deref(),
        )?;

        if query_inputs.is_empty() {
            return Ok(answers.iter().map(|_| Vec::new()).collect());
        }

        if answers.first().is_some_and(|first| {
            answers
                .iter()
                .chain(&query_inputs)
                .all(|input| same_feature_identity(first, input))
        }) {
            return Ok(predict_retrievability_many_after_reviews_for_identity(
                self.model.as_ref(),
                &mut base_features,
                &base_state_maps,
                answers,
                &query_inputs,
            ));
        }

        let answer_work_items = answers
            .iter()
            .map(|answer| {
                let mut features = base_features.clone();
                ReviewPredictionWorkItem {
                    features: features.features_for(answer),
                    state: base_state_maps.state_owned(answer),
                }
            })
            .collect::<Vec<_>>();
        let answer_heads = self.model.review_many(&answer_work_items);

        let query_count = query_inputs.len();
        let mut query_work_items = Vec::with_capacity(answers.len() * query_count);
        for (answer, heads) in answers.into_iter().zip(answer_heads) {
            let mut features = base_features.clone();
            features.store_review(&answer);
            for query_input in &query_inputs {
                query_work_items.push(ReviewPredictionWorkItem {
                    features: features.features_for(query_input),
                    state: base_state_maps.state_owned_after_review(
                        &answer,
                        query_input,
                        &heads.next_state,
                    ),
                });
            }
        }

        Ok(self
            .model
            .review_many(&query_work_items)
            .chunks_exact(query_count)
            .map(|heads| {
                heads
                    .iter()
                    .map(|heads| heads.retrievability)
                    .collect::<Vec<_>>()
            })
            .collect())
    }

    pub fn predict_retrievability_many_after_reviews_from_warm_up(
        &mut self,
        answers: Vec<ReviewInput>,
        query_inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<Vec<f32>>> {
        for answer in &answers {
            if answer.is_query || answer.ease.is_none() {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV resident future prediction requires answered review inputs",
                ));
            }
        }
        if query_inputs.iter().any(|input| !input.is_query) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV resident future prediction only supports query inputs",
            ));
        }
        if answers.is_empty() {
            return Ok(Vec::new());
        }
        if query_inputs.is_empty() {
            return Ok(answers.iter().map(|_| Vec::new()).collect());
        }

        let first = &answers[0];
        if !answers
            .iter()
            .chain(&query_inputs)
            .all(|input| same_feature_identity(first, input))
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV resident future prediction requires one feature identity",
            ));
        }

        Ok(predict_retrievability_many_after_reviews_for_identity(
            self.model.as_ref(),
            &mut self.features,
            &self.warm_up_states,
            answers,
            &query_inputs,
        ))
    }

    pub fn warm_up_reviews(
        &mut self,
        reviews: Vec<ReviewInput>,
        record_predictions: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        // The scan-capture harness records per-timestep coefficients from the
        // answer pass only, so it needs the per-review path.
        #[cfg(test)]
        if rwkv_scan_capture_active() {
            return self.warm_up_reviews_sequential(reviews, record_predictions);
        }
        bulk::warm_up_reviews_bulk(self, reviews, record_predictions)
    }

    /// Recompute pre-answer predictions in an independent worker, without
    /// changing the resident state used by the reviewer.
    pub fn forecast_history_predictions(
        &self,
        reviews: Vec<ReviewInput>,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<Vec<(usize, f32)>> {
        const CHUNK: usize = 8192;
        let total = reviews.len();
        let mut worker = self.workload_worker(FeatureState::default(), HashMap::new());
        let mut predictions = Vec::with_capacity(total);
        progress(0, total.min(u32::MAX as usize) as u32)?;
        for (chunk_index, chunk) in reviews.chunks(CHUNK).enumerate() {
            let start = chunk_index * CHUNK;
            predictions.extend(
                worker
                    .warm_up_reviews(chunk.to_vec(), true)?
                    .into_iter()
                    .map(|(index, value)| (start + index, value)),
            );
            progress(
                (start + chunk.len()).min(u32::MAX as usize) as u32,
                total.min(u32::MAX as usize) as u32,
            )?;
        }
        Ok(predictions)
    }

    /// Advance answer history and predict recall immediately after each answer.
    /// Query rows do not advance state; elapsed days and seconds are set to
    /// zero.
    pub fn warm_up_reviews_post(
        &mut self,
        reviews: Vec<ReviewInput>,
    ) -> io::Result<Vec<(usize, f32)>> {
        bulk::warm_up_reviews_post_bulk(self, reviews)
    }

    pub fn warm_up_reviews_query(
        &mut self,
        reviews: Vec<ReviewInput>,
        queries: Vec<ReviewInput>,
        after: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        bulk::warm_up_reviews_query_bulk(self, reviews, queries, after)
    }

    /// The per-review reference implementation of `warm_up_reviews`. The bulk
    /// path must match it bit-for-bit; parity tests compare the two.
    #[cfg_attr(not(test), allow(dead_code))]
    fn warm_up_reviews_sequential(
        &mut self,
        reviews: Vec<ReviewInput>,
        record_predictions: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        let mut predictions = vec![];
        for (index, input) in reviews.into_iter().enumerate() {
            if input.ease.is_none() {
                continue;
            }

            // Query features must be assigned before answer features so
            // first-seen ID encodings keep the benchmark-compatible order.
            let query_features = record_predictions.then(|| {
                let mut query_input = input.clone();
                query_input.is_query = true;
                query_input.ease = None;
                query_input.duration_millis = None;
                self.features.features_for(&query_input)
            });

            let features = self.features.features_for(&input);
            let model = &*self.model;
            // The query and answer inputs share card/note/deck/preset ids, so
            // both passes read the same pre-review state and can run in
            // parallel; only the answer pass advances state below.
            let view = self.warm_up_states.state_view(&input);
            let state = view.as_ref();
            let (query_retrievability, heads) = match &query_features {
                Some(query_features) => {
                    let (retrievability, heads) = rayon::join(
                        || {
                            model.review_retrievability_query_refs(&[ReviewPredictionQueryRef {
                                features: query_features,
                                state,
                            }])
                        },
                        || model.review(&features, state),
                    );
                    (Some(retrievability?[0]), heads)
                }
                None => (None, model.review(&features, state)),
            };
            if let Some(retrievability) = query_retrievability {
                predictions.push((index, retrievability));
            }

            self.features.store_review(&input);
            self.curves.insert(input.card_id, heads.curve.clone());
            self.warm_up_states.store(&input, heads.next_state);
        }

        Ok(predictions)
    }

    #[cfg(test)]
    fn warm_up_reviews_with_state_compression(
        &mut self,
        reviews: &[ReviewInput],
        compression: Option<StateCompression>,
    ) -> Vec<f32> {
        let mut predictions = Vec::new();
        for input in reviews {
            if input.ease.is_none() {
                continue;
            }

            let mut query_input = input.clone();
            query_input.is_query = true;
            query_input.ease = None;
            query_input.duration_millis = None;
            let features = self.features.features_for(&query_input);
            let query_heads = self.model.review(
                &features,
                self.warm_up_states.state_view(&query_input).as_ref(),
            );
            predictions.push(query_heads.retrievability);

            let features = self.features.features_for(input);
            let heads = self
                .model
                .review(&features, self.warm_up_states.state_view(input).as_ref());
            self.features.store_review(input);
            self.curves.insert(input.card_id, heads.curve.clone());
            let next_state = match compression {
                Some(compression) => compressed_srs_state(heads.next_state, compression),
                None => heads.next_state,
            };
            self.warm_up_states.store(input, next_state);
        }

        predictions
    }

    pub fn warm_up_snapshot(&self) -> RwkvWarmUpSnapshot {
        RwkvWarmUpSnapshot {
            card_states: serialize_state_map(&self.warm_up_states.card),
            note_states: serialize_state_map(&self.warm_up_states.note),
            deck_states: serialize_state_map(&self.warm_up_states.deck),
            preset_states: serialize_state_map(&self.warm_up_states.preset),
            global_state: self
                .warm_up_states
                .global
                .as_ref()
                .map(serialize_module_state),
        }
    }

    pub fn warm_up_state(&self, input: &ReviewInput) -> ReviewStateOwned {
        self.warm_up_states.serialized_state(input)
    }

    pub fn append_warm_up_snapshot_binary(&self, path: PathBuf) -> io::Result<()> {
        let mut out = fs::OpenOptions::new().append(true).open(path)?;
        write_snapshot_state_map(&mut out, &self.warm_up_states.card)?;
        write_snapshot_state_map(&mut out, &self.warm_up_states.note)?;
        write_snapshot_state_map(&mut out, &self.warm_up_states.deck)?;
        write_snapshot_state_map(&mut out, &self.warm_up_states.preset)?;
        write_snapshot_optional_bytes(
            &mut out,
            self.warm_up_states
                .global
                .as_ref()
                .map(serialize_module_state)
                .as_deref(),
        )?;
        let cache_state = self.cache_state();
        write_snapshot_optional_bytes(&mut out, Some(&cache_state))
    }

    /// `append_warm_up_snapshot_binary` for a paired runtime: every stream
    /// state and the runtime cache hold the release's (`self`) and the
    /// instant model's, in the layout of `crate::rwkv::paired`.
    pub fn append_paired_warm_up_snapshot_binary(
        &self,
        instant: &RwkvInference,
        path: PathBuf,
    ) -> io::Result<()> {
        let mut out = io::BufWriter::new(fs::OpenOptions::new().append(true).open(path)?);
        let (release, paired) = (&self.warm_up_states, &instant.warm_up_states);
        write_paired_snapshot_state_map(&mut out, &release.card, &paired.card)?;
        write_paired_snapshot_state_map(&mut out, &release.note, &paired.note)?;
        write_paired_snapshot_state_map(&mut out, &release.deck, &paired.deck)?;
        write_paired_snapshot_state_map(&mut out, &release.preset, &paired.preset)?;
        let global = match (
            self.warm_up_states.global.as_ref(),
            instant.warm_up_states.global.as_ref(),
        ) {
            (Some(release), Some(paired)) => Some(crate::rwkv::paired::join_state(
                &serialize_module_state(release),
                &serialize_module_state(paired),
            )),
            (None, None) => None,
            _ => return Err(paired_streams_differ()),
        };
        write_snapshot_optional_bytes(&mut out, global.as_deref())?;
        let cache_state =
            crate::rwkv::paired::join_cache(&self.cache_state(), &instant.cache_state());
        write_snapshot_optional_bytes(&mut out, Some(&cache_state))?;
        io::Write::flush(&mut out)
    }

    /// Instant recall and button probabilities of each query request, from
    /// the query row alone: no simulated answers, which only intervals need.
    pub fn predict_query_outputs_many(
        &mut self,
        requests: &[ReviewPredictionRequest],
    ) -> io::Result<Vec<(f32, [f32; 4])>> {
        let mut work_items = Vec::with_capacity(requests.len());
        for request in requests {
            if !request.input.is_query {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "RWKV batched prediction only supports query inputs",
                ));
            }
            work_items.push(self.prediction_work_item(&request.input, &request.state)?);
        }
        Ok(self
            .model
            .review_many(&work_items)
            .into_iter()
            .map(|heads| (heads.retrievability, heads.button_probabilities))
            .collect())
    }

    /// `review` without intervals or S90: instant recall, button
    /// probabilities and the next stream states. Answers advance the
    /// resident state exactly as `review` does.
    pub fn review_query_outputs(
        &mut self,
        input: ReviewInput,
        state: ReviewState<'_>,
    ) -> io::Result<(f32, [f32; 4], ReviewStateOwned)> {
        let heads = self.review_heads(&input, &state)?;
        if !input.is_query {
            self.features.store_review(&input);
            self.curves.insert(input.card_id, heads.curve.clone());
        }
        let next = ReviewStateOwned {
            card: Some(serialize_module_state(&heads.next_state.card)),
            deck: Some(serialize_module_state(&heads.next_state.deck)),
            note: Some(serialize_module_state(&heads.next_state.note)),
            preset: Some(serialize_module_state(&heads.next_state.preset)),
            global: Some(serialize_module_state(&heads.next_state.global)),
        };
        if !input.is_query {
            self.warm_up_states.store(&input, heads.next_state);
        }
        Ok((heads.retrievability, heads.button_probabilities, next))
    }

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
        self.write_warm_up_state_checkpoint_with(
            None,
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

    /// Writes one segment for a paired runtime: the release (`self`) and the
    /// instant model share the segment row; the runtime cache holds both, and
    /// the instant model's state delta uses its own chunk index range.
    #[allow(clippy::too_many_arguments)]
    pub fn write_paired_warm_up_state_checkpoint(
        &mut self,
        instant: &mut RwkvInference,
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
        self.write_warm_up_state_checkpoint_with(
            Some(instant),
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

    #[allow(clippy::too_many_arguments)]
    fn write_warm_up_state_checkpoint_with(
        &mut self,
        mut instant: Option<&mut RwkvInference>,
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
        let release_runtime_state_len = self.runtime_cache_state_len();
        let runtime_state_len = match instant.as_deref() {
            Some(instant) => {
                crate::rwkv::paired::PAIRED_CACHE_MAGIC.len()
                    + 8
                    + release_runtime_state_len
                    + instant.runtime_cache_state_len()
            }
            None => release_runtime_state_len,
        };
        let state_delta_len = self.warm_up_states.checkpoint_delta_len(full)?;
        let instant_state_delta_len = instant
            .as_deref()
            .map(|instant| instant.warm_up_states.checkpoint_delta_len(full))
            .transpose()?;
        let store = match self.state_cache_store.take() {
            Some(store) if store.matches(&path, store_generation, durable) => store,
            _ => StateCacheStoreWriter::open(path, store_generation, durable)?,
        };
        let result = (|| {
            let mut connection = store
                .connection
                .lock()
                .map_err(|_| io::Error::other("RWKV state-cache connection lock poisoned"))?;
            // The bundled SQLite shares an LRU pool across connections. Clean
            // 4 KiB collection pages can otherwise be replaced by 64 KiB store
            // pages up to the pool's combined page count, exceeding this
            // connection's byte budget. Shrink the pool before the first write,
            // as well as after commit, so spills recycle the store's own pages.
            connection
                .release_memory()
                .map_err(state_cache_store_error)?;
            let transaction = connection.transaction().map_err(state_cache_store_error)?;
            validate_state_cache_parent(&transaction, parent_segment_id)?;
            transaction
                .execute(
                    r#"
insert into segments (
  parent_id,
  last_review_id,
  review_count,
  history_hash,
  replay_key,
  previous_review_ids,
  previous_intervals,
  review_counts,
  runtime_state
) values (?, ?, ?, ?, ?, ?, ?, ?, zeroblob(?))
"#,
                    params![
                        parent_segment_id,
                        last_review_id,
                        review_count,
                        history_hash,
                        replay_key,
                        previous_review_ids,
                        previous_intervals,
                        review_counts,
                        i64::try_from(runtime_state_len).map_err(|_| {
                            io::Error::new(
                                io::ErrorKind::InvalidData,
                                "RWKV runtime state is too large",
                            )
                        })?,
                    ],
                )
                .map_err(state_cache_store_error)?;
            let segment_id = transaction.last_insert_rowid();
            let state_delta_chunk_ids = insert_state_cache_delta_chunks(
                &transaction,
                segment_id,
                state_delta_len,
                store.delta_chunk_bytes,
                0,
            )?;
            let instant_state_delta_chunk_ids = instant_state_delta_len
                .map(|len| {
                    insert_state_cache_delta_chunks(
                        &transaction,
                        segment_id,
                        len,
                        store.delta_chunk_bytes,
                        crate::rwkv::paired::INSTANT_CHUNK_BASE,
                    )
                })
                .transpose()?;
            {
                let mut runtime_state = transaction
                    .blob_open(MAIN_DB, "segments", "runtime_state", segment_id, false)
                    .map_err(state_cache_store_error)?;
                if let Some(instant) = instant.as_deref() {
                    io::Write::write_all(
                        &mut runtime_state,
                        crate::rwkv::paired::PAIRED_CACHE_MAGIC,
                    )?;
                    io::Write::write_all(
                        &mut runtime_state,
                        &(release_runtime_state_len as u64).to_le_bytes(),
                    )?;
                    self.write_runtime_cache_state(&mut runtime_state)?;
                    instant.write_runtime_cache_state(&mut runtime_state)?;
                } else {
                    self.write_runtime_cache_state(&mut runtime_state)?;
                }
                runtime_state.close().map_err(state_cache_store_error)?;
            }
            if let (Some(instant), Some(chunk_ids), Some(len)) = (
                instant.as_deref(),
                instant_state_delta_chunk_ids,
                instant_state_delta_len,
            ) {
                let mut state_delta =
                    StateCacheDeltaChunkWriter::new(&transaction, chunk_ids, len)?;
                instant
                    .warm_up_states
                    .write_checkpoint_delta(&mut state_delta, full)?;
                state_delta.finish()?;
            }
            {
                let mut state_delta = StateCacheDeltaChunkWriter::new(
                    &transaction,
                    state_delta_chunk_ids,
                    state_delta_len,
                )?;
                self.warm_up_states
                    .write_checkpoint_delta(&mut state_delta, full)?;
                state_delta.finish()?;
            }
            transaction.commit().map_err(state_cache_store_error)?;
            // The writer stays open between checkpoints. Release the clean
            // pages left by large zeroblob inserts and incremental blob writes.
            connection
                .release_memory()
                .map_err(state_cache_store_error)?;
            self.warm_up_states.mark_checkpoint_saved();
            if let Some(instant) = instant.as_deref_mut() {
                instant.warm_up_states.mark_checkpoint_saved();
            }
            Ok(segment_id)
        })();
        if result.is_ok() {
            self.state_cache_store = Some(store);
        }
        result
    }

    pub fn finish_warm_up_state_checkpoints(&mut self) -> io::Result<()> {
        let Some(store) = self.state_cache_store.take() else {
            return Ok(());
        };
        let connection = store
            .connection
            .into_inner()
            .map_err(|_| io::Error::other("RWKV state-cache connection lock poisoned"))?;
        connection
            .close()
            .map_err(|(_, error)| state_cache_store_error(error))
    }

    pub fn restore_warm_up_state_checkpoint(
        &mut self,
        path: PathBuf,
        store_generation: &str,
        segment_id: i64,
    ) -> io::Result<()> {
        self.restore_warm_up_state_checkpoint_with(None, path, store_generation, segment_id)
    }

    /// Restores a segment written by `write_paired_warm_up_state_checkpoint`.
    pub fn restore_paired_warm_up_state_checkpoint(
        &mut self,
        instant: &mut RwkvInference,
        path: PathBuf,
        store_generation: &str,
        segment_id: i64,
    ) -> io::Result<()> {
        self.restore_warm_up_state_checkpoint_with(
            Some(instant),
            path,
            store_generation,
            segment_id,
        )
    }

    fn restore_warm_up_state_checkpoint_with(
        &mut self,
        instant: Option<&mut RwkvInference>,
        path: PathBuf,
        store_generation: &str,
        segment_id: i64,
    ) -> io::Result<()> {
        self.finish_warm_up_state_checkpoints()?;
        let connection = Connection::open_with_flags(
            path,
            OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NO_MUTEX,
        )
        .map_err(state_cache_store_error)?;
        validate_state_cache_store(&connection, store_generation)?;
        let runtime_state = connection
            .query_row(
                "select runtime_state from segments where id = ?",
                [segment_id],
                |row| row.get::<_, Vec<u8>>(0),
            )
            .optional()
            .map_err(state_cache_store_error)?
            .ok_or_else(|| {
                io::Error::new(
                    io::ErrorKind::InvalidData,
                    "missing RWKV state-cache segment",
                )
            })?;
        let segment_chain = state_cache_segment_chain(&connection, segment_id)?;
        let Some(instant) = instant else {
            let warm_up_states = read_state_cache_maps(&connection, &segment_chain, 0)?;
            let (features, curves) = read_runtime_cache_state(&runtime_state)?;
            self.warm_up_states = warm_up_states;
            self.features = features;
            self.curves = curves;
            return Ok(());
        };
        instant.finish_warm_up_state_checkpoints()?;
        let (release_runtime, instant_runtime) = crate::rwkv::paired::split_cache(&runtime_state)?;
        let warm_up_states = read_state_cache_maps(&connection, &segment_chain, 0)?;
        let instant_warm_up_states = read_state_cache_maps(
            &connection,
            &segment_chain,
            crate::rwkv::paired::INSTANT_CHUNK_BASE,
        )?;
        let (features, curves) = read_runtime_cache_state(release_runtime)?;
        let (instant_features, instant_curves) = read_runtime_cache_state(instant_runtime)?;

        self.warm_up_states = warm_up_states;
        self.features = features;
        self.curves = curves;
        instant.warm_up_states = instant_warm_up_states;
        instant.features = instant_features;
        instant.curves = instant_curves;
        Ok(())
    }

    pub fn restore_warm_up_snapshot(&mut self, snapshot: RwkvWarmUpSnapshot) -> io::Result<()> {
        self.warm_up_states = ReviewStateMaps::from_serialized(
            &snapshot.card_states,
            &snapshot.note_states,
            &snapshot.deck_states,
            &snapshot.preset_states,
            snapshot.global_state.as_deref(),
        )?;
        Ok(())
    }

    /// What kuma3's offline state file holds, taken now to be written on
    /// another thread: card and note states are shared ([SharedStateMap]),
    /// so this copies pointers, not the states (the deck, preset and global
    /// ones are few and copied).
    pub fn state_snapshot(&self) -> RwkvStateSnapshot {
        let states = &self.warm_up_states;
        RwkvStateSnapshot {
            states: ReviewStateMaps {
                card: states.card.clone(),
                note: states.note.clone(),
                deck: states.deck.clone(),
                preset: states.preset.clone(),
                global: states.global.clone(),
                ..Default::default()
            },
            cache_state: self.cache_state(),
        }
    }

    /// Reads what [RwkvStateSnapshot::write_warm_up_states] wrote, one state at
    /// a time.
    pub fn read_warm_up_states(&mut self, input: &mut impl io::Read) -> io::Result<()> {
        let mut buffer = Vec::new();
        let mut card = SharedStateMap::default();
        let mut note = SharedStateMap::default();
        let mut deck = HashMap::new();
        let mut preset = HashMap::new();
        // the file's map order (card, note, deck, preset) against the model's module
        // order (card, deck, note, preset, global): every state must have its
        // module's layer count
        let layers = |module: usize| self.model.modules[module].layers.len();
        for (map, layers) in [(&mut card, layers(0)), (&mut note, layers(2))] {
            let count = read_u64_le(input)?;
            for _ in 0..count {
                let id = read_u64_le(input)? as i64;
                map.put(id, read_file_module_state(input, layers)?);
            }
        }
        for (map, layers) in [(&mut deck, layers(1)), (&mut preset, layers(3))] {
            let count = read_u64_le(input)?;
            for _ in 0..count {
                let id = read_u64_le(input)? as i64;
                map.insert(id, read_half_module_state(input, &mut buffer, layers)?);
            }
        }
        let mut flag = [0_u8];
        input.read_exact(&mut flag)?;
        let global = match flag[0] {
            1 => Some(read_half_module_state(input, &mut buffer, layers(4))?),
            _ => None,
        };
        self.warm_up_states = ReviewStateMaps {
            card,
            note,
            deck,
            preset,
            global,
            ..Default::default()
        };
        Ok(())
    }

    pub fn restore_warm_up_state(
        &mut self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
        state: ReviewStateOwned,
    ) -> io::Result<()> {
        self.warm_up_states
            .restore_serialized(card_id, note_id, deck_id, preset_id, &state)
    }

    /// Moves the resident state and feature caches aside without
    /// serializing them, leaving the runtime empty for a temporary replay.
    /// `restore_stashed_warm_up_state()` puts them back unchanged, including
    /// the changes that the next delta checkpoint still has to save.
    pub fn stash_warm_up_state(&mut self) -> io::Result<()> {
        if self.stashed_warm_up_state.is_some() {
            return Err(io::Error::other("RWKV resident state is already stashed"));
        }
        self.stashed_warm_up_state = Some(StashedWarmUpState {
            features: mem::take(&mut self.features),
            curves: mem::take(&mut self.curves),
            warm_up_states: mem::take(&mut self.warm_up_states),
            spill: None,
        });
        Ok(())
    }

    /// Stashes resident state, spilling only card and note modules to disk.
    /// Any write failure leaves the complete resident state untouched.
    pub fn stash_warm_up_state_to_file(&mut self, path: &Path) -> io::Result<()> {
        if self.stashed_warm_up_state.is_some() {
            return Err(io::Error::other("RWKV resident state is already stashed"));
        }
        let spill = WarmUpStateSpill::write(path, &self.warm_up_states)?;
        self.stash_warm_up_state()?;
        let stashed = self.stashed_warm_up_state.as_mut().unwrap();
        stashed.spill = Some(spill);
        drop(mem::take(&mut stashed.warm_up_states.card));
        drop(mem::take(&mut stashed.warm_up_states.note));
        release_free_memory_to_os();
        Ok(())
    }

    /// Replaces the current state with the one saved by
    /// `stash_warm_up_state()`.
    pub fn restore_stashed_warm_up_state(&mut self) -> io::Result<()> {
        let stashed = self
            .stashed_warm_up_state
            .as_mut()
            .ok_or_else(|| io::Error::other("no stashed RWKV resident state"))?;
        if let Some(spill) = &stashed.spill {
            // Discard the temporary replay's large maps synchronously so they
            // do not coexist with the restored maps. On error the user's state
            // still lives in the stash and spill file, ready for another attempt.
            drop(mem::take(&mut self.warm_up_states.card));
            drop(mem::take(&mut self.warm_up_states.note));
            release_free_memory_to_os();
            let [card, note] = spill.read()?;
            stashed.warm_up_states.card = card.into_iter().collect();
            stashed.warm_up_states.note = note.into_iter().collect();
        }
        let stashed = self
            .stashed_warm_up_state
            .take()
            .ok_or_else(|| io::Error::other("no stashed RWKV resident state"))?;
        let replaced = StashedWarmUpState {
            features: mem::replace(&mut self.features, stashed.features),
            curves: mem::replace(&mut self.curves, stashed.curves),
            warm_up_states: mem::replace(&mut self.warm_up_states, stashed.warm_up_states),
            spill: None,
        };
        drop_in_background(replaced);
        if let Some(spill) = stashed.spill {
            if let Err(error) = anki_io::remove_file(&spill.path) {
                tracing::warn!(path = ?spill.path, %error, "failed to delete RWKV resident state spill");
            }
        }
        Ok(())
    }

    /// Discards a stash that will no longer be restored, freeing it off-thread.
    pub fn discard_stashed_warm_up_state(&mut self) {
        if let Some(stashed) = self.stashed_warm_up_state.take() {
            if let Some(spill) = &stashed.spill {
                if let Err(error) = anki_io::remove_file(&spill.path) {
                    tracing::warn!(path = ?spill.path, %error, "failed to delete RWKV resident state spill");
                }
            }
            drop_in_background(stashed);
        }
    }

    pub fn reset_warm_up_state(&mut self) {
        self.state_cache_store = None;
        self.warm_up_states = ReviewStateMaps::default();
    }

    fn review_heads(
        &mut self,
        input: &ReviewInput,
        state: &ReviewState<'_>,
    ) -> io::Result<ReviewHeads> {
        let card_state = deserialize_module_state(state.card)?;
        let deck_state = deserialize_module_state(state.deck)?;
        let note_state = deserialize_module_state(state.note)?;
        let preset_state = deserialize_module_state(state.preset)?;
        let global_state = deserialize_module_state(state.global)?;
        self.review_heads_for_state(
            input,
            SrsStateRef {
                card: card_state.as_ref(),
                deck: deck_state.as_ref(),
                note: note_state.as_ref(),
                preset: preset_state.as_ref(),
                global: global_state.as_ref(),
            },
        )
    }

    fn review_heads_for_state(
        &mut self,
        input: &ReviewInput,
        state: SrsStateRef<'_>,
    ) -> io::Result<ReviewHeads> {
        let features = self.features.features_for(input);
        Ok(self.model.review(&features, state))
    }

    fn prediction_work_item(
        &mut self,
        input: &ReviewInput,
        state: &ReviewStateOwned,
    ) -> io::Result<ReviewPredictionWorkItem> {
        Ok(ReviewPredictionWorkItem {
            state: state.deserialize()?,
            features: self.features.features_for(input),
        })
    }

    fn answer_heads(
        &mut self,
        input: &ReviewInput,
        state: &ReviewState<'_>,
    ) -> io::Result<[ReviewHeads; 4]> {
        let mut heads = Vec::with_capacity(ANSWER_EASES.len());
        for ease in ANSWER_EASES {
            heads.push(self.review_heads(&simulated_answer_input(input, ease), state)?);
        }

        match heads.try_into() {
            Ok(heads) => Ok(heads),
            Err(_) => unreachable!("answer head count should be fixed"),
        }
    }

    fn answer_intervals(
        &self,
        input: &ReviewInput,
        answer_heads: &[ReviewHeads],
    ) -> ([Option<f32>; 4], [Option<f32>; 4]) {
        let curves = std::array::from_fn(|index| &answer_heads[index].curve);
        let target_retentions = std::array::from_fn(|index| {
            input.target_retentions[index].unwrap_or(self.target_retention)
        });

        (
            unrounded_intervals_for_answer_curves(
                curves,
                target_retentions,
                self.max_interval_days,
                input.enforce_grade_order,
            ),
            unrounded_intervals_for_answer_curves(
                curves,
                [S90_TARGET_RETENTION; 4],
                self.max_interval_days,
                input.enforce_grade_order,
            ),
        )
    }

    fn current_intervals(
        &self,
        input: &ReviewInput,
        heads: &ReviewHeads,
    ) -> (Option<u32>, Option<f32>) {
        let target_retention = input.target_retentions[2].unwrap_or(self.target_retention);
        (
            interval_for_curve(&heads.curve, target_retention, self.max_interval_days),
            unrounded_interval_for_curve(
                &heads.curve,
                S90_TARGET_RETENTION,
                self.max_interval_days,
            ),
        )
    }

    pub fn state_for_card(&self, card_id: i64) -> RwkvInferenceState {
        RwkvInferenceState {
            feature_state: self.features.state_for_card(card_id),
            card_id,
            curve: self.curves.get(&card_id).cloned(),
        }
    }

    pub fn state_for_review(
        &self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
    ) -> RwkvInferenceState {
        RwkvInferenceState {
            feature_state: self
                .features
                .state_for_review(card_id, note_id, deck_id, preset_id),
            card_id,
            curve: self.curves.get(&card_id).cloned(),
        }
    }

    pub fn restore_state(&mut self, state: &RwkvInferenceState) {
        self.features.restore_state(&state.feature_state);
        if let Some(curve) = &state.curve {
            self.curves.insert(state.card_id, curve.clone());
        } else {
            self.curves.remove(&state.card_id);
        }
    }

    pub fn cache_state(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(self.runtime_cache_state_len());
        self.write_runtime_cache_state(&mut out)
            .expect("writing to a Vec should not fail");
        out
    }

    fn runtime_cache_state_len(&self) -> usize {
        b"ARWKVPROCSTATE4".len()
            + 1
            + self.features.cache_state_len()
            + 4
            + self
                .curves
                .values()
                .map(|curve| 8 + curve.cache_state_len())
                .sum::<usize>()
    }

    fn write_runtime_cache_state(&self, out: &mut impl io::Write) -> io::Result<()> {
        out.write_all(b"ARWKVPROCSTATE4")?;
        out.write_all(&[u8::from(SMALL_MODEL)])?;
        self.features.write_cache_state_to(out)?;
        write_state_cache_u32(out, self.curves.len())?;
        let mut curves: Vec<_> = self.curves.iter().collect();
        curves.sort_by_key(|(card_id, _)| *card_id);
        for (card_id, curve) in curves {
            out.write_all(&card_id.to_le_bytes())?;
            curve.write_cache_state_to(out)?;
        }
        Ok(())
    }

    pub fn restore_cache_state(&mut self, bytes: &[u8]) -> io::Result<()> {
        let (features, curves) = read_runtime_cache_state(bytes)?;
        self.features = features;
        self.curves = curves;
        Ok(())
    }

    fn worker_from_cache_state(&self, bytes: &[u8]) -> io::Result<RwkvInference> {
        let (features, curves) = read_runtime_cache_state(bytes)?;
        Ok(self.workload_worker(features, curves))
    }

    pub fn simulate_workload(
        &self,
        inputs: Vec<RwkvWorkloadSimulationInput>,
        mut snapshot: RwkvWorkloadSimulationSnapshot,
        config: RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadSimulationOutput> {
        check_workload_dr_range(&config)?;
        let Some(runtime_state) = snapshot.runtime_state.take() else {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "RWKV workload simulation requires a runtime state snapshot",
            ));
        };
        let base_features = read_runtime_feature_state(&runtime_state)?;
        let base_state = ReviewStateMaps::from_snapshot_for_inputs(&snapshot, &inputs)?;
        drop(snapshot);
        self.simulate_workload_with_state(inputs, base_features, &base_state, config, progress)
    }

    /// Simulates from the resident warmed state instead of a serialized
    /// snapshot, leaving that state unchanged.
    pub fn simulate_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvWorkloadSimulationInput>,
        config: RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadSimulationOutput> {
        check_workload_dr_range(&config)?;
        self.simulate_workload_with_state(
            inputs,
            self.features.clone(),
            &self.warm_up_states,
            config,
            progress,
        )
    }

    /// The simulation only reads the card, note, deck and preset streams of
    /// `inputs` plus the global stream from `base_state`, and never changes
    /// it: each target copies a stream when it first advances it.
    fn simulate_workload_with_state(
        &self,
        inputs: Vec<RwkvWorkloadSimulationInput>,
        base_features: FeatureState,
        base_state: &ReviewStateMaps,
        config: RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadSimulationOutput> {
        let target_dr_step = config.target_dr_step.max(1);
        let target_drs = target_drs(config.min_dr, config.max_dr, target_dr_step);
        let total_steps = target_drs.len() as u32 + 1;
        progress(0, total_steps)?;
        // Simulation workers never read recall curves, so they start without
        // copies of them.
        let (reviewless_end_memorized, reviewless_end_weighted_memorized) = {
            let mut reviewless_worker = self.workload_worker(base_features.clone(), HashMap::new());
            let introduced_inputs = inputs
                .iter()
                .filter(|input| input.review_input.card_type != Some(CardType::New as i64))
                .cloned()
                .collect::<Vec<_>>();
            reviewless_worker.simulation_memorized_for_inputs(
                &introduced_inputs,
                &SimulationStates::new(base_state),
                S90_TARGET_RETENTION,
                config.days_to_simulate as i64,
            )?
        };
        progress(1, total_steps)?;

        let target_state_len = base_state.state_len_for(&StreamIds::for_workload_inputs(&inputs));
        let points = self.simulate_workload_targets(
            RwkvWorkloadTargetSweep {
                inputs,
                base_state,
                target_state_len,
                target_drs,
                days_to_simulate: config.days_to_simulate as i64,
                review_limit: config.review_limit as usize,
                new_limit: config.new_limit as usize,
                new_cards_ignore_review_limit: config.new_cards_ignore_review_limit,
                max_interval: config.max_interval.max(1),
                review_order: config.review_order,
                suspend_after_lapses: config.suspend_after_lapses,
                state_update_interval: config.state_update_interval.max(1),
                review_model: config.review_model,
                base_features,
                total_steps,
            },
            progress,
        )?;
        Ok(RwkvWorkloadSimulationOutput {
            reviewless_end_memorized,
            reviewless_end_weighted_memorized,
            points,
        })
    }

    fn simulate_workload_targets(
        &self,
        sweep: RwkvWorkloadTargetSweep<'_>,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<Vec<(u32, RwkvWorkloadSimulationPoint)>> {
        let RwkvWorkloadTargetSweep {
            inputs,
            base_state,
            target_state_len,
            target_drs,
            days_to_simulate,
            review_limit,
            new_limit,
            new_cards_ignore_review_limit,
            max_interval,
            review_order,
            suspend_after_lapses,
            state_update_interval,
            review_model,
            base_features,
            total_steps,
        } = sweep;

        let simulate_target = |dr: u32| {
            let mut worker = self.workload_worker(base_features.clone(), HashMap::new());
            let mut states = SimulationStates::new(base_state);
            worker.simulate_workload_for_target(
                &inputs,
                &mut states,
                RwkvWorkloadTargetConfig {
                    dr,
                    days_to_simulate,
                    review_limit,
                    new_limit,
                    new_cards_ignore_review_limit,
                    max_interval,
                    review_order,
                    suspend_after_lapses,
                    state_update_interval,
                    review_model: &review_model,
                },
            )
        };
        // Every target copies the streams it advances, at most all of the
        // inputs' streams. Sampled inputs make those copies small, so run
        // several targets at once while bounding the memory of the concurrent
        // copies.
        let concurrent_targets = (WORKLOAD_TARGET_STATE_BUDGET_BYTES / target_state_len.max(1))
            .clamp(1, rayon::current_num_threads())
            .min(target_drs.len().max(1));
        // Higher targets review more often and take longest, so start them
        // first, and let each worker pull the next target as soon as it is
        // free instead of waiting for the slowest target of a fixed batch.
        let mut queue = target_drs.iter().copied().enumerate().collect::<Vec<_>>();
        queue.sort_by_key(|(_, dr)| std::cmp::Reverse(*dr));
        let next_target = AtomicUsize::new(0);
        let stopped = AtomicBool::new(false);
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut points = (0..target_drs.len()).map(|_| None).collect::<Vec<_>>();
        std::thread::scope(|scope| {
            let (queue, next_target, stopped) = (&queue, &next_target, &stopped);
            let simulate_target = &simulate_target;
            scope.spawn(move || {
                rayon::scope(|workers| {
                    for _ in 0..concurrent_targets {
                        let sender = sender.clone();
                        workers.spawn(move |_| {
                            while !stopped.load(Ordering::Relaxed) {
                                let Some(&(index, dr)) =
                                    queue.get(next_target.fetch_add(1, Ordering::Relaxed))
                                else {
                                    break;
                                };
                                let result = simulate_target(dr);
                                if sender.send((index, result)).is_err() {
                                    break;
                                }
                            }
                        });
                    }
                });
            });
            let mut completed = 0;
            for (index, result) in receiver {
                let step = result.and_then(|point| {
                    points[index] = Some(point);
                    completed += 1;
                    progress(completed + 1, total_steps)
                });
                if let Err(err) = step {
                    stopped.store(true, Ordering::Relaxed);
                    return Err(err);
                }
            }
            Ok(())
        })?;
        Ok(target_drs
            .into_iter()
            .zip(points)
            .map(|(dr, point)| (dr, point.expect("every workload target completed")))
            .collect())
    }

    fn workload_worker(
        &self,
        features: FeatureState,
        curves: HashMap<i64, ReviewCurve>,
    ) -> RwkvInference {
        RwkvInference {
            #[cfg(test)]
            captured_curves: None,
            model: Arc::clone(&self.model),
            answer_time_head: self.answer_time_head.clone(),
            features,
            curves,
            warm_up_states: ReviewStateMaps::default(),
            stashed_warm_up_state: None,
            state_cache_store: None,
            target_retention: self.target_retention,
            max_interval_days: self.max_interval_days,
        }
    }

    fn simulate_workload_for_target(
        &mut self,
        inputs: &[RwkvWorkloadSimulationInput],
        states: &mut SimulationStates<'_>,
        config: RwkvWorkloadTargetConfig<'_>,
    ) -> io::Result<RwkvWorkloadSimulationPoint> {
        let target_retention = config.dr as f32 / 100.0;
        let query_inputs: Vec<_> = inputs
            .iter()
            .map(|input| simulation_query_input(input, target_retention, 0))
            .collect();
        let predictions = self.workload_query_predictions_for_inputs(&query_inputs, states);
        let mut cards = inputs
            .iter()
            .zip(predictions.iter())
            .map(|(input, prediction)| simulation_card(input, prediction, target_retention))
            .collect::<Vec<_>>();

        let mut total_cost = 0.0;
        let mut review_count: u32 = 0;
        for day in 0..config.days_to_simulate {
            let mut due_review_indexes: Vec<_> = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (!card.is_new && !card.suspended && card.due_day <= day).then_some(index)
                })
                .collect();
            let order_inputs = due_review_indexes
                .iter()
                .map(|index| simulation_query_input_for_card(&cards[*index], target_retention, day))
                .collect::<Vec<_>>();
            let order_predictions =
                self.workload_query_predictions_for_inputs(&order_inputs, states);
            let prediction_by_index = due_review_indexes
                .iter()
                .copied()
                .zip(order_predictions)
                .collect::<HashMap<_, _>>();
            due_review_indexes.sort_by_key(|index| {
                simulation_review_priority(
                    &cards[*index],
                    prediction_by_index.get(index),
                    config.review_order,
                    target_retention,
                )
            });
            if config.review_limit > 0 {
                due_review_indexes.truncate(config.review_limit);
            } else {
                due_review_indexes.clear();
            }

            let mut due_new_indexes = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (card.is_new && !card.suspended && card.due_day <= day).then_some(index)
                })
                .collect::<Vec<_>>();
            due_new_indexes.sort_by_key(|index| cards[*index].review_input.review_input.card_id);
            due_new_indexes.truncate(config.new_limit);
            if !config.new_cards_ignore_review_limit {
                due_new_indexes
                    .truncate(config.review_limit.saturating_sub(due_review_indexes.len()));
            }
            let mut due_indexes = due_review_indexes;
            due_indexes.extend(due_new_indexes);

            let mut offset = 0;
            while offset < due_indexes.len() {
                let reviews_until_state_update =
                    config.state_update_interval - (review_count % config.state_update_interval);
                let chunk_len =
                    (reviews_until_state_update as usize).min(due_indexes.len() - offset);
                let chunk_indexes = &due_indexes[offset..offset + chunk_len];
                let query_inputs = chunk_indexes
                    .iter()
                    .map(|index| {
                        simulation_query_input_for_card(&cards[*index], target_retention, day)
                    })
                    .collect::<Vec<_>>();
                let predictions = self.workload_query_predictions_for_inputs(&query_inputs, states);
                let eases = chunk_indexes
                    .iter()
                    .zip(&predictions)
                    .map(|(index, prediction)| {
                        let retrievability = valid_probability_or_default(
                            prediction.retrievability,
                            target_retention,
                        );
                        let probabilities = config.review_model.probabilities_for(retrievability);
                        simulation_grade(
                            probabilities,
                            cards[*index].review_input.review_input.card_id,
                            day,
                            cards[*index].reps,
                        )
                    })
                    .collect::<Vec<_>>();
                let intervals =
                    self.selected_answer_intervals_for_inputs(&query_inputs, states, &eases);

                for ((position, index), ease) in chunk_indexes.iter().enumerate().zip(&eases) {
                    let grade_seconds = config.review_model.grade_seconds[(*ease - 1) as usize];
                    total_cost += grade_seconds;
                    review_count += 1;

                    let duration_millis = (grade_seconds * 1000.0).round().max(1.0) as i64;
                    let answer_input = simulation_answer_input(
                        &cards[*index],
                        target_retention,
                        day,
                        *ease,
                        duration_millis,
                    );
                    let interval = intervals[position]
                        .unwrap_or(1)
                        .clamp(1, config.max_interval);
                    if review_count.is_multiple_of(config.state_update_interval) {
                        self.apply_simulation_answer(&answer_input, states)?;
                    }

                    cards[*index].last_review_day = day;
                    cards[*index].due_day = day + interval as i64;
                    cards[*index].interval_days = interval as i64;
                    cards[*index].reps += 1;
                    if cards[*index].is_new {
                        cards[*index].is_new = false;
                        cards[*index].review_input.review_input.card_type =
                            Some(CardType::Review as i64);
                    }
                    if *ease == 1 {
                        cards[*index].lapses += 1;
                        if config
                            .suspend_after_lapses
                            .is_some_and(|threshold| cards[*index].lapses >= threshold as i64)
                        {
                            cards[*index].suspended = true;
                        }
                    }
                }
                offset += chunk_len;
            }
        }

        let (memorized, weighted_memorized) = self.simulation_memorized_for_cards(
            &cards,
            states,
            target_retention,
            config.days_to_simulate,
        )?;
        Ok(RwkvWorkloadSimulationPoint {
            memorized,
            weighted_memorized,
            cost: total_cost,
            review_count,
        })
    }

    fn workload_query_predictions_for_inputs(
        &mut self,
        inputs: &[ReviewInput],
        states: &SimulationStates<'_>,
    ) -> Vec<RwkvWorkloadQueryPrediction> {
        let features = inputs
            .iter()
            .map(|input| self.features.features_for(input))
            .collect::<Vec<_>>();
        self.review_in_chunks(inputs, &features, states, |index, heads| {
            let (current_interval, current_s90) = self.current_intervals(&inputs[index], &heads);
            RwkvWorkloadQueryPrediction {
                retrievability: heads.retrievability,
                current_interval,
                current_s90,
            }
        })
    }

    /// The model's review of each of `inputs` (with its `features`) from
    /// `states`, [STATE_VIEW_CHUNK] at a time;
    /// `map` gets the index into `inputs`.
    fn review_in_chunks<T: Send>(
        &self,
        inputs: &[ReviewInput],
        features: &[Vec<f32>],
        states: &SimulationStates<'_>,
        map: impl Fn(usize, ReviewHeads) -> T + Sync,
    ) -> Vec<T> {
        let mut out = Vec::with_capacity(inputs.len());
        for (chunk, (inputs, features)) in inputs
            .chunks(STATE_VIEW_CHUNK)
            .zip(features.chunks(STATE_VIEW_CHUNK))
            .enumerate()
        {
            let views = inputs
                .iter()
                .map(|input| states.state_view(input))
                .collect::<Vec<_>>();
            let work_items = features
                .iter()
                .zip(&views)
                .map(|(features, view)| ReviewPredictionBorrowedWorkItem {
                    features: features.clone(),
                    state: view.as_ref(),
                })
                .collect::<Vec<_>>();
            let offset = chunk * STATE_VIEW_CHUNK;
            out.extend(
                self.model
                    .review_many_borrowed(&work_items, |index, heads| map(offset + index, heads)),
            );
        }
        out
    }

    fn selected_answer_intervals_for_inputs(
        &mut self,
        inputs: &[ReviewInput],
        states: &SimulationStates<'_>,
        eases: &[u8],
    ) -> Vec<Option<u32>> {
        debug_assert_eq!(inputs.len(), eases.len());
        let answer_inputs = inputs
            .iter()
            .zip(eases)
            .flat_map(|(input, ease)| {
                if input.enforce_grade_order {
                    ANSWER_EASES.to_vec()
                } else {
                    vec![*ease]
                }
                .into_iter()
                .map(|ease| simulated_answer_input(input, ease))
            })
            .collect::<Vec<_>>();
        let features = answer_inputs
            .iter()
            .map(|input| self.features.features_for(input))
            .collect::<Vec<_>>();
        let (default_retention, max_interval_days) =
            (self.target_retention, self.max_interval_days);
        let curves = self.review_in_chunks(&answer_inputs, &features, states, |_, heads| heads.curve);
        let mut next = 0;
        inputs
            .iter()
            .zip(eases)
            .map(|(input, ease)| {
                let grade = (*ease - 1) as usize;
                if input.enforce_grade_order {
                    let answer_curves = std::array::from_fn(|index| &curves[next + index]);
                    next += ANSWER_EASES.len();
                    let targets = input
                        .target_retentions
                        .map(|target| target.unwrap_or(default_retention));
                    unrounded_intervals_for_answer_curves(
                        answer_curves,
                        targets,
                        max_interval_days,
                        true,
                    )[grade]
                        .map(|days| clamped_interval_days(days, max_interval_days))
                } else {
                    let curve = &curves[next];
                    next += 1;
                    interval_for_curve(
                        curve,
                        input.target_retentions[grade].unwrap_or(default_retention),
                        max_interval_days,
                    )
                }
            })
            .collect()
    }

    fn apply_simulation_answer(
        &mut self,
        input: &ReviewInput,
        states: &mut SimulationStates<'_>,
    ) -> io::Result<()> {
        let heads = self.review_heads_for_state(input, states.state_view(input).as_ref())?;
        self.features.store_review(input);
        states.store(input, heads.next_state)
    }

    fn simulation_memorized_for_inputs(
        &mut self,
        inputs: &[RwkvWorkloadSimulationInput],
        states: &SimulationStates<'_>,
        target_retention: f32,
        day: i64,
    ) -> io::Result<(f32, f32)> {
        let query_inputs = inputs
            .iter()
            .map(|input| simulation_query_input(input, target_retention, day))
            .collect::<Vec<_>>();
        let predictions = self.workload_query_predictions_for_inputs(&query_inputs, states);
        Ok(memorized_from_workload_predictions(&predictions))
    }

    fn simulation_memorized_for_cards(
        &mut self,
        cards: &[RwkvSimulationCard],
        states: &SimulationStates<'_>,
        target_retention: f32,
        day: i64,
    ) -> io::Result<(f32, f32)> {
        let query_inputs = cards
            .iter()
            .filter(|card| !card.is_new)
            .map(|card| simulation_query_input_for_card(card, target_retention, day))
            .collect::<Vec<_>>();
        let predictions = self.workload_query_predictions_for_inputs(&query_inputs, states);
        Ok(memorized_from_workload_predictions(&predictions))
    }
}

fn same_feature_identity(left: &ReviewInput, right: &ReviewInput) -> bool {
    left.card_id == right.card_id
        && left.note_id == right.note_id
        && left.deck_id == right.deck_id
        && left.preset_id == right.preset_id
}

fn check_workload_dr_range(config: &RwkvWorkloadSimulationConfig) -> io::Result<()> {
    if config.min_dr > config.max_dr {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "RWKV workload min DR must be <= max DR",
        ));
    }
    Ok(())
}

fn predict_retrievability_many_after_reviews_for_identity(
    model: &SrsModel,
    features: &mut FeatureState,
    state_maps: &ReviewStateMaps,
    answers: Vec<ReviewInput>,
    query_inputs: &[ReviewInput],
) -> Vec<Vec<f32>> {
    let answer_work_items = answers
        .iter()
        .map(|answer| ReviewPredictionWorkItem {
            features: features.features_for(answer),
            state: state_maps.state_owned(answer),
        })
        .collect::<Vec<_>>();
    let answer_heads = model.review_many(&answer_work_items);

    let query_count = query_inputs.len();
    let mut query_work_items = Vec::with_capacity(answers.len() * query_count);
    for (answer, heads) in answers.into_iter().zip(answer_heads) {
        let feature_state = features.state_for_review(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
        );
        features.store_review(&answer);
        for query_input in query_inputs {
            query_work_items.push(ReviewPredictionWorkItem {
                features: features.features_for(query_input),
                state: state_maps.state_owned_after_review(&answer, query_input, &heads.next_state),
            });
        }
        features.restore_state(&feature_state);
    }

    model
        .review_many(&query_work_items)
        .chunks_exact(query_count)
        .map(|heads| heads.iter().map(|heads| heads.retrievability).collect())
        .collect()
}

fn read_runtime_feature_state(bytes: &[u8]) -> io::Result<FeatureState> {
    let mut cursor = Cursor::new(bytes);
    cursor.expect_magic(b"ARWKVPROCSTATE4")?;
    if cursor.u8()? != u8::from(SMALL_MODEL) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "RWKV cache belongs to a different model",
        ));
    }
    let features = FeatureState::read_cache_state(&mut cursor)?;
    let curve_count = cursor.u32()? as usize;
    for _ in 0..curve_count {
        cursor.i64()?;
        let power = cursor.u8()?;
        if power > 1 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid curve head",
            ));
        }
        cursor.skip_f32_vec()?;
        cursor.skip_f32_vec()?;
        if power == 1 {
            cursor.skip_f32_vec()?;
            cursor.skip_f32_vec()?;
        }
    }
    cursor.expect_end()?;
    Ok(features)
}

fn read_runtime_cache_state(bytes: &[u8]) -> io::Result<(FeatureState, HashMap<i64, ReviewCurve>)> {
    let mut cursor = Cursor::new(bytes);
    cursor.expect_magic(b"ARWKVPROCSTATE4")?;
    if cursor.u8()? != u8::from(SMALL_MODEL) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "RWKV cache belongs to a different model",
        ));
    }
    let features = FeatureState::read_cache_state(&mut cursor)?;
    let curve_count = cursor.count(16)?; // card id + two vector lengths
    let mut curves = HashMap::with_capacity(curve_count);
    for _ in 0..curve_count {
        let card_id = cursor.i64()?;
        let curve = ReviewCurve::read_cache_state(&mut cursor)?;
        curves.insert(card_id, curve);
    }
    cursor.expect_end()?;
    Ok((features, curves))
}

fn simulated_answer_input(input: &ReviewInput, ease: u8) -> ReviewInput {
    let mut input = input.clone();
    input.is_query = false;
    input.ease = Some(ease);
    input
}

fn target_drs(min_dr: u32, max_dr: u32, step: u32) -> Vec<u32> {
    let mut values = Vec::new();
    let mut dr = min_dr;
    while dr <= max_dr {
        values.push(dr);
        match dr.checked_add(step) {
            Some(next) => dr = next,
            None => break,
        }
    }
    if values.last().copied() != Some(max_dr) {
        values.push(max_dr);
    }
    values
}

fn simulation_card(
    input: &RwkvWorkloadSimulationInput,
    prediction: &RwkvWorkloadQueryPrediction,
    target_retention: f32,
) -> RwkvSimulationCard {
    let elapsed_days = input_elapsed_days(&input.review_input);
    let mut due_day = 0;
    match prediction.current_interval {
        Some(current_interval)
            if prediction.retrievability.is_finite()
                && prediction.retrievability > target_retention =>
        {
            due_day = (current_interval as i64 - elapsed_days).max(1);
        }
        _ => {}
    }
    RwkvSimulationCard {
        review_input: input.clone(),
        due_day,
        last_review_day: -elapsed_days,
        reps: input.reps.unwrap_or(0),
        lapses: input.lapses.unwrap_or(0),
        interval_days: input.interval_days.unwrap_or(1).max(1),
        ease_factor: input.ease_factor.unwrap_or(0),
        is_new: input.review_input.card_type == Some(CardType::New as i64),
        suspended: false,
    }
}

fn simulation_review_priority(
    card: &RwkvSimulationCard,
    prediction: Option<&RwkvWorkloadQueryPrediction>,
    review_order: ReviewCardOrder,
    target_retention: f32,
) -> (i64, i64) {
    let card_id = card.review_input.review_input.card_id;
    let retrievability = valid_probability_or_default(
        prediction
            .map(|prediction| prediction.retrievability)
            .unwrap_or(target_retention),
        target_retention,
    );
    let scaled_retrievability = (retrievability * 1_000_000.0).round() as i64;
    let priority = match review_order {
        ReviewCardOrder::IntervalsAscending => card.interval_days,
        ReviewCardOrder::IntervalsDescending => -card.interval_days,
        ReviewCardOrder::EaseAscending => card.ease_factor,
        ReviewCardOrder::EaseDescending => -card.ease_factor,
        ReviewCardOrder::RetrievabilityAscending => scaled_retrievability,
        ReviewCardOrder::RetrievabilityDescending => -scaled_retrievability,
        ReviewCardOrder::RelativeOverdueness => {
            (relative_overdueness(retrievability, target_retention) * 1_000_000.0).round() as i64
        }
        ReviewCardOrder::Random => (simulation_unit_hash(card_id, 0, 0) * i64::MAX as f64) as i64,
        ReviewCardOrder::Added => card_id,
        ReviewCardOrder::ReverseAdded => -card_id,
        ReviewCardOrder::Day | ReviewCardOrder::DayThenDeck | ReviewCardOrder::DeckThenDay => {
            card.due_day
        }
    };
    (priority, card_id)
}

fn simulation_query_input(
    input: &RwkvWorkloadSimulationInput,
    target_retention: f32,
    day: i64,
) -> ReviewInput {
    let elapsed_days = input_elapsed_days(&input.review_input) + day;
    simulation_input(
        &input.review_input,
        target_retention,
        day,
        elapsed_days,
        true,
        None,
        None,
    )
}

fn simulation_query_input_for_card(
    card: &RwkvSimulationCard,
    target_retention: f32,
    day: i64,
) -> ReviewInput {
    let elapsed_days = (day - card.last_review_day).max(0);
    simulation_input(
        &card.review_input.review_input,
        target_retention,
        day,
        elapsed_days,
        true,
        None,
        None,
    )
}

fn simulation_answer_input(
    card: &RwkvSimulationCard,
    target_retention: f32,
    day: i64,
    ease: u8,
    duration_millis: i64,
) -> ReviewInput {
    let elapsed_days = (day - card.last_review_day).max(0);
    simulation_input(
        &card.review_input.review_input,
        target_retention,
        day,
        elapsed_days,
        false,
        Some(ease),
        Some(duration_millis),
    )
}

fn simulation_input(
    input: &ReviewInput,
    target_retention: f32,
    day: i64,
    elapsed_days: i64,
    is_query: bool,
    ease: Option<u8>,
    duration_millis: Option<i64>,
) -> ReviewInput {
    let mut input = input.clone();
    input.is_query = is_query;
    input.ease = ease;
    input.duration_millis = duration_millis;
    input.day_offset = input.day_offset.map(|day_offset| day_offset + day);
    input.current_elapsed_days = Some(elapsed_days);
    input.current_elapsed_seconds = None;
    input.target_retentions = [
        Some(target_retention),
        Some(target_retention),
        Some(target_retention),
        Some(target_retention),
    ];
    input
}

fn input_elapsed_days(input: &ReviewInput) -> i64 {
    input.current_elapsed_days.unwrap_or(0).max(0)
}

fn memorized_from_workload_predictions(predictions: &[RwkvWorkloadQueryPrediction]) -> (f32, f32) {
    let mut memorized = 0.0;
    let mut weighted = 0.0;
    for prediction in predictions {
        if !valid_probability(prediction.retrievability) {
            continue;
        }
        memorized += prediction.retrievability;
        weighted += prediction.retrievability * s90_weight(prediction.current_s90);
    }
    (memorized, weighted)
}

fn s90_weight(current_s90: Option<f32>) -> f32 {
    let Some(current_s90) = current_s90 else {
        return 1.0;
    };
    if current_s90 <= 0.0 {
        return 1.0;
    }
    1.0 - ((-8.0 / 365.0) * current_s90).exp()
}

fn valid_probability(value: f32) -> bool {
    value.is_finite() && (0.0..=1.0).contains(&value)
}

fn valid_probability_or_default(value: f32, default: f32) -> f32 {
    if valid_probability(value) {
        value
    } else {
        default
    }
}

trait WorkloadReviewProbabilities {
    fn probabilities_for(&self, retrievability: f32) -> [f32; 4];
}

impl WorkloadReviewProbabilities for RwkvWorkloadReviewModel {
    fn probabilities_for(&self, retrievability: f32) -> [f32; 4] {
        let bucket = simulator_bucket(retrievability);
        self.bucket_probabilities
            .iter()
            .find_map(|(candidate, probabilities)| (*candidate == bucket).then_some(*probabilities))
            .unwrap_or_else(|| fallback_grade_probabilities(retrievability))
    }
}

fn fallback_grade_probabilities(retrievability: f32) -> [f32; 4] {
    let r = if retrievability.is_finite() {
        retrievability.clamp(0.0, 1.0)
    } else {
        0.9
    };
    let again = (1.0 - r).clamp(0.02, 0.85);
    let success = 1.0 - again;
    let mut hard_share = ((0.95 - r) / 0.45).clamp(0.10, 0.45);
    let mut easy_share = ((r - 0.75) / 0.25).clamp(0.05, 0.35);
    if hard_share + easy_share > 0.90 {
        let scale = 0.90 / (hard_share + easy_share);
        hard_share *= scale;
        easy_share *= scale;
    }
    let hard = success * hard_share;
    let easy = success * easy_share;
    let good = (success - hard - easy).max(0.0);
    let total = again + hard + good + easy;
    [again / total, hard / total, good / total, easy / total]
}

fn simulation_grade(probabilities: [f32; 4], card_id: i64, day: i64, reps: i64) -> u8 {
    let threshold = simulation_unit_hash(card_id, day, reps);
    let mut cumulative = 0.0;
    for (index, probability) in probabilities.iter().enumerate() {
        cumulative += *probability as f64;
        if threshold <= cumulative {
            return (index + 1) as u8;
        }
    }
    4
}

fn simulation_unit_hash(card_id: i64, day: i64, reps: i64) -> f64 {
    let mut value =
        (card_id as u64) ^ ((day as u64).wrapping_add(1)).wrapping_mul(0x9E3779B185EBCA87);
    value ^= ((reps as u64).wrapping_add(1)).wrapping_mul(0x165667B19E3779F9);
    value ^= value >> 33;
    value = value.wrapping_mul(0xFF51AFD7ED558CCD);
    value ^= value >> 33;
    value = value.wrapping_mul(0xC4CEB9FE1A85EC53);
    value ^= value >> 33;
    value as f64 / u64::MAX as f64
}

fn simulator_bucket(retrievability: f32) -> u32 {
    const BUCKET_COUNT: u32 = 20;
    if !retrievability.is_finite() {
        return BUCKET_COUNT - 1;
    }
    ((retrievability * BUCKET_COUNT as f32) as u32).clamp(0, BUCKET_COUNT - 1)
}

trait DeserializeReviewState {
    fn deserialize(&self) -> io::Result<SrsStateOwned>;
}

impl DeserializeReviewState for ReviewStateOwned {
    fn deserialize(&self) -> io::Result<SrsStateOwned> {
        Ok(SrsStateOwned {
            card: deserialize_module_state(self.card.as_deref())?,
            deck: deserialize_module_state(self.deck.as_deref())?,
            note: deserialize_module_state(self.note.as_deref())?,
            preset: deserialize_module_state(self.preset.as_deref())?,
            global: deserialize_module_state(self.global.as_deref())?,
        })
    }
}

#[derive(Clone)]
struct FeatureState {
    first_day_offset: Option<i64>,
    previous_day_offset: Option<i64>,
    card_set: HashMap<i64, ()>,
    last_new_cards: HashMap<i64, i64>,
    last_i: HashMap<i64, i64>,
    today: i64,
    today_reviews: i64,
    today_new_cards: i64,
    card_first_day_offset: HashMap<i64, i64>,
    card_elapsed_days_cumulative: HashMap<i64, i64>,
    card_elapsed_seconds_cumulative: HashMap<i64, i64>,
    id_encodings: HashMap<(IdKind, i64), Vec<f32>>,
    id_rng: TorchIdRng,
    review_index: i64,
}

impl Default for FeatureState {
    fn default() -> Self {
        Self {
            first_day_offset: None,
            previous_day_offset: None,
            card_set: HashMap::new(),
            last_new_cards: HashMap::new(),
            last_i: HashMap::new(),
            today: -1,
            today_reviews: 0,
            today_new_cards: 0,
            card_first_day_offset: HashMap::new(),
            card_elapsed_days_cumulative: HashMap::new(),
            card_elapsed_seconds_cumulative: HashMap::new(),
            id_encodings: HashMap::new(),
            id_rng: TorchIdRng::default(),
            review_index: 0,
        }
    }
}

#[derive(Clone)]
struct FeatureStateForCard {
    card_id: i64,
    first_day_offset: Option<i64>,
    previous_day_offset: Option<i64>,
    card_was_seen: bool,
    last_new_cards: Option<i64>,
    last_i: Option<i64>,
    today: i64,
    today_reviews: i64,
    today_new_cards: i64,
    card_first_day_offset: Option<i64>,
    card_elapsed_days_cumulative: Option<i64>,
    card_elapsed_seconds_cumulative: Option<i64>,
    review_index: i64,
    id_encodings: Option<IdEncodingSnapshot>,
}

#[derive(Clone)]
struct IdEncodingSnapshot {
    id_rng: TorchIdRng,
    encodings: [IdEncodingSnapshotEntry; 4],
}

#[derive(Clone)]
struct IdEncodingSnapshotEntry {
    key: (IdKind, i64),
    encoding: Option<Vec<f32>>,
}

impl FeatureState {
    fn state_for_card(&self, card_id: i64) -> FeatureStateForCard {
        FeatureStateForCard {
            card_id,
            first_day_offset: self.first_day_offset,
            previous_day_offset: self.previous_day_offset,
            card_was_seen: self.card_set.contains_key(&card_id),
            last_new_cards: self.last_new_cards.get(&card_id).copied(),
            last_i: self.last_i.get(&card_id).copied(),
            today: self.today,
            today_reviews: self.today_reviews,
            today_new_cards: self.today_new_cards,
            card_first_day_offset: self.card_first_day_offset.get(&card_id).copied(),
            card_elapsed_days_cumulative: self.card_elapsed_days_cumulative.get(&card_id).copied(),
            card_elapsed_seconds_cumulative: self
                .card_elapsed_seconds_cumulative
                .get(&card_id)
                .copied(),
            review_index: self.review_index,
            id_encodings: None,
        }
    }

    fn state_for_review(
        &self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
    ) -> FeatureStateForCard {
        FeatureStateForCard {
            id_encodings: Some(IdEncodingSnapshot {
                id_rng: self.id_rng.clone(),
                encodings: id_encoding_keys(card_id, note_id, deck_id, preset_id).map(|key| {
                    IdEncodingSnapshotEntry {
                        key,
                        encoding: self.id_encodings.get(&key).cloned(),
                    }
                }),
            }),
            ..self.state_for_card(card_id)
        }
    }

    fn restore_state(&mut self, state: &FeatureStateForCard) {
        self.first_day_offset = state.first_day_offset;
        self.previous_day_offset = state.previous_day_offset;
        self.today = state.today;
        self.today_reviews = state.today_reviews;
        self.today_new_cards = state.today_new_cards;
        self.review_index = state.review_index;

        if state.card_was_seen {
            self.card_set.insert(state.card_id, ());
        } else {
            self.card_set.remove(&state.card_id);
        }
        restore_map_entry(
            &mut self.last_new_cards,
            state.card_id,
            state.last_new_cards,
        );
        restore_map_entry(&mut self.last_i, state.card_id, state.last_i);
        restore_map_entry(
            &mut self.card_first_day_offset,
            state.card_id,
            state.card_first_day_offset,
        );
        restore_map_entry(
            &mut self.card_elapsed_days_cumulative,
            state.card_id,
            state.card_elapsed_days_cumulative,
        );
        restore_map_entry(
            &mut self.card_elapsed_seconds_cumulative,
            state.card_id,
            state.card_elapsed_seconds_cumulative,
        );
        if let Some(snapshot) = &state.id_encodings {
            self.id_rng = snapshot.id_rng.clone();
            for entry in &snapshot.encodings {
                if let Some(encoding) = &entry.encoding {
                    self.id_encodings.insert(entry.key, encoding.clone());
                } else {
                    self.id_encodings.remove(&entry.key);
                }
            }
        }
    }

    fn features_for(&mut self, input: &ReviewInput) -> Vec<f32> {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();

        let elapsed_seconds = elapsed_seconds(input);
        let elapsed_days = elapsed_days(input, elapsed_seconds);
        let elapsed_days_cumulative = self
            .card_elapsed_days_cumulative
            .get(&input.card_id)
            .copied()
            .unwrap_or(0)
            + elapsed_days;
        let elapsed_seconds_cumulative = self
            .card_elapsed_seconds_cumulative
            .get(&input.card_id)
            .copied()
            .unwrap_or(0)
            + elapsed_seconds;

        let raw_day_offset = input.day_offset.unwrap_or(0);
        let day_offset = self
            .first_day_offset
            .map_or(0, |first_day_offset| raw_day_offset - first_day_offset);
        let day_offset_first = self
            .card_first_day_offset
            .get(&input.card_id)
            .copied()
            .unwrap_or(day_offset);
        let previous_day_offset = self.previous_day_offset.unwrap_or(0);
        let diff_new_cards = self
            .last_new_cards
            .get(&input.card_id)
            .map_or(0, |last_new_cards| {
                self.card_set.len() as i64 - last_new_cards
            });
        let diff_reviews = self
            .last_i
            .get(&input.card_id)
            .map_or(0, |last_i| (self.review_index - last_i - 1).max(0));

        let mut today_new_cards = self.today_new_cards;
        let mut today_reviews = self.today_reviews;
        if day_offset != self.today {
            today_new_cards = 0;
            today_reviews = -1;
        }
        today_reviews += 1;
        if !self.card_set.contains_key(&input.card_id) {
            today_new_cards += 1;
        }

        let mut features = Vec::with_capacity(CARD_FEATURES);
        features.extend([
            scale_elapsed_days(elapsed_days),
            scale_elapsed_days_cumulative(elapsed_days_cumulative),
            scale_elapsed_seconds(elapsed_seconds),
            cyclic_sin(elapsed_seconds, SECONDS_PER_DAY),
            cyclic_cos(elapsed_seconds, SECONDS_PER_DAY),
            scale_elapsed_seconds_cumulative(elapsed_seconds_cumulative),
            cyclic_sin(elapsed_seconds_cumulative, SECONDS_PER_DAY),
            cyclic_cos(elapsed_seconds_cumulative, SECONDS_PER_DAY),
            scaled_duration(input),
        ]);

        let rating = input.ease.unwrap_or(0);
        for ease in 1..=4 {
            features.push(if !input.is_query && rating == ease {
                1.0
            } else {
                0.0
            });
        }

        features.extend([
            if input.note_id.is_none() { 1.0 } else { 0.0 },
            if input.deck_id.is_none() { 1.0 } else { 0.0 },
            if input.preset_id.is_none() { 1.0 } else { 0.0 },
            scale_day_offset_diff(day_offset - previous_day_offset),
            ((day_offset.rem_euclid(7) as f32) - 3.0) / 3.0,
            scale_diff_new_cards(diff_new_cards),
            scale_diff_reviews(diff_reviews),
            scale_cum_new_cards_today(today_new_cards),
            scale_cum_reviews_today(today_reviews),
            if input.is_query {
                0.0
            } else {
                scale_state(input.card_type.unwrap_or(0))
            },
            if input.is_query { 1.0 } else { 0.0 },
        ]);

        // Preview unseen IDs using the answer's allocation order, without
        // reserving encodings or advancing the committed RNG.
        let mut preview_id_rng = None;
        for key in id_encoding_keys(input.card_id, input.note_id, input.deck_id, input.preset_id) {
            if let Some(encoding) = self.id_encodings.get(&key) {
                features.extend_from_slice(encoding);
            } else {
                features.extend(
                    preview_id_rng
                        .get_or_insert_with(|| self.id_rng.clone())
                        .encoding(id_encoding_dim(key.0)),
                );
            }
        }
        append_day_offset_encoding(&mut features, day_offset, day_offset_first);
        debug_assert_eq!(features.len(), CARD_FEATURES);
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::FeaturesFor, profile_started);
        features
    }

    fn store_review(&mut self, input: &ReviewInput) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();

        self.assign_id_encodings(input);

        let elapsed_seconds = elapsed_seconds(input);
        let elapsed_days = elapsed_days(input, elapsed_seconds);
        *self
            .card_elapsed_days_cumulative
            .entry(input.card_id)
            .or_default() += elapsed_days;
        *self
            .card_elapsed_seconds_cumulative
            .entry(input.card_id)
            .or_default() += elapsed_seconds;

        let raw_day_offset = input.day_offset.unwrap_or(0);
        let day_offset = self
            .first_day_offset
            .map_or(0, |first_day_offset| raw_day_offset - first_day_offset);
        if self.first_day_offset.is_none() {
            self.first_day_offset = Some(raw_day_offset);
        }

        if day_offset != self.today {
            self.today = day_offset;
            self.today_new_cards = 0;
            self.today_reviews = -1;
        }
        self.today_reviews += 1;
        if !self.card_set.contains_key(&input.card_id) {
            self.today_new_cards += 1;
            self.card_set.insert(input.card_id, ());
            self.card_first_day_offset.insert(input.card_id, day_offset);
        }

        self.previous_day_offset = Some(day_offset);
        self.last_i.insert(input.card_id, self.review_index);
        self.last_new_cards
            .insert(input.card_id, self.card_set.len() as i64);
        self.review_index += 1;

        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::FeaturesStore, profile_started);
    }

    fn assign_id_encodings(&mut self, input: &ReviewInput) {
        for key in id_encoding_keys(input.card_id, input.note_id, input.deck_id, input.preset_id) {
            self.id_encodings
                .entry(key)
                .or_insert_with(|| self.id_rng.encoding(id_encoding_dim(key.0)));
        }
    }

    #[cfg(test)]
    fn write_cache_state(&self, out: &mut Vec<u8>) {
        self.write_cache_state_to(out)
            .expect("writing to a Vec should not fail");
    }

    fn cache_state_len(&self) -> usize {
        state_cache_optional_i64_len(self.first_day_offset)
            + state_cache_optional_i64_len(self.previous_day_offset)
            + state_cache_i64_set_len(self.card_set.len())
            + state_cache_i64_map_len(self.last_new_cards.len())
            + state_cache_i64_map_len(self.last_i.len())
            + 3 * 8
            + state_cache_i64_map_len(self.card_first_day_offset.len())
            + state_cache_i64_map_len(self.card_elapsed_days_cumulative.len())
            + state_cache_i64_map_len(self.card_elapsed_seconds_cumulative.len())
            + 8
            + state_cache_id_encodings_len(&self.id_encodings)
            + self.id_rng.cache_state_len()
    }

    fn write_cache_state_to(&self, out: &mut impl io::Write) -> io::Result<()> {
        write_state_cache_optional_i64(out, self.first_day_offset)?;
        write_state_cache_optional_i64(out, self.previous_day_offset)?;
        write_state_cache_i64_set(out, self.card_set.keys().copied())?;
        write_state_cache_i64_map(out, &self.last_new_cards)?;
        write_state_cache_i64_map(out, &self.last_i)?;
        out.write_all(&self.today.to_le_bytes())?;
        out.write_all(&self.today_reviews.to_le_bytes())?;
        out.write_all(&self.today_new_cards.to_le_bytes())?;
        write_state_cache_i64_map(out, &self.card_first_day_offset)?;
        write_state_cache_i64_map(out, &self.card_elapsed_days_cumulative)?;
        write_state_cache_i64_map(out, &self.card_elapsed_seconds_cumulative)?;
        out.write_all(&self.review_index.to_le_bytes())?;
        write_state_cache_id_encodings(out, &self.id_encodings)?;
        self.id_rng.write_cache_state_to(out)
    }

    fn read_cache_state(cursor: &mut Cursor<'_>) -> io::Result<Self> {
        Ok(Self {
            first_day_offset: cursor.option_i64()?,
            previous_day_offset: cursor.option_i64()?,
            card_set: read_i64_set(cursor)?,
            last_new_cards: read_i64_map(cursor)?,
            last_i: read_i64_map(cursor)?,
            today: cursor.i64()?,
            today_reviews: cursor.i64()?,
            today_new_cards: cursor.i64()?,
            card_first_day_offset: read_i64_map(cursor)?,
            card_elapsed_days_cumulative: read_i64_map(cursor)?,
            card_elapsed_seconds_cumulative: read_i64_map(cursor)?,
            review_index: cursor.i64()?,
            id_encodings: read_id_encodings(cursor)?,
            id_rng: TorchIdRng::read_cache_state(cursor)?,
        })
    }
}

fn restore_map_entry(map: &mut HashMap<i64, i64>, key: i64, value: Option<i64>) {
    if let Some(value) = value {
        map.insert(key, value);
    } else {
        map.remove(&key);
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
enum IdKind {
    Card,
    Note,
    Deck,
    Preset,
}

fn id_encoding_keys(
    card_id: i64,
    note_id: Option<i64>,
    deck_id: Option<i64>,
    preset_id: Option<i64>,
) -> [(IdKind, i64); 4] {
    [
        (IdKind::Card, card_id),
        (IdKind::Note, note_id.unwrap_or(ID_PLACEHOLDER + card_id)),
        (IdKind::Deck, deck_id.unwrap_or(ID_PLACEHOLDER)),
        (IdKind::Preset, preset_id.unwrap_or(ID_PLACEHOLDER)),
    ]
}

fn id_encoding_dim(kind: IdKind) -> usize {
    match kind {
        IdKind::Card | IdKind::Note => 12,
        IdKind::Deck | IdKind::Preset => 8,
    }
}

fn read_id_encodings(cursor: &mut Cursor<'_>) -> io::Result<HashMap<(IdKind, i64), Vec<f32>>> {
    let count = cursor.count(13)?; // kind + id + vector length
    let mut values = HashMap::with_capacity(count);
    for _ in 0..count {
        let kind = IdKind::from_cache_code(cursor.u8()?)?;
        let value = cursor.i64()?;
        let encoding = cursor.f32_vec()?;
        if encoding.len() != id_encoding_dim(kind) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "damaged RWKV id encoding",
            ));
        }
        values.insert((kind, value), encoding);
    }
    Ok(values)
}

impl IdKind {
    fn cache_code(self) -> u8 {
        match self {
            IdKind::Card => 0,
            IdKind::Note => 1,
            IdKind::Deck => 2,
            IdKind::Preset => 3,
        }
    }

    fn from_cache_code(code: u8) -> io::Result<Self> {
        match code {
            0 => Ok(IdKind::Card),
            1 => Ok(IdKind::Note),
            2 => Ok(IdKind::Deck),
            3 => Ok(IdKind::Preset),
            _ => Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid RWKV id kind",
            )),
        }
    }
}

#[derive(Clone)]
struct TorchIdRng {
    state: [u32; TORCH_ID_RNG_STATE_LEN],
    index: usize,
}

impl Default for TorchIdRng {
    fn default() -> Self {
        Self {
            state: TORCH_ID_RNG_INITIAL_STATE,
            index: TORCH_ID_RNG_INITIAL_INDEX,
        }
    }
}

impl TorchIdRng {
    fn encoding(&mut self, dim: usize) -> Vec<f32> {
        (0..dim)
            .map(|_| (self.next_u32() as u64 % ID_SPLIT) as f32 - ((ID_SPLIT - 1) as f32 / 2.0))
            .collect()
    }

    fn next_u32(&mut self) -> u32 {
        if self.index >= TORCH_ID_RNG_STATE_LEN {
            self.twist();
        }
        let mut value = self.state[self.index];
        self.index += 1;
        value ^= value >> 11;
        value ^= (value << 7) & 0x9d2c_5680;
        value ^= (value << 15) & 0xefc6_0000;
        value ^ (value >> 18)
    }

    fn twist(&mut self) {
        const UPPER_MASK: u32 = 0x8000_0000;
        const LOWER_MASK: u32 = 0x7fff_ffff;
        const MATRIX_A: u32 = 0x9908_b0df;

        for index in 0..TORCH_ID_RNG_STATE_LEN {
            let next = (index + 1) % TORCH_ID_RNG_STATE_LEN;
            let twist = (self.state[index] & UPPER_MASK) | (self.state[next] & LOWER_MASK);
            let mut value = self.state[(index + 397) % TORCH_ID_RNG_STATE_LEN] ^ (twist >> 1);
            if twist & 1 != 0 {
                value ^= MATRIX_A;
            }
            self.state[index] = value;
        }
        self.index = 0;
    }

    fn cache_state_len(&self) -> usize {
        4 + 4 * self.state.len()
    }

    fn write_cache_state_to(&self, out: &mut impl io::Write) -> io::Result<()> {
        write_state_cache_u32(out, self.index)?;
        for value in self.state {
            out.write_all(&value.to_le_bytes())?;
        }
        Ok(())
    }

    fn read_cache_state(cursor: &mut Cursor<'_>) -> io::Result<Self> {
        let index = cursor.u32()? as usize;
        if index > TORCH_ID_RNG_STATE_LEN {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid RWKV id RNG index",
            ));
        }
        let mut state = [0; TORCH_ID_RNG_STATE_LEN];
        for value in &mut state {
            *value = cursor.u32()?;
        }
        Ok(Self { state, index })
    }
}

fn append_day_offset_encoding(features: &mut Vec<f32>, day_offset: i64, first_day_offset: i64) {
    for period in DAY_OFFSET_ENCODE_PERIODS {
        let phase = 2.0 * std::f32::consts::PI / period;
        let day = day_offset.rem_euclid(period as i64) as f32;
        let first_day = first_day_offset.rem_euclid(period as i64) as f32;
        features.push((phase * day).sin());
        features.push((phase * day).cos());
        features.push((phase * first_day).sin());
        features.push((phase * first_day).cos());
    }
}

fn elapsed_seconds(input: &ReviewInput) -> i64 {
    if creation_elapsed_is_query_only(input) {
        return -1;
    }
    input
        .current_elapsed_seconds
        .or_else(|| {
            input
                .current_elapsed_days
                .map(|days| days * SECONDS_PER_DAY)
        })
        .unwrap_or(-1)
}

fn elapsed_days(input: &ReviewInput, elapsed_seconds: i64) -> i64 {
    if creation_elapsed_is_query_only(input) {
        return -1;
    }
    input.current_elapsed_days.unwrap_or({
        if elapsed_seconds >= 0 {
            elapsed_seconds / SECONDS_PER_DAY
        } else {
            -1
        }
    })
}

fn creation_elapsed_is_query_only(input: &ReviewInput) -> bool {
    !input.is_query && input.ease.is_some() && input.card_type == Some(0)
}

fn scaled_duration(input: &ReviewInput) -> f32 {
    input
        .duration_millis
        .map_or(0.0, |millis| scale_duration(millis as f32))
}

fn scale_elapsed_days(x: i64) -> f32 {
    (log_elapsed(x) - ELAPSED_DAYS_MEAN) / ELAPSED_DAYS_STD
}

fn scale_elapsed_days_cumulative(x: i64) -> f32 {
    (log_elapsed(x) - ELAPSED_DAYS_CUMULATIVE_MEAN) / ELAPSED_DAYS_CUMULATIVE_STD
}

fn scale_elapsed_seconds(x: i64) -> f32 {
    (log_elapsed(x) - ELAPSED_SECONDS_MEAN) / ELAPSED_SECONDS_STD
}

fn scale_elapsed_seconds_cumulative(x: i64) -> f32 {
    (log_elapsed(x) - ELAPSED_SECONDS_CUMULATIVE_MEAN) / ELAPSED_SECONDS_CUMULATIVE_STD
}

fn scale_duration(x: f32) -> f32 {
    ((10.0 + x).ln() - DURATION_MEAN) / DURATION_STD
}

fn scale_diff_new_cards(x: i64) -> f32 {
    ((3.0 + x as f32).ln() - DIFF_NEW_CARDS_MEAN) / DIFF_NEW_CARDS_STD
}

fn scale_diff_reviews(x: i64) -> f32 {
    ((3.0 + x as f32).ln() - DIFF_REVIEWS_MEAN) / DIFF_REVIEWS_STD
}

fn scale_cum_new_cards_today(x: i64) -> f32 {
    ((3.0 + x as f32).ln() - CUM_NEW_CARDS_TODAY_MEAN) / CUM_NEW_CARDS_TODAY_STD
}

fn scale_cum_reviews_today(x: i64) -> f32 {
    ((3.0 + x as f32).ln() - CUM_REVIEWS_TODAY_MEAN) / CUM_REVIEWS_TODAY_STD
}

fn scale_state(x: i64) -> f32 {
    x as f32 - 2.0
}

fn scale_day_offset_diff(x: i64) -> f32 {
    (std::f32::consts::E + x as f32).ln().ln()
}

fn log_elapsed(x: i64) -> f32 {
    if x == -1 {
        0.0
    } else {
        (1.0 + 1e-5 + x as f32).ln()
    }
}

fn cyclic_sin(value: i64, period: i64) -> f32 {
    ((value.rem_euclid(period) as f32) * 2.0 * std::f32::consts::PI / period as f32).sin()
}

fn cyclic_cos(value: i64, period: i64) -> f32 {
    ((value.rem_euclid(period) as f32) * 2.0 * std::f32::consts::PI / period as f32).cos()
}

struct WeightMap {
    tensors: HashMap<String, Tensor>,
    #[cfg(test)]
    packed_linears: HashMap<String, packed_weights::PackedLinear>,
}

struct Tensor {
    shape: Vec<usize>,
    values: Vec<f32>,
}

impl WeightMap {
    fn load(path: &PathBuf) -> io::Result<Self> {
        let data = fs::read(path)?;
        let mut cursor = Cursor::new(&data);
        let int8 = data.starts_with(b"ARWKVWEIGHTS8PC1");
        cursor.expect_magic(if int8 {
            b"ARWKVWEIGHTS8PC1"
        } else {
            b"ARWKVWEIGHTS1"
        })?;
        let count = cursor.u32()? as usize;
        let mut tensors = HashMap::with_capacity(count);

        for _ in 0..count {
            let name_len = cursor.u16()? as usize;
            let name = cursor.string(name_len)?;
            let kind = if int8 { cursor.u8()? } else { 0 };
            let rank = cursor.u8()? as usize;
            let mut shape = Vec::with_capacity(rank);
            let mut len = 1_usize;
            for _ in 0..rank {
                let dim = cursor.u32()? as usize;
                len = len.checked_mul(dim).ok_or_else(|| {
                    io::Error::new(io::ErrorKind::InvalidData, "tensor is too large")
                })?;
                shape.push(dim);
            }
            let mut values = Vec::with_capacity(len);
            match kind {
                0 => {
                    for _ in 0..len {
                        values.push(cursor.f32()?);
                    }
                }
                1 if shape.len() == 2 && shape[0] > 0 && shape[1] > 0 => {
                    let scales = (0..shape[0])
                        .map(|_| cursor.f32())
                        .collect::<io::Result<Vec<_>>>()?;
                    for index in 0..len {
                        let code = cursor.u8()? as i8;
                        if code == i8::MIN
                            || !scales[index / shape[1]].is_finite()
                            || scales[index / shape[1]] <= 0.0
                        {
                            return Err(io::Error::new(
                                io::ErrorKind::InvalidData,
                                "invalid int8 weight",
                            ));
                        }
                        values.push(code as f32 * scales[index / shape[1]]);
                    }
                }
                _ => {
                    return Err(io::Error::new(
                        io::ErrorKind::InvalidData,
                        "invalid weight encoding",
                    ))
                }
            }
            tensors.insert(name, Tensor { shape, values });
        }

        cursor.expect_end()?;
        Ok(Self {
            tensors,
            #[cfg(test)]
            packed_linears: HashMap::new(),
        })
    }

    fn values(&self, name: &str) -> io::Result<Vec<f32>> {
        self.tensors
            .get(name)
            .map(|tensor| tensor.values.clone())
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, missing_weight(name)))
    }

    fn linear(&self, name: &str, input: usize, output: usize, bias: bool) -> io::Result<Linear> {
        let weight_name = format!("{name}.weight");
        let bias_name = format!("{name}.bias");
        let weight = &self.tensor(&weight_name, &[output, input])?.values;
        let bias = if bias {
            Some(self.tensor(&bias_name, &[output])?.values.clone())
        } else {
            None
        };
        #[cfg(test)]
        if let Some(packed) = self.packed_linears.get(&weight_name) {
            return Ok(Linear {
                input,
                output,
                weight_by_input: Vec::new(),
                bias,
                packed_weight: Some(packed.clone()),
            });
        }
        let mut weight_by_input = vec![0.0; weight.len()];
        for row in 0..output {
            for column in 0..input {
                weight_by_input[column * output + row] = weight[row * input + column];
            }
        }
        Ok(Linear {
            input,
            output,
            weight_by_input,
            bias,
            #[cfg(test)]
            packed_weight: None,
        })
    }

    fn named_linear(
        &self,
        weight: &str,
        bias: &str,
        input: usize,
        output: usize,
    ) -> io::Result<Linear> {
        let tensor = self.tensor(weight, &[output, input])?;
        let mut weight_by_input = vec![0.0; input * output];
        for row in 0..output {
            for col in 0..input {
                weight_by_input[col * output + row] = tensor.values[row * input + col];
            }
        }
        Ok(Linear {
            input,
            output,
            weight_by_input,
            bias: Some(self.tensor(bias, &[output])?.values.clone()),
            #[cfg(test)]
            packed_weight: None,
        })
    }

    fn layer_norm(&self, name: &str, dim: usize, eps: f32) -> io::Result<Norm> {
        Ok(Norm {
            groups: 1,
            dim,
            eps,
            weight: self
                .tensor(&format!("{name}.weight"), &[dim])?
                .values
                .clone(),
            bias: self.tensor(&format!("{name}.bias"), &[dim])?.values.clone(),
        })
    }

    fn group_norm(&self, name: &str, groups: usize, dim: usize, eps: f32) -> io::Result<Norm> {
        Ok(Norm {
            groups,
            dim,
            eps,
            weight: self
                .tensor(&format!("{name}.weight"), &[dim])?
                .values
                .clone(),
            bias: self.tensor(&format!("{name}.bias"), &[dim])?.values.clone(),
        })
    }

    fn tensor(&self, name: &str, shape: &[usize]) -> io::Result<&Tensor> {
        let tensor = self
            .tensors
            .get(name)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, missing_weight(name)))?;
        if tensor.shape != shape {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!(
                    "weight {name} has shape {:?}, expected {shape:?}",
                    tensor.shape
                ),
            ));
        }
        Ok(tensor)
    }
}

fn missing_weight(name: &str) -> String {
    format!("missing weight: {name}")
}

struct Cursor<'a> {
    data: &'a [u8],
    offset: usize,
}

impl<'a> Cursor<'a> {
    fn new(data: &'a [u8]) -> Self {
        Self { data, offset: 0 }
    }

    fn expect_magic(&mut self, magic: &[u8]) -> io::Result<()> {
        let found = self.bytes(magic.len())?;
        if found != magic {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "unexpected file magic",
            ));
        }
        Ok(())
    }

    fn expect_end(&self) -> io::Result<()> {
        if self.offset == self.data.len() {
            Ok(())
        } else {
            Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "trailing bytes in file",
            ))
        }
    }

    /// A count read from the data, refused when that many entries of at least
    /// `entry_bytes` can't fit in what is left: a damaged count would otherwise
    /// reserve gigabytes up front and abort the app.
    fn count(&mut self, entry_bytes: usize) -> io::Result<usize> {
        let count = self.u32()? as usize;
        if count.saturating_mul(entry_bytes) > self.data.len() - self.offset {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "count larger than the data",
            ));
        }
        Ok(count)
    }

    fn bytes(&mut self, len: usize) -> io::Result<&'a [u8]> {
        let end = self
            .offset
            .checked_add(len)
            .ok_or_else(|| io::Error::new(io::ErrorKind::UnexpectedEof, "offset overflow"))?;
        let bytes = self
            .data
            .get(self.offset..end)
            .ok_or_else(|| io::Error::new(io::ErrorKind::UnexpectedEof, "file ended early"))?;
        self.offset = end;
        Ok(bytes)
    }

    fn string(&mut self, len: usize) -> io::Result<String> {
        let bytes = self.bytes(len)?;
        String::from_utf8(bytes.to_vec())
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "invalid utf-8 string"))
    }

    fn u8(&mut self) -> io::Result<u8> {
        Ok(self.bytes(1)?[0])
    }

    fn u16(&mut self) -> io::Result<u16> {
        let mut bytes = [0; 2];
        bytes.copy_from_slice(self.bytes(2)?);
        Ok(u16::from_le_bytes(bytes))
    }

    fn u32(&mut self) -> io::Result<u32> {
        let mut bytes = [0; 4];
        bytes.copy_from_slice(self.bytes(4)?);
        Ok(u32::from_le_bytes(bytes))
    }

    fn i64(&mut self) -> io::Result<i64> {
        let mut bytes = [0; 8];
        bytes.copy_from_slice(self.bytes(8)?);
        Ok(i64::from_le_bytes(bytes))
    }

    fn option_i64(&mut self) -> io::Result<Option<i64>> {
        match self.u8()? {
            0 => Ok(None),
            1 => Ok(Some(self.i64()?)),
            _ => Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid optional i64 tag",
            )),
        }
    }

    fn f32(&mut self) -> io::Result<f32> {
        let mut bytes = [0; 4];
        bytes.copy_from_slice(self.bytes(4)?);
        Ok(f32::from_le_bytes(bytes))
    }

    fn f32_vec(&mut self) -> io::Result<Vec<f32>> {
        let byte_len = (self.u32()? as usize)
            .checked_mul(std::mem::size_of::<f32>())
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "vector too large"))?;
        // Bounds-check once, then convert in bulk: about 2.5x faster than
        // decoding element by element on the state-restore path.
        Ok(self
            .bytes(byte_len)?
            .as_chunks::<{ std::mem::size_of::<f32>() }>()
            .0
            .iter()
            .map(|bytes| f32::from_le_bytes(*bytes))
            .collect())
    }

    fn skip_f32_vec(&mut self) -> io::Result<()> {
        let byte_len = (self.u32()? as usize)
            .checked_mul(std::mem::size_of::<f32>())
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "vector too large"))?;
        self.bytes(byte_len)?;
        Ok(())
    }
}

struct SrsModel {
    features_0: Linear,
    features_norm: Norm,
    features_3: Linear,
    modules: Vec<RwkvModule>,
    prehead_norm: Norm,
    head_w_0: Linear,
    head_w_norm: Norm,
    head_w_4: Linear,
    w_linear: Option<Linear>,
    head_ahead_0: Option<Linear>,
    ahead_linear: Option<Linear>,
    power_heads: Option<[Linear; 3]>,
    head_p_0: Linear,
    p_linear: Linear,
    /// Leak-free checkpoints were trained with the three missing-ID flags
    /// (inputs 13..15) always zero.
    leak_free_inputs: bool,
    /// The engine's layer counts and schedule, or the tiny model's (one card
    /// layer) in the small engine; read from the weights at load.
    layer_plan: LayerPlan,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
struct ReviewRetrievabilityScratch {
    feature_hidden: [f32; HEAD_DIM],
    normalized_features: [f32; HEAD_DIM],
    module: ModuleQueryScratch,
    prehead: [f32; D_MODEL],
    head: [f32; HEAD_DIM],
    logits: [f32; 4],
    probabilities: [f32; 4],
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
#[derive(Default)]
struct ReviewRetrievabilityBatchScratch {
    feature_input: Vec<f32>,
    feature_hidden: Vec<f32>,
    normalized_features: Vec<f32>,
    x: Vec<f32>,
    module: ModuleQueryBatchScratch,
    prehead: Vec<f32>,
    head: Vec<f32>,
    logits: Vec<f32>,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
impl Default for ReviewRetrievabilityScratch {
    fn default() -> Self {
        Self {
            feature_hidden: [0.0; HEAD_DIM],
            normalized_features: [0.0; HEAD_DIM],
            module: ModuleQueryScratch::default(),
            prehead: [0.0; D_MODEL],
            head: [0.0; HEAD_DIM],
            logits: [0.0; 4],
            probabilities: [0.0; 4],
        }
    }
}

/// The tiny model is the small architecture without the card's second
/// layer: its weights hold no `rwkv_modules.0.blocks.1.` tensor at all. Any
/// other checkpoint keeps the engine's layers (a partly missing layer fails
/// to load as before).
fn layer_plan_for(weights: &WeightMap) -> LayerPlan {
    let card_second_layer = "rwkv_modules.0.blocks.1.";
    if SMALL_MODEL
        && !weights
            .tensors
            .keys()
            .any(|name| name.starts_with(card_second_layer))
    {
        TINY_LAYER_PLAN
    } else {
        ENGINE_LAYER_PLAN
    }
}

impl SrsModel {
    fn load(path: &PathBuf) -> io::Result<Self> {
        let weights = WeightMap::load(path)?;
        Self::from_weights(weights)
    }

    fn from_weights(weights: WeightMap) -> io::Result<Self> {
        let layer_plan = layer_plan_for(&weights);
        let modules = layer_plan
            .module_layers
            .iter()
            .enumerate()
            .map(|(module_id, layer_count)| RwkvModule::load(&weights, module_id, *layer_count))
            .collect::<io::Result<Vec<_>>>()?;

        Ok(Self {
            features_0: weights.linear("features2card.0", CARD_FEATURES, HEAD_DIM, true)?,
            features_norm: weights.layer_norm("features2card.2", HEAD_DIM, 1e-5)?,
            features_3: weights.linear("features2card.3", HEAD_DIM, D_MODEL, true)?,
            modules,
            prehead_norm: weights.layer_norm("prehead_norm", D_MODEL, 1e-5)?,
            head_w_0: weights.linear("head_w.0", D_MODEL, D_MODEL, true)?,
            head_w_norm: weights.layer_norm("head_w.2", D_MODEL, 1e-5)?,
            head_w_4: weights.linear("head_w.4", D_MODEL, HEAD_DIM, true)?,
            w_linear: if SMALL_MODEL {
                None
            } else {
                Some(weights.linear("w_linear", HEAD_DIM, NUM_CURVES, true)?)
            },
            head_ahead_0: if SMALL_MODEL {
                None
            } else {
                Some(weights.linear("head_ahead_logits.0", D_MODEL, HEAD_DIM, true)?)
            },
            ahead_linear: if SMALL_MODEL {
                None
            } else {
                Some(weights.linear("ahead_linear", HEAD_DIM, NUM_CURVES, true)?)
            },
            power_heads: if SMALL_MODEL {
                Some([
                    weights.named_linear("gru_w_weight", "gru_w_bias", HEAD_DIM, 3)?,
                    weights.named_linear("gru_s_weight", "gru_s_bias", HEAD_DIM, 3)?,
                    weights.named_linear("gru_d_weight", "gru_d_bias", HEAD_DIM, 3)?,
                ])
            } else {
                None
            },
            head_p_0: weights.linear("head_p.0", D_MODEL, HEAD_DIM, true)?,
            p_linear: weights.linear("p_linear", HEAD_DIM, 4, true)?,
            leak_free_inputs: false,
            layer_plan,
        })
    }

    /// Card layers of the loaded checkpoint: 1 for the tiny model, else the
    /// engine's.
    fn card_layers(&self) -> usize {
        self.layer_plan.module_layers[0]
    }

    /// Zeroes the inputs this checkpoint never saw set: feature 22 for the
    /// small architecture and, for leak-free checkpoints, the missing note,
    /// deck and preset flags.
    fn zero_unused_features(&self, features: &mut [f32]) {
        if SMALL_MODEL {
            features[22] = 0.0;
        }
        if self.leak_free_inputs {
            features[MISSING_ID_FLAG_FEATURES].fill(0.0);
        }
    }

    fn review(&self, features: &[f32], state: SrsStateRef<'_>) -> ReviewHeads {
        self.review_features(features, state)
    }

    fn review_many(&self, items: &[ReviewPredictionWorkItem]) -> Vec<ReviewHeads> {
        items
            .par_iter()
            .map(|item| self.review_features(&item.features, item.state.as_ref()))
            .collect()
    }

    /// Maps each item's heads as soon as they are computed: a batch of full
    /// heads would hold a next state per item, hundreds of KB each.
    fn review_many_borrowed<T: Send>(
        &self,
        items: &[ReviewPredictionBorrowedWorkItem<'_>],
        map: impl Fn(usize, ReviewHeads) -> T + Sync,
    ) -> Vec<T> {
        items
            .par_iter()
            .enumerate()
            .map(|(index, item)| map(index, self.review_features(&item.features, item.state)))
            .collect()
    }

    fn review_retrievability_many(
        &self,
        items: &[ReviewPredictionWorkItem],
    ) -> io::Result<Vec<f32>> {
        let items = items
            .iter()
            .map(|item| ReviewPredictionQueryRef {
                features: &item.features,
                state: item.state.as_ref(),
            })
            .collect::<Vec<_>>();
        self.review_retrievability_query_refs(&items)
    }

    fn review_retrievability_many_borrowed(
        &self,
        items: &[ReviewPredictionBorrowedWorkItem<'_>],
    ) -> io::Result<Vec<f32>> {
        let items = items
            .iter()
            .map(|item| ReviewPredictionQueryRef {
                features: &item.features,
                state: item.state,
            })
            .collect::<Vec<_>>();
        self.review_retrievability_query_refs(&items)
    }

    fn review_retrievability_query_refs(
        &self,
        items: &[ReviewPredictionQueryRef<'_>],
    ) -> io::Result<Vec<f32>> {
        #[cfg(all(any(target_os = "macos", target_arch = "aarch64"), not(test)))]
        {
            self.review_retrievability_query_refs_batched(items)
        }

        #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
        {
            self.review_retrievability_query_refs_scalar(items)
        }
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn review_retrievability_query_refs_scalar(
        &self,
        items: &[ReviewPredictionQueryRef<'_>],
    ) -> io::Result<Vec<f32>> {
        items
            .par_iter()
            .map_init(
                // Keep the large per-worker scratch off Rayon's limited worker stacks.
                || Box::new(ReviewRetrievabilityScratch::default()),
                |scratch, item| {
                    self.review_retrievability_features(item.features, item.state, scratch)
                },
            )
            .collect()
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn review_retrievability_query_refs_batched(
        &self,
        items: &[ReviewPredictionQueryRef<'_>],
    ) -> io::Result<Vec<f32>> {
        let mut retrievabilities = vec![0.0; items.len()];
        retrievabilities
            .par_chunks_mut(RETRIEVABILITY_GEMM_BATCH_SIZE)
            .zip(items.par_chunks(RETRIEVABILITY_GEMM_BATCH_SIZE))
            .try_for_each_init(
                ReviewRetrievabilityBatchScratch::default,
                |scratch, (retrievabilities, items)| {
                    self.review_retrievability_query_batch(items, scratch, retrievabilities)
                },
            )?;
        Ok(retrievabilities)
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn review_retrievability_query_batch(
        &self,
        items: &[ReviewPredictionQueryRef<'_>],
        scratch: &mut ReviewRetrievabilityBatchScratch,
        retrievabilities: &mut [f32],
    ) -> io::Result<()> {
        let rows = items.len();
        scratch.feature_input.clear();
        scratch.feature_input.reserve(rows * CARD_FEATURES);
        for item in items {
            scratch.feature_input.extend_from_slice(item.features);
            let start = scratch.feature_input.len() - CARD_FEATURES;
            self.zero_unused_features(&mut scratch.feature_input[start..]);
        }

        self.features_0
            .apply_batch(&scratch.feature_input, rows, &mut scratch.feature_hidden);
        query_silu_batch_in_place(&mut scratch.feature_hidden);
        self.features_norm.apply_batch(
            &scratch.feature_hidden,
            rows,
            &mut scratch.normalized_features,
        );
        self.features_3
            .apply_batch(&scratch.normalized_features, rows, &mut scratch.x);
        query_silu_batch_in_place(&mut scratch.x);

        let decoded = items
            .iter()
            .map(|item| item.state.try_decoded())
            .collect::<io::Result<Vec<_>>>()?;
        let decoded_items: Vec<_> = items
            .iter()
            .zip(&decoded)
            .map(|(item, states)| ReviewPredictionQueryRef {
                features: item.features,
                state: SrsStateRef {
                    card: states[0].as_deref(),
                    deck: states[1].as_deref(),
                    note: states[2].as_deref(),
                    preset: states[3].as_deref(),
                    global: states[4].as_deref(),
                },
            })
            .collect();
        let mut v0: [Vec<f32>; 5] = std::array::from_fn(|_| vec![0.0; rows * D_MODEL]);
        for &(module_id, layer_id) in self.layer_plan.steps {
            self.modules[module_id].layers[layer_id].run_query_batch(
                &scratch.x,
                &mut v0[module_id],
                &decoded_items,
                module_id,
                layer_id,
                &mut scratch.module.next,
                &mut scratch.module.layer,
            );
            std::mem::swap(&mut scratch.x, &mut scratch.module.next);
        }

        self.prehead_norm
            .apply_batch(&scratch.x, rows, &mut scratch.prehead);
        self.head_p_0
            .apply_batch(&scratch.prehead, rows, &mut scratch.head);
        relu_in_place(&mut scratch.head);
        self.p_linear
            .apply_batch(&scratch.head, rows, &mut scratch.logits);
        for (retrievability, logits) in retrievabilities
            .iter_mut()
            .zip(scratch.logits.as_chunks::<4>().0)
        {
            let mut probabilities = [0.0; 4];
            softmax_into(logits, &mut probabilities);
            *retrievability = 1.0 - probabilities[0];
        }
        Ok(())
    }

    /// The feature MLP shared by the per-review and bulk replay paths.
    fn feature_mlp(&self, features: &[f32]) -> Vec<f32> {
        let mut features = features.to_vec();
        self.zero_unused_features(&mut features);
        let mut x = self.features_0.apply(&features);
        silu_in_place(&mut x);
        x = self.features_norm.apply(&x);
        x = self.features_3.apply(&x);
        silu_in_place(&mut x);
        x
    }

    /// The recall-curve heads shared by the per-review and bulk replay paths.
    /// `prehead_x` must already have `prehead_norm` applied.
    fn curve_head(&self, prehead_x: &[f32]) -> ReviewCurve {
        let mut head_w = self.head_w_0.apply(prehead_x);
        relu_in_place(&mut head_w);
        head_w = self.head_w_norm.apply(&head_w);
        head_w = self.head_w_4.apply(&head_w);
        if let Some([w, s, d]) = &self.power_heads {
            return ReviewCurve {
                weights: softmax(&w.apply(&head_w)),
                ahead_logits: vec![],
                power: Some((s.apply(&head_w), d.apply(&head_w))),
            };
        }
        let weights = softmax(&self.w_linear.as_ref().unwrap().apply(&head_w));

        let mut ahead = self.head_ahead_0.as_ref().unwrap().apply(prehead_x);
        relu_in_place(&mut ahead);
        let ahead_logits = self.ahead_linear.as_ref().unwrap().apply(&ahead);

        ReviewCurve {
            ahead_logits,
            weights,
            power: None,
        }
    }

    /// The retrievability head shared by the per-review and bulk replay
    /// paths. `prehead_x` must already have `prehead_norm` applied.
    fn button_probabilities_head(&self, prehead_x: &[f32]) -> [f32; 4] {
        let mut head_p = self.head_p_0.apply(prehead_x);
        relu_in_place(&mut head_p);
        let logits = self.p_linear.apply(&head_p);
        let probabilities = softmax(&logits);
        let mut out = [0.0; 4];
        out.copy_from_slice(&probabilities);
        out
    }

    fn retrievability_head(&self, prehead_x: &[f32]) -> f32 {
        1.0 - self.button_probabilities_head(prehead_x)[0]
    }

    fn review_features(&self, features: &[f32], state: SrsStateRef<'_>) -> ReviewHeads {
        #[cfg(test)]
        let review_profile_started = rwkv_warmup_profile_start();
        #[cfg(test)]
        let feature_mlp_profile_started = rwkv_warmup_profile_start();

        let x = self.feature_mlp(features);

        #[cfg(test)]
        rwkv_warmup_profile_record(
            RwkvWarmupProfileBucket::FeatureMlp,
            feature_mlp_profile_started,
        );

        #[cfg(test)]
        let _module_profile_started = rwkv_warmup_profile_start();
        let states = [
            state.card,
            state.deck,
            state.note,
            state.preset,
            state.global,
        ];
        let decoded: Vec<_> = states
            .iter()
            .map(|state| state.map(ModuleState::decoded))
            .collect();
        let mut x = x;
        let mut v0 = [const { Vec::new() }; 5];
        let mut next: [Vec<LayerState>; 5] = std::array::from_fn(|_| Vec::new());
        for &(module_id, layer_id) in self.layer_plan.steps {
            let previous = decoded[module_id]
                .as_ref()
                .and_then(|state| state.layers.get(layer_id));
            let (out, value, layer_state) =
                self.modules[module_id].layers[layer_id].run(&x, &v0[module_id], previous);
            x = out;
            v0[module_id] = value;
            next[module_id].push(layer_state);
        }
        let [card_state, deck_state, note_state, preset_state, global_state] =
            std::array::from_fn(|id| {
                ModuleState {
                    layers: std::mem::take(&mut next[id]),
                    packed: None,
                }
                .compact(self.modules[id].precision)
            });

        #[cfg(test)]
        let heads_profile_started = rwkv_warmup_profile_start();
        let x = self.prehead_norm.apply(&x);
        let curve = self.curve_head(&x);
        let button_probabilities = self.button_probabilities_head(&x);
        let retrievability = 1.0 - button_probabilities[0];

        let next_state = SrsState {
            card: card_state,
            deck: deck_state,
            note: note_state,
            preset: preset_state,
            global: global_state,
        };

        let heads = ReviewHeads {
            retrievability,
            button_probabilities,
            curve,
            next_state,
        };
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Heads, heads_profile_started);
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::ModelReview, review_profile_started);
        heads
    }

    /// The answer-time head's frozen inputs at a query and the instant head's
    /// grade probabilities. Runs the same trunk as [`Self::review_features`]
    /// and keeps the residual after the input encoder and after each layer
    /// round, layer-normalised without affine parameters, then the instant
    /// head's hidden layer.
    fn answer_time_inputs(&self, features: &[f32], state: SrsStateRef<'_>) -> (Vec<f32>, [f32; 4]) {
        let unit = Norm {
            groups: 1,
            dim: D_MODEL,
            eps: 1e-5,
            weight: vec![1.0; D_MODEL],
            bias: vec![0.0; D_MODEL],
        };
        let mut out = Vec::with_capacity(AnswerTimeHead::FROZEN);
        let mut x = self.feature_mlp(features);
        out.extend(unit.apply(&x));
        let states = [
            state.card,
            state.deck,
            state.note,
            state.preset,
            state.global,
        ];
        let decoded: Vec<_> = states
            .iter()
            .map(|state| state.map(ModuleState::decoded))
            .collect();
        let mut v0 = [const { Vec::new() }; 5];
        let steps = self.layer_plan.steps;
        for (index, &(module_id, layer_id)) in steps.iter().enumerate() {
            let previous = decoded[module_id]
                .as_ref()
                .and_then(|state| state.layers.get(layer_id));
            let (next, value, _) =
                self.modules[module_id].layers[layer_id].run(&x, &v0[module_id], previous);
            x = next;
            v0[module_id] = value;
            if index + 1 == steps.len() || steps[index + 1].1 != layer_id {
                out.extend(unit.apply(&x));
            }
        }
        let prehead = self.prehead_norm.apply(&x);
        let mut head_p = self.head_p_0.apply(&prehead);
        relu_in_place(&mut head_p);
        let probabilities = softmax(&self.p_linear.apply(&head_p));
        out.extend_from_slice(&head_p);
        let mut button_probabilities = [0.0; 4];
        button_probabilities.copy_from_slice(&probabilities);
        (out, button_probabilities)
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn review_retrievability_features(
        &self,
        features: &[f32],
        state: SrsStateRef<'_>,
        scratch: &mut ReviewRetrievabilityScratch,
    ) -> io::Result<f32> {
        let mut features = features.to_vec();
        self.zero_unused_features(&mut features);
        self.features_0
            .apply_into(&features, &mut scratch.feature_hidden);
        silu_in_place(&mut scratch.feature_hidden);
        self.features_norm
            .apply_into(&scratch.feature_hidden, &mut scratch.normalized_features);
        let mut x = [0.0; D_MODEL];
        self.features_3
            .apply_into(&scratch.normalized_features, &mut x);
        silu_in_place(&mut x);

        let decoded = state.try_decoded()?;
        let mut v0 = [[0.0; D_MODEL]; 5];
        for &(module_id, layer_id) in self.layer_plan.steps {
            let previous = decoded[module_id]
                .as_ref()
                .and_then(|state| state.layers.get(layer_id));
            self.modules[module_id].layers[layer_id].run_query_into(
                &x,
                &mut v0[module_id],
                previous,
                &mut scratch.module.next,
                &mut scratch.module.layer,
            );
            x = scratch.module.next;
        }

        self.prehead_norm.apply_into(&x, &mut scratch.prehead);
        self.head_p_0
            .apply_into(&scratch.prehead, &mut scratch.head);
        relu_in_place(&mut scratch.head);
        self.p_linear.apply_into(&scratch.head, &mut scratch.logits);
        softmax_into(&scratch.logits, &mut scratch.probabilities);
        Ok(1.0 - scratch.probabilities[0])
    }
}

/// The answer-time head on the frozen trunk: Linear, ReLU, Linear to 4
/// grades x 16 log-time bins. Its inputs are the query's residual after the
/// input encoder and after each layer round (layer-normalised without
/// affine parameters), the instant head's hidden layer, and the history
/// features.
struct AnswerTimeHead {
    hidden: Linear,
    out: Linear,
}

impl AnswerTimeHead {
    const FROZEN: usize = (ANSWER_TIME_ROUNDS + 1) * D_MODEL + HEAD_DIM;
    const INPUTS: usize = Self::FROZEN + answer_time_items::ANSWER_TIME_FEATURES;

    /// Loads `time_head.f32.bin` (or `time_head.int8.bin` beside INT8
    /// weights) from the folder of the weights; `None` when the folder has
    /// none or the engine is not the small one.
    fn load_beside(weights: &Path) -> io::Result<Option<Self>> {
        if !SMALL_MODEL {
            return Ok(None);
        }
        let int8 = weights
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.contains("int8"));
        let path = weights.with_file_name(if int8 {
            "time_head.int8.bin"
        } else {
            "time_head.f32.bin"
        });
        if !path.is_file() {
            return Ok(None);
        }
        Self::load(&path).map(Some)
    }

    fn load(path: &PathBuf) -> io::Result<Self> {
        let weights = WeightMap::load(path)?;
        let version = weights.tensor("time_head.format_version", &[1])?.values[0];
        if version != ANSWER_TIME_FORMAT_VERSION {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("unsupported RWKV answer-time head format {version}"),
            ));
        }
        let hidden = weights
            .tensors
            .get("time_mlps.0.hidden.weight")
            .map(|tensor| tensor.shape[0])
            .ok_or_else(|| {
                io::Error::new(
                    io::ErrorKind::InvalidData,
                    missing_weight("time_mlps.0.hidden.weight"),
                )
            })?;
        Ok(Self {
            hidden: weights.linear("time_mlps.0.hidden", Self::INPUTS, hidden, true)?,
            out: weights.linear(
                "time_mlps.0.out",
                hidden,
                4 * answer_time_items::ANSWER_TIME_BINS,
                true,
            )?,
        })
    }

    fn logits(&self, frozen: &[f32], explicit: &[f32]) -> Vec<f32> {
        let mut inputs = Vec::with_capacity(Self::INPUTS);
        inputs.extend_from_slice(frozen);
        inputs.extend_from_slice(explicit);
        let mut hidden = self.hidden.apply(&inputs);
        relu_in_place(&mut hidden);
        self.out.apply(&hidden)
    }

    fn predict(
        &self,
        frozen: &[f32],
        explicit: &[f32],
        button_probabilities: [f32; 4],
    ) -> AnswerTimeOutput {
        AnswerTimeOutput::from_logits(&self.logits(frozen, explicit), button_probabilities)
    }
}

/// Layer rounds of the interleaved schedule (the small model: 4). A round
/// ends where the next step starts another layer index.
const ANSWER_TIME_ROUNDS: usize = layer_rounds(LAYER_STEPS);
// The tiny model drops one step of round 2, not a round: an answer-time head
// beside it would read the same number of residuals.
const _: () = assert!(!SMALL_MODEL || layer_rounds(TINY_LAYER_STEPS) == ANSWER_TIME_ROUNDS);

const fn layer_rounds(steps: &[(usize, usize)]) -> usize {
    let mut rounds = 0;
    let mut index = 0;
    while index < steps.len() {
        if index + 1 == steps.len() || steps[index + 1].1 != steps[index].1 {
            rounds += 1;
        }
        index += 1;
    }
    rounds
}

struct ReviewHeads {
    retrievability: f32,
    button_probabilities: [f32; 4],
    curve: ReviewCurve,
    next_state: SrsState,
}

#[derive(Clone)]
struct ReviewCurve {
    ahead_logits: Vec<f32>,
    weights: Vec<f32>,
    power: Option<(Vec<f32>, Vec<f32>)>,
}

impl ReviewCurve {
    #[cfg(test)]
    fn write_cache_state(&self, out: &mut Vec<u8>) {
        self.write_cache_state_to(out)
            .expect("writing to a Vec should not fail");
    }

    fn cache_state_len(&self) -> usize {
        9 + 4 * (self.ahead_logits.len() + self.weights.len())
            + self
                .power
                .as_ref()
                .map_or(0, |(s, d)| 8 + 4 * (s.len() + d.len()))
    }

    fn write_cache_state_to(&self, out: &mut impl io::Write) -> io::Result<()> {
        out.write_all(&[u8::from(self.power.is_some())])?;
        write_state_cache_f32_slice(out, &self.ahead_logits)?;
        write_state_cache_f32_slice(out, &self.weights)?;
        if let Some((s, d)) = &self.power {
            write_state_cache_f32_slice(out, s)?;
            write_state_cache_f32_slice(out, d)?;
        }
        Ok(())
    }

    fn read_cache_state(cursor: &mut Cursor<'_>) -> io::Result<Self> {
        let power = cursor.u8()?;
        let ahead_logits = cursor.f32_vec()?;
        let weights = cursor.f32_vec()?;
        let power = match power {
            0 => None,
            1 => Some((cursor.f32_vec()?, cursor.f32_vec()?)),
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "invalid curve head",
                ))
            }
        };
        Ok(Self {
            ahead_logits,
            weights,
            power,
        })
    }
}

#[derive(Clone, Copy)]
struct SrsStateRef<'a> {
    card: Option<&'a ModuleState>,
    deck: Option<&'a ModuleState>,
    note: Option<&'a ModuleState>,
    preset: Option<&'a ModuleState>,
    global: Option<&'a ModuleState>,
}

impl<'a> SrsStateRef<'a> {
    fn try_decoded(self) -> io::Result<[Option<Cow<'a, ModuleState>>; 5]> {
        let mut decoded = std::array::from_fn(|_| None);
        for (slot, (scope, state)) in decoded.iter_mut().zip([
            ("card", self.card),
            ("deck", self.deck),
            ("note", self.note),
            ("preset", self.preset),
            ("global", self.global),
        ]) {
            *slot = state
                .map(ModuleState::try_decoded)
                .transpose()
                .map_err(|error| {
                    io::Error::new(error.kind(), format!("RWKV {scope} state: {error}"))
                })?;
        }
        Ok(decoded)
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn module(self, module_id: usize) -> Option<&'a ModuleState> {
        match module_id {
            0 => self.card,
            1 => self.deck,
            2 => self.note,
            3 => self.preset,
            4 => self.global,
            _ => None,
        }
    }
}

/// [SrsStateRef] for states that may have been decoded for the query.
struct SrsStateView<'a> {
    card: Option<Cow<'a, ModuleState>>,
    note: Option<Cow<'a, ModuleState>>,
    deck: Option<&'a ModuleState>,
    preset: Option<&'a ModuleState>,
    global: Option<&'a ModuleState>,
}

impl SrsStateView<'_> {
    fn as_ref(&self) -> SrsStateRef<'_> {
        SrsStateRef {
            card: self.card.as_deref(),
            note: self.note.as_deref(),
            deck: self.deck,
            preset: self.preset,
            global: self.global,
        }
    }
}

struct SrsStateOwned {
    card: Option<ModuleState>,
    deck: Option<ModuleState>,
    note: Option<ModuleState>,
    preset: Option<ModuleState>,
    global: Option<ModuleState>,
}

impl SrsStateOwned {
    fn as_ref(&self) -> SrsStateRef<'_> {
        SrsStateRef {
            card: self.card.as_ref(),
            deck: self.deck.as_ref(),
            note: self.note.as_ref(),
            preset: self.preset.as_ref(),
            global: self.global.as_ref(),
        }
    }
}

struct SrsState {
    card: ModuleState,
    deck: ModuleState,
    note: ModuleState,
    preset: ModuleState,
    global: ModuleState,
}

/// See [RwkvInference::state_snapshot].
pub struct RwkvStateSnapshot {
    states: ReviewStateMaps,
    cache_state: Vec<u8>,
}

impl RwkvStateSnapshot {
    /// Writes the warm-up states one at a time (kuma3's offline state file):
    /// the card, note, deck and preset maps (u64 count, then i64 id + state,
    /// by id), then the global state (flag byte + state). Card and note states
    /// are stored as the engine keeps them ([SharedStateMap]), the others as
    /// 16-bit floats (see [write_half_module_state]).
    pub fn write_warm_up_states(&self, out: &mut impl io::Write) -> io::Result<()> {
        let states = &self.states;
        let maps: [&dyn StateMap; 4] = [&states.card, &states.note, &states.deck, &states.preset];
        for map in maps {
            out.write_all(&(map.count() as u64).to_le_bytes())?;
            let mut ids = map.ids();
            ids.sort_unstable();
            for id in ids {
                out.write_all(&id.to_le_bytes())?;
                map.write_file_state(&id, out)?;
            }
        }
        match &states.global {
            Some(state) => {
                out.write_all(&[1])?;
                write_half_module_state(out, state)
            }
            None => out.write_all(&[0]),
        }
    }

    /// What [RwkvInference::cache_state] gave when the snapshot was taken.
    pub fn cache_state(&self) -> &[u8] {
        &self.cache_state
    }
}

#[derive(Default)]
struct ReviewStateMaps {
    card: SharedStateMap,
    note: SharedStateMap,
    deck: HashMap<i64, ModuleState>,
    preset: HashMap<i64, ModuleState>,
    global: Option<ModuleState>,
    dirty_card: HashSet<i64>,
    dirty_note: HashSet<i64>,
    dirty_deck: HashSet<i64>,
    dirty_preset: HashSet<i64>,
    global_dirty: bool,
}

impl ReviewStateMaps {
    fn from_serialized(
        card_states: &[(i64, Vec<u8>)],
        note_states: &[(i64, Vec<u8>)],
        deck_states: &[(i64, Vec<u8>)],
        preset_states: &[(i64, Vec<u8>)],
        global_state: Option<&[u8]>,
    ) -> io::Result<Self> {
        Ok(Self {
            card: deserialize_state_map(card_states)?,
            note: deserialize_state_map(note_states)?,
            deck: deserialize_state_map(deck_states)?,
            preset: deserialize_state_map(preset_states)?,
            global: deserialize_module_state(global_state)?,
            dirty_card: HashSet::new(),
            dirty_note: HashSet::new(),
            dirty_deck: HashSet::new(),
            dirty_preset: HashSet::new(),
            global_dirty: false,
        })
    }

    /// Deserializes only the snapshot states that `inputs` can read or
    /// advance, plus the global state.
    fn from_snapshot_for_inputs(
        snapshot: &RwkvWorkloadSimulationSnapshot,
        inputs: &[RwkvWorkloadSimulationInput],
    ) -> io::Result<Self> {
        let ids = StreamIds::for_workload_inputs(inputs);
        Ok(Self {
            card: deserialize_state_map_subset(&snapshot.card_states, &ids.card)?,
            note: deserialize_state_map_subset(&snapshot.note_states, &ids.note)?,
            deck: deserialize_state_map_subset(&snapshot.deck_states, &ids.deck)?,
            preset: deserialize_state_map_subset(&snapshot.preset_states, &ids.preset)?,
            global: deserialize_module_state(snapshot.global_state.as_deref())?,
            ..Default::default()
        })
    }

    /// Approximate memory used by the states of `ids`, plus the global state.
    fn state_len_for(&self, ids: &StreamIds) -> usize {
        let maps: [(&dyn StateMap, _); 4] = [
            (&self.card, &ids.card),
            (&self.note, &ids.note),
            (&self.deck, &ids.deck),
            (&self.preset, &ids.preset),
        ];
        maps.into_iter()
            .flat_map(|(states, ids)| ids.iter().filter_map(|id| states.serialized_len(id)))
            .chain(self.global.iter().map(serialized_module_state_len))
            .sum()
    }

    /// Copies the states without their checkpoint dirty-tracking.
    #[cfg(test)]
    fn clone_states(&self) -> Self {
        Self {
            card: self.card.clone(),
            note: self.note.clone(),
            deck: self.deck.clone(),
            preset: self.preset.clone(),
            global: self.global.clone(),
            ..Default::default()
        }
    }

    /// The states `input` reads; card and note states are converted to f32.
    fn state_view(&self, input: &ReviewInput) -> SrsStateView<'_> {
        SrsStateView {
            card: self.card.state(&input.card_id),
            note: input.note_id.and_then(|id| self.note.state(&id)),
            deck: input.deck_id.and_then(|id| self.deck.get(&id)),
            preset: input.preset_id.and_then(|id| self.preset.get(&id)),
            global: self.global.as_ref(),
        }
    }

    /// The fork's accessor, for its tests and benches (kuma3 shares card and
    /// note states, so they are borrowed through their `Arc`).
    #[cfg(test)]
    fn state_ref(&self, input: &ReviewInput) -> SrsStateRef<'_> {
        SrsStateRef {
            card: self.card.0.get(&input.card_id).map(|state| &**state),
            deck: input.deck_id.and_then(|id| self.deck.get(&id)),
            note: input
                .note_id
                .and_then(|id| self.note.0.get(&id))
                .map(|state| &**state),
            preset: input.preset_id.and_then(|id| self.preset.get(&id)),
            global: self.global.as_ref(),
        }
    }

    fn state_owned(&self, input: &ReviewInput) -> SrsStateOwned {
        SrsStateOwned {
            card: self.card.state(&input.card_id).map(Cow::into_owned),
            note: input
                .note_id
                .and_then(|id| self.note.state(&id).map(Cow::into_owned)),
            deck: input.deck_id.and_then(|id| self.deck.get(&id).cloned()),
            preset: input.preset_id.and_then(|id| self.preset.get(&id).cloned()),
            global: self.global.clone(),
        }
    }

    fn serialized_state(&self, input: &ReviewInput) -> ReviewStateOwned {
        ReviewStateOwned {
            card: self
                .card
                .state(&input.card_id)
                .map(|s| serialize_module_state(&s)),
            note: input
                .note_id
                .and_then(|id| self.note.state(&id))
                .map(|s| serialize_module_state(&s)),
            deck: input
                .deck_id
                .and_then(|id| self.deck.get(&id))
                .map(serialize_module_state),
            preset: input
                .preset_id
                .and_then(|id| self.preset.get(&id))
                .map(serialize_module_state),
            global: self.global.as_ref().map(serialize_module_state),
        }
    }

    fn state_owned_after_review(
        &self,
        answer: &ReviewInput,
        query: &ReviewInput,
        next_state: &SrsState,
    ) -> SrsStateOwned {
        SrsStateOwned {
            card: if query.card_id == answer.card_id {
                Some(next_state.card.clone())
            } else {
                self.card.state(&query.card_id).map(Cow::into_owned)
            },
            note: branched_optional_module_state(
                &self.note,
                query.note_id,
                answer.note_id,
                &next_state.note,
            ),
            deck: branched_optional_module_state(
                &self.deck,
                query.deck_id,
                answer.deck_id,
                &next_state.deck,
            ),
            preset: branched_optional_module_state(
                &self.preset,
                query.preset_id,
                answer.preset_id,
                &next_state.preset,
            ),
            global: Some(next_state.global.clone()),
        }
    }

    fn store(&mut self, input: &ReviewInput, state: SrsState) {
        self.card.put(input.card_id, state.card);
        self.mark_dirty(input);
        if let Some(note_id) = input.note_id {
            self.note.put(note_id, state.note);
        }
        if let Some(deck_id) = input.deck_id {
            self.deck.insert(deck_id, state.deck);
        }
        if let Some(preset_id) = input.preset_id {
            self.preset.insert(preset_id, state.preset);
        }
        self.global = Some(state.global);
    }

    fn mark_dirty(&mut self, input: &ReviewInput) {
        self.dirty_card.insert(input.card_id);
        if let Some(note_id) = input.note_id {
            self.dirty_note.insert(note_id);
        }
        if let Some(deck_id) = input.deck_id {
            self.dirty_deck.insert(deck_id);
        }
        if let Some(preset_id) = input.preset_id {
            self.dirty_preset.insert(preset_id);
        }
        self.global_dirty = true;
    }

    fn restore_serialized(
        &mut self,
        card_id: i64,
        note_id: Option<i64>,
        deck_id: Option<i64>,
        preset_id: Option<i64>,
        state: &ReviewStateOwned,
    ) -> io::Result<()> {
        restore_serialized_state(&mut self.card, card_id, state.card.as_deref())?;
        self.dirty_card.insert(card_id);
        restore_optional_serialized_state(&mut self.note, note_id, state.note.as_deref())?;
        self.dirty_note.extend(note_id);
        restore_optional_serialized_state(&mut self.deck, deck_id, state.deck.as_deref())?;
        self.dirty_deck.extend(deck_id);
        restore_optional_serialized_state(&mut self.preset, preset_id, state.preset.as_deref())?;
        self.dirty_preset.extend(preset_id);
        self.global = deserialize_module_state(state.global.as_deref())?;
        self.global_dirty = true;
        Ok(())
    }

    fn mark_checkpoint_saved(&mut self) {
        self.dirty_card.clear();
        self.dirty_note.clear();
        self.dirty_deck.clear();
        self.dirty_preset.clear();
        self.global_dirty = false;
    }

    fn checkpoint_delta_len(&self, full: bool) -> io::Result<usize> {
        let mut len = STATE_CACHE_DELTA_MAGIC.len();
        let maps: [(&dyn StateMap, _); 4] = [
            (&self.card, &self.dirty_card),
            (&self.note, &self.dirty_note),
            (&self.deck, &self.dirty_deck),
            (&self.preset, &self.dirty_preset),
        ];
        for (states, dirty) in maps {
            len = len
                .checked_add(state_cache_map_delta_len(states, dirty, full)?)
                .ok_or_else(state_cache_delta_too_large)?;
        }
        len = len.checked_add(1).ok_or_else(state_cache_delta_too_large)?;
        if full || self.global_dirty {
            if let Some(global) = &self.global {
                len = len
                    .checked_add(4 + serialized_module_state_len(global))
                    .ok_or_else(state_cache_delta_too_large)?;
            }
        }
        Ok(len)
    }

    fn write_checkpoint_delta(&self, out: &mut impl io::Write, full: bool) -> io::Result<()> {
        out.write_all(STATE_CACHE_DELTA_MAGIC)?;
        let maps: [(&dyn StateMap, _); 4] = [
            (&self.card, &self.dirty_card),
            (&self.note, &self.dirty_note),
            (&self.deck, &self.dirty_deck),
            (&self.preset, &self.dirty_preset),
        ];
        for (states, dirty) in maps {
            write_state_cache_map_delta(out, states, dirty, full)?;
        }
        if !full && !self.global_dirty {
            out.write_all(&[0])?;
        } else if let Some(global) = &self.global {
            out.write_all(&[2])?;
            write_snapshot_module_state(out, global)?;
        } else {
            out.write_all(&[1])?;
        }
        Ok(())
    }
}

fn state_cache_delta_identities(
    states: &dyn StateMap,
    dirty: &HashSet<i64>,
    full: bool,
) -> Vec<i64> {
    let mut identities = if full {
        states.ids()
    } else {
        dirty.iter().copied().collect::<Vec<_>>()
    };
    identities.sort_unstable();
    identities
}

fn state_cache_map_delta_len(
    states: &dyn StateMap,
    dirty: &HashSet<i64>,
    full: bool,
) -> io::Result<usize> {
    let identities = state_cache_delta_identities(states, dirty, full);
    let mut len = 4_usize;
    for identity in identities {
        len = len.checked_add(9).ok_or_else(state_cache_delta_too_large)?;
        if let Some(state_len) = states.serialized_len(&identity) {
            len = len
                .checked_add(4 + state_len)
                .ok_or_else(state_cache_delta_too_large)?;
        }
    }
    Ok(len)
}

fn write_state_cache_map_delta(
    out: &mut impl io::Write,
    states: &dyn StateMap,
    dirty: &HashSet<i64>,
    full: bool,
) -> io::Result<()> {
    let identities = state_cache_delta_identities(states, dirty, full);
    write_snapshot_u32(out, identities.len())?;
    for identity in identities {
        out.write_all(&identity.to_le_bytes())?;
        if let Some(state) = states.state(&identity) {
            out.write_all(&[1])?;
            write_snapshot_module_state(out, &state)?;
        } else {
            out.write_all(&[0])?;
        }
    }
    Ok(())
}

fn state_cache_delta_too_large() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "RWKV state delta is too large")
}

fn restore_serialized_state(
    states: &mut dyn StateMap,
    id: i64,
    state: Option<&[u8]>,
) -> io::Result<()> {
    if let Some(state) = deserialize_module_state(state)? {
        states.put(id, state);
    } else {
        states.take(&id);
    }
    Ok(())
}

fn restore_optional_serialized_state(
    states: &mut dyn StateMap,
    id: Option<i64>,
    state: Option<&[u8]>,
) -> io::Result<()> {
    if let Some(id) = id {
        restore_serialized_state(states, id, state)?;
    }
    Ok(())
}

fn branched_optional_module_state(
    map: &dyn StateMap,
    query_id: Option<i64>,
    answer_id: Option<i64>,
    next_state: &ModuleState,
) -> Option<ModuleState> {
    match query_id {
        Some(id) if Some(id) == answer_id => Some(next_state.clone()),
        Some(id) => map.state(&id).map(Cow::into_owned),
        None => None,
    }
}

/// Identities of the card, note, deck and preset streams used by some inputs.
#[derive(Default)]
struct StreamIds {
    card: HashSet<i64>,
    note: HashSet<i64>,
    deck: HashSet<i64>,
    preset: HashSet<i64>,
}

impl StreamIds {
    fn for_workload_inputs(inputs: &[RwkvWorkloadSimulationInput]) -> Self {
        let mut ids = Self::default();
        for input in inputs {
            let input = &input.review_input;
            ids.card.insert(input.card_id);
            ids.note.extend(input.note_id);
            ids.deck.extend(input.deck_id);
            ids.preset.extend(input.preset_id);
        }
        ids
    }
}

/// Decoded copies of the compressed resident streams most often queried by an
/// Instant forecast. They are immutable and shared by all simulated paths.
#[derive(Default)]
struct ForecastDecodedStates {
    card: HashMap<i64, ModuleState>,
    note: HashMap<i64, ModuleState>,
    bytes: usize,
}

impl ForecastDecodedStates {
    fn new(base: &ReviewStateMaps, ids: &StreamIds, budget: usize) -> io::Result<Self> {
        fn add(
            source: &impl StateMap,
            ids: &HashSet<i64>,
            target: &mut HashMap<i64, ModuleState>,
            bytes: &mut usize,
            budget: usize,
        ) -> io::Result<()> {
            for id in ids {
                if *bytes >= budget {
                    break;
                }
                let Some(state) = source.state(id).filter(|state| state.packed.is_some()) else {
                    continue;
                };
                let decoded = state.try_decoded()?.into_owned();
                let size = forecast_decoded_state_size(&decoded);
                if size <= budget.saturating_sub(*bytes) {
                    target.insert(*id, decoded);
                    *bytes += size;
                }
            }
            Ok(())
        }

        let mut cache = Self::default();
        add(
            &base.card,
            &ids.card,
            &mut cache.card,
            &mut cache.bytes,
            budget,
        )?;
        add(
            &base.note,
            &ids.note,
            &mut cache.note,
            &mut cache.bytes,
            budget,
        )?;
        Ok(cache)
    }
}

fn forecast_decoded_state_size(state: &ModuleState) -> usize {
    // Decoded compact matrices retain their packed representation too. The
    // 50% margin covers that, plus map and Vec allocation metadata.
    serialized_module_state_len(state).saturating_mul(3) / 2
        + 128
        + state.layers.len() * std::mem::size_of::<LayerState>()
}

/// The review states a simulation sees: `base` until the simulation advances
/// a stream, then its own copy of that stream. A simulation therefore holds
/// only the streams it advanced, and never changes `base`.
struct SimulationStates<'a> {
    base: &'a ReviewStateMaps,
    decoded: Option<&'a ForecastDecodedStates>,
    decoded_own_bytes: usize,
    decoded_own_budget: usize,
    card: HashMap<i64, ModuleState>,
    note: HashMap<i64, ModuleState>,
    deck: HashMap<i64, ModuleState>,
    preset: HashMap<i64, ModuleState>,
    global: Option<ModuleState>,
}

impl<'a> SimulationStates<'a> {
    fn new(base: &'a ReviewStateMaps) -> Self {
        Self {
            base,
            decoded: None,
            decoded_own_bytes: 0,
            decoded_own_budget: 0,
            card: HashMap::new(),
            note: HashMap::new(),
            deck: HashMap::new(),
            preset: HashMap::new(),
            global: None,
        }
    }

    fn with_forecast_cache(
        base: &'a ReviewStateMaps,
        decoded: &'a ForecastDecodedStates,
        own_budget: usize,
    ) -> Self {
        let mut states = Self::new(base);
        states.decoded = Some(decoded);
        states.decoded_own_budget = own_budget;
        states
    }

    fn state_view(&self, input: &ReviewInput) -> SrsStateView<'_> {
        fn lookup<'s>(
            own: &'s HashMap<i64, ModuleState>,
            decoded: Option<&'s HashMap<i64, ModuleState>>,
            base: &'s HashMap<i64, ModuleState>,
            id: i64,
        ) -> Option<&'s ModuleState> {
            own.get(&id)
                .or_else(|| decoded.and_then(|states| states.get(&id)))
                .or_else(|| base.get(&id))
        }
        fn lookup_half<'s>(
            own: &'s HashMap<i64, ModuleState>,
            decoded: Option<&'s HashMap<i64, ModuleState>>,
            base: &'s SharedStateMap,
            id: i64,
        ) -> Option<Cow<'s, ModuleState>> {
            own.get(&id)
                .or_else(|| decoded.and_then(|states| states.get(&id)))
                .map(Cow::Borrowed)
                .or_else(|| base.state(&id))
        }
        SrsStateView {
            card: lookup_half(
                &self.card,
                self.decoded.map(|cache| &cache.card),
                &self.base.card,
                input.card_id,
            ),
            note: input.note_id.and_then(|id| {
                lookup_half(
                    &self.note,
                    self.decoded.map(|cache| &cache.note),
                    &self.base.note,
                    id,
                )
            }),
            deck: input
                .deck_id
                .and_then(|id| lookup(&self.deck, None, &self.base.deck, id)),
            preset: input
                .preset_id
                .and_then(|id| lookup(&self.preset, None, &self.base.preset, id)),
            global: self.global.as_ref().or(self.base.global.as_ref()),
        }
    }

    fn store(&mut self, input: &ReviewInput, state: SrsState) -> io::Result<()> {
        fn store_stream(
            states: &mut HashMap<i64, ModuleState>,
            cache: Option<&HashMap<i64, ModuleState>>,
            id: i64,
            state: ModuleState,
            used: &mut usize,
            budget: usize,
        ) -> io::Result<()> {
            let cached = cache.is_some_and(|cache| cache.contains_key(&id));
            let previous = states
                .get(&id)
                .filter(|state| cached && state.packed.is_none());
            let previous_size = previous.map_or(0, forecast_decoded_state_size);
            let state =
                if cached && state.packed.is_some() && (*used < budget || previous.is_some()) {
                    let decoded = state.try_decoded()?.into_owned();
                    let size = forecast_decoded_state_size(&decoded);
                    if size <= budget.saturating_sub(*used).saturating_add(previous_size) {
                        *used = used.saturating_sub(previous_size) + size;
                        decoded
                    } else {
                        *used = used.saturating_sub(previous_size);
                        state
                    }
                } else {
                    *used = used.saturating_sub(previous_size);
                    state
                };
            states.insert(id, state);
            Ok(())
        }
        store_stream(
            &mut self.card,
            self.decoded.map(|cache| &cache.card),
            input.card_id,
            state.card,
            &mut self.decoded_own_bytes,
            self.decoded_own_budget,
        )?;
        if let Some(note_id) = input.note_id {
            store_stream(
                &mut self.note,
                self.decoded.map(|cache| &cache.note),
                note_id,
                state.note,
                &mut self.decoded_own_bytes,
                self.decoded_own_budget,
            )?;
        }
        if let Some(deck_id) = input.deck_id {
            self.deck.insert(deck_id, state.deck);
        }
        if let Some(preset_id) = input.preset_id {
            self.preset.insert(preset_id, state.preset);
        }
        self.global = Some(state.global);
        Ok(())
    }
}

fn deserialize_state_map_subset<M: StateMap + Default>(
    states: &[(i64, Vec<u8>)],
    ids: &HashSet<i64>,
) -> io::Result<M> {
    let mut map = M::default();
    for (key, state) in states {
        if !ids.contains(key) {
            continue;
        }
        if let Some(state) = deserialize_module_state(Some(state.as_slice()))? {
            map.put(*key, state);
        }
    }
    Ok(map)
}

fn deserialize_state_map<M: StateMap + Default>(states: &[(i64, Vec<u8>)]) -> io::Result<M> {
    let mut map = M::default();
    for (key, state) in states {
        let Some(state) = deserialize_module_state(Some(state.as_slice()))? else {
            continue;
        };
        map.put(*key, state);
    }
    Ok(map)
}

fn serialize_state_map(states: &dyn StateMap) -> Vec<(i64, Vec<u8>)> {
    let mut states: Vec<_> = states
        .ids()
        .into_iter()
        .map(|key| (key, serialize_module_state(&states.state(&key).unwrap())))
        .collect();
    states.sort_by_key(|(key, _)| *key);
    states
}

fn serialize_module_state(state: &ModuleState) -> Vec<u8> {
    if let Some(packed) = &state.packed {
        return packed.clone();
    }
    let mut out = Vec::with_capacity(serialized_module_state_len(state));
    out.extend_from_slice(b"ARWKVMODSTATE1");
    write_u32(&mut out, state.layers.len() as u32);
    for layer in &state.layers {
        write_f32_slice(
            &mut out,
            layer
                .time
                .as_ref()
                .map_or(&[][..], |time| time.x_shift.as_slice()),
        );
        write_f32_slice(
            &mut out,
            layer
                .time
                .as_ref()
                .map_or(&[][..], |time| time.matrix.as_slice()),
        );
        write_f32_slice(
            &mut out,
            layer
                .channel_shift
                .as_ref()
                .map_or(&[][..], |shift| shift.as_slice()),
        );
    }
    out
}

fn serialized_module_state_len(state: &ModuleState) -> usize {
    if let Some(packed) = &state.packed {
        return packed.len();
    }
    b"ARWKVMODSTATE1".len()
        + 4
        + state
            .layers
            .iter()
            .map(|layer| {
                12 + 4
                    * (layer.time.as_ref().map_or(0, |time| time.x_shift.len())
                        + layer.time.as_ref().map_or(0, |time| time.matrix.len())
                        + layer.channel_shift.as_ref().map_or(0, |shift| shift.len()))
            })
            .sum::<usize>()
}

fn write_serialized_module_state(out: &mut impl io::Write, state: &ModuleState) -> io::Result<()> {
    if let Some(packed) = &state.packed {
        return out.write_all(packed);
    }
    out.write_all(b"ARWKVMODSTATE1")?;
    write_snapshot_u32(out, state.layers.len())?;
    for layer in &state.layers {
        write_state_cache_f32_slice(
            out,
            layer
                .time
                .as_ref()
                .map_or(&[][..], |time| time.x_shift.as_slice()),
        )?;
        write_state_cache_f32_slice(
            out,
            layer
                .time
                .as_ref()
                .map_or(&[][..], |time| time.matrix.as_slice()),
        )?;
        write_state_cache_f32_slice(
            out,
            layer
                .channel_shift
                .as_ref()
                .map_or(&[][..], |shift| shift.as_slice()),
        )?;
    }
    Ok(())
}

/// u32 layer count, then per layer its time-mix shift, time-mix matrix and
/// channel-mix shift, each as u32 length + IEEE half floats (little endian).
/// The values are small (|x| < 10 in big collections) and a half keeps them to
/// 0.05%; with states rounded like this after every review, the fork's state
/// compression metric gave the same log loss and RMSE as full floats.
fn write_half_module_state(out: &mut impl io::Write, state: &ModuleState) -> io::Result<()> {
    out.write_all(&(state.layers.len() as u32).to_le_bytes())?;
    let mut bytes = Vec::new();
    for layer in &state.layers {
        let time = layer.time.as_ref();
        for values in [
            time.map_or(&[][..], |time| time.x_shift.as_slice()),
            time.map_or(&[][..], |time| time.matrix.as_slice()),
            layer.channel_shift.as_deref().unwrap_or_default(),
        ] {
            bytes.clear();
            bytes.extend_from_slice(&(values.len() as u32).to_le_bytes());
            for value in values {
                bytes.extend_from_slice(&half::f16::from_f32(*value).to_le_bytes());
            }
            out.write_all(&bytes)?;
        }
    }
    Ok(())
}

fn read_u64_le(input: &mut impl io::Read) -> io::Result<u64> {
    let mut bytes = [0_u8; 8];
    input.read_exact(&mut bytes)?;
    Ok(u64::from_le_bytes(bytes))
}

/// Reads what [SharedStateMap::write_file_state] wrote. Like
/// [read_half_module_state], anything but the shape the model gives its
/// states (`layers` layers of the model's sizes) is a damaged file.
fn read_file_module_state(input: &mut impl io::Read, layers: usize) -> io::Result<ModuleState> {
    let damaged = || io::Error::new(io::ErrorKind::InvalidData, "damaged RWKV state");
    let len = read_u64_le(input)?;
    // a compact or f32 state of the biggest module is well under 8 MB
    if len > 8 << 20 {
        return Err(damaged());
    }
    let mut bytes = vec![0; len as usize];
    input.read_exact(&mut bytes)?;
    let state = deserialize_module_state(Some(&bytes))?.ok_or_else(damaged)?;
    let decoded = state.decoded();
    let shape_ok = decoded.layers.len() == layers
        && decoded.layers.iter().all(|layer| {
            layer.time.as_ref().is_some_and(|time| {
                time.x_shift.len() == D_MODEL && time.matrix.len() == HEADS * HEAD_SIZE * HEAD_SIZE
            }) && layer
                .channel_shift
                .as_ref()
                .is_none_or(|c| c.len() == D_MODEL)
        });
    if !shape_ok {
        return Err(damaged());
    }
    drop(decoded);
    Ok(state)
}

/// Reads what [write_half_module_state] wrote; `buffer` is reused between
/// states. The state must have the shape the model gives every state it makes:
/// `layers` layers, each with its x shift (D_MODEL), time-mix matrix (HEADS ×
/// HEAD_SIZE²) and channel shift (D_MODEL); anything else is a damaged file.
fn read_half_module_state(
    input: &mut impl io::Read,
    buffer: &mut Vec<u8>,
    layers: usize,
) -> io::Result<ModuleState> {
    fn damaged() -> io::Error {
        io::Error::new(io::ErrorKind::InvalidData, "damaged RWKV state")
    }
    if read_state_cache_u32(input)? as usize != layers {
        return Err(damaged());
    }
    fn read_values(
        input: &mut impl io::Read,
        buffer: &mut Vec<u8>,
        want: usize,
    ) -> io::Result<Vec<f32>> {
        read_values_or_none(input, buffer, want)?.ok_or_else(damaged)
    }
    /// None for a layer without a channel mixer (no values)
    fn read_values_or_none(
        input: &mut impl io::Read,
        buffer: &mut Vec<u8>,
        want: usize,
    ) -> io::Result<Option<Vec<f32>>> {
        let len = read_state_cache_u32(input)? as usize;
        if len == 0 {
            return Ok(None);
        }
        if len != want {
            return Err(damaged());
        }
        buffer.resize(len * 2, 0);
        input.read_exact(buffer)?;
        Ok(Some(
            buffer
                .as_chunks::<2>()
                .0
                .iter()
                .map(|bytes| half::f16::from_le_bytes(*bytes).to_f32())
                .collect(),
        ))
    }
    let mut states = Vec::with_capacity(layers);
    for _ in 0..layers {
        let x_shift = read_values(input, buffer, D_MODEL)?;
        let matrix = read_values(input, buffer, HEADS * HEAD_SIZE * HEAD_SIZE)?;
        let channel_shift = read_values_or_none(input, buffer, D_MODEL)?;
        states.push(LayerState {
            time: Some(TimeState {
                x_shift,
                matrix,
                packed_matrix: None,
            }),
            channel_shift,
        });
    }
    Ok(ModuleState {
        layers: states,
        packed: None,
    })
}

fn write_snapshot_module_state(out: &mut impl io::Write, state: &ModuleState) -> io::Result<()> {
    write_snapshot_u32(out, serialized_module_state_len(state))?;
    write_serialized_module_state(out, state)
}

fn write_snapshot_state_map(out: &mut impl io::Write, states: &dyn StateMap) -> io::Result<()> {
    write_snapshot_u32(out, states.count())?;
    let mut identities = states.ids();
    identities.sort_unstable();
    for identity in identities {
        out.write_all(&identity.to_le_bytes())?;
        write_snapshot_module_state(out, &states.state(&identity).unwrap())?;
    }
    Ok(())
}

fn paired_streams_differ() -> io::Error {
    io::Error::new(
        io::ErrorKind::InvalidData,
        "RWKV paired models hold different streams",
    )
}

fn write_paired_snapshot_state_map(
    out: &mut impl io::Write,
    release: &impl StateMap,
    instant: &impl StateMap,
) -> io::Result<()> {
    if release.count() != instant.count() {
        return Err(paired_streams_differ());
    }
    write_snapshot_u32(out, release.count())?;
    let mut identities = release.ids();
    identities.sort_unstable();
    for identity in &identities {
        let state = release.state(identity).ok_or_else(paired_streams_differ)?;
        let state = &*state;
        let paired = instant.state(identity).ok_or_else(paired_streams_differ)?;
        let paired = &*paired;
        let release_len = serialized_module_state_len(state);
        let instant_len = serialized_module_state_len(paired);
        out.write_all(&identity.to_le_bytes())?;
        write_snapshot_u32(
            out,
            crate::rwkv::paired::paired_len(release_len, instant_len),
        )?;
        crate::rwkv::paired::write_paired_header(out, release_len)?;
        write_serialized_module_state(out, state)?;
        write_serialized_module_state(out, paired)?;
    }
    Ok(())
}

fn write_snapshot_optional_bytes(out: &mut impl io::Write, value: Option<&[u8]>) -> io::Result<()> {
    match value {
        Some(value) => {
            out.write_all(&[1])?;
            write_snapshot_bytes(out, value)
        }
        None => out.write_all(&[0]),
    }
}

fn write_snapshot_bytes(out: &mut impl io::Write, value: &[u8]) -> io::Result<()> {
    write_snapshot_u32(out, value.len())?;
    out.write_all(value)
}

fn write_snapshot_u32(out: &mut impl io::Write, value: usize) -> io::Result<()> {
    let value = u32::try_from(value)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "RWKV state is too large"))?;
    out.write_all(&value.to_le_bytes())
}

fn open_state_cache_store(
    path: &PathBuf,
    store_generation: &str,
    durable: bool,
) -> io::Result<Connection> {
    let connection = Connection::open(path).map_err(state_cache_store_error)?;
    if !durable {
        connection
            .execute_batch(
                "pragma journal_mode = off;
                 pragma synchronous = off;
                 pragma locking_mode = exclusive;",
            )
            .map_err(state_cache_store_error)?;
    } else {
        connection
            .execute_batch(
                "pragma journal_mode = delete;
                 pragma synchronous = full;",
            )
            .map_err(state_cache_store_error)?;
    }
    let schema_version = connection
        .pragma_query_value(None, "user_version", |row| row.get::<_, i64>(0))
        .map_err(state_cache_store_error)?;
    if schema_version == 0 {
        connection
            .pragma_update(None, "page_size", STATE_CACHE_STORE_PAGE_SIZE)
            .map_err(state_cache_store_error)?;
        connection
            .execute_batch(
                r#"
create table store_metadata (
  key text primary key,
  value text not null
) without rowid;
create table segments (
  id integer primary key,
  parent_id integer references segments(id),
  last_review_id integer not null,
  review_count integer not null,
  history_hash text not null,
  replay_key text not null,
  previous_review_ids blob not null,
  previous_intervals blob not null,
  review_counts blob not null,
  runtime_state blob not null
);
create table segment_state_chunks (
  id integer primary key,
  segment_id integer not null references segments(id) on delete cascade,
  chunk_index integer not null,
  state_delta blob not null,
  unique (segment_id, chunk_index)
);
"#,
            )
            .map_err(state_cache_store_error)?;
        connection
            .execute(
                "insert into store_metadata (key, value) values ('generation', ?)",
                [store_generation],
            )
            .map_err(state_cache_store_error)?;
        connection
            .pragma_update(None, "user_version", STATE_CACHE_STORE_SCHEMA_VERSION)
            .map_err(state_cache_store_error)?;
    }
    validate_state_cache_store(&connection, store_generation)?;
    // Reapply the byte budget after the page size is known. SQLite's page-size
    // change initializes the new cache capacity using the old page size.
    // Otherwise its -2000 KiB budget retains hundreds of 64 KiB pages.
    connection
        .pragma_update(None, "cache_size", -2000)
        .map_err(state_cache_store_error)?;
    Ok(connection)
}

fn validate_state_cache_store(connection: &Connection, store_generation: &str) -> io::Result<()> {
    let schema_version = connection
        .pragma_query_value(None, "user_version", |row| row.get::<_, i64>(0))
        .map_err(state_cache_store_error)?;
    if schema_version != STATE_CACHE_STORE_SCHEMA_VERSION {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("unsupported RWKV state-cache store schema {schema_version}"),
        ));
    }
    let actual_generation = connection
        .query_row(
            "select value from store_metadata where key = 'generation'",
            [],
            |row| row.get::<_, String>(0),
        )
        .optional()
        .map_err(state_cache_store_error)?;
    if actual_generation.as_deref() != Some(store_generation) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "RWKV state-cache store generation mismatch",
        ));
    }
    Ok(())
}

fn validate_state_cache_parent(
    transaction: &Transaction<'_>,
    parent_segment_id: Option<i64>,
) -> io::Result<()> {
    let Some(parent_segment_id) = parent_segment_id else {
        return Ok(());
    };
    let parent_exists = transaction
        .query_row(
            "select 1 from segments where id = ?",
            [parent_segment_id],
            |_| Ok(()),
        )
        .optional()
        .map_err(state_cache_store_error)?
        .is_some();
    if !parent_exists {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "missing RWKV state-cache parent segment",
        ));
    }
    Ok(())
}

fn insert_state_cache_delta_chunks(
    transaction: &Transaction<'_>,
    segment_id: i64,
    state_delta_len: usize,
    chunk_bytes: usize,
    first_chunk_index: i64,
) -> io::Result<Vec<i64>> {
    let mut chunk_ids = Vec::with_capacity(state_delta_len.div_ceil(chunk_bytes));
    let mut remaining = state_delta_len;
    let mut chunk_index = first_chunk_index;
    while remaining > 0 {
        let chunk_len = remaining.min(chunk_bytes);
        transaction
            .execute(
                r#"
insert into segment_state_chunks (segment_id, chunk_index, state_delta)
values (?, ?, zeroblob(?))
"#,
                params![
                    segment_id,
                    chunk_index,
                    i64::try_from(chunk_len).map_err(|_| state_cache_delta_too_large())?,
                ],
            )
            .map_err(state_cache_store_error)?;
        chunk_ids.push(transaction.last_insert_rowid());
        remaining -= chunk_len;
        chunk_index += 1;
    }
    if chunk_ids.is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "empty RWKV state delta",
        ));
    }
    Ok(chunk_ids)
}

struct StateCacheDeltaChunkWriter<'conn> {
    blob: Blob<'conn>,
    remaining_chunk_ids: std::vec::IntoIter<i64>,
    expected_len: usize,
    written: usize,
}

impl<'conn> StateCacheDeltaChunkWriter<'conn> {
    fn new(
        connection: &'conn Connection,
        chunk_ids: Vec<i64>,
        expected_len: usize,
    ) -> io::Result<Self> {
        let mut remaining_chunk_ids = chunk_ids.into_iter();
        let first_chunk_id = remaining_chunk_ids.next().ok_or_else(|| {
            io::Error::new(io::ErrorKind::InvalidData, "missing RWKV state delta chunk")
        })?;
        let blob = connection
            .blob_open(
                MAIN_DB,
                "segment_state_chunks",
                "state_delta",
                first_chunk_id,
                false,
            )
            .map_err(state_cache_store_error)?;
        Ok(Self {
            blob,
            remaining_chunk_ids,
            expected_len,
            written: 0,
        })
    }

    fn finish(mut self) -> io::Result<()> {
        if self.written != self.expected_len || self.remaining_chunk_ids.next().is_some() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "incomplete RWKV state delta",
            ));
        }
        self.blob.close().map_err(state_cache_store_error)
    }
}

impl io::Write for StateCacheDeltaChunkWriter<'_> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let mut written = 0;
        while written < buf.len() {
            let chunk_written = io::Write::write(&mut self.blob, &buf[written..])?;
            if chunk_written == 0 {
                let next_chunk_id = self.remaining_chunk_ids.next().ok_or_else(|| {
                    io::Error::new(
                        io::ErrorKind::WriteZero,
                        "RWKV state delta exceeds allocated chunks",
                    )
                })?;
                self.blob
                    .reopen(next_chunk_id)
                    .map_err(state_cache_store_error)?;
            } else {
                written += chunk_written;
                self.written += chunk_written;
            }
        }
        Ok(written)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

/// The chunks of one state delta: a paired runtime stores the instant
/// model's delta from `paired::INSTANT_CHUNK_BASE`, the release's from 0.
fn state_cache_delta_chunk_ids(
    connection: &Connection,
    segment_id: i64,
    first_chunk_index: i64,
) -> io::Result<Vec<i64>> {
    let mut statement = connection
        .prepare(
            r#"
select id, chunk_index
from segment_state_chunks
where segment_id = ? and chunk_index >= ? and chunk_index < ?
order by chunk_index
"#,
        )
        .map_err(state_cache_store_error)?;
    let rows = statement
        .query_map(
            params![
                segment_id,
                first_chunk_index,
                first_chunk_index + crate::rwkv::paired::INSTANT_CHUNK_BASE
            ],
            |row| Ok((row.get::<_, i64>(0)?, row.get::<_, i64>(1)?)),
        )
        .map_err(state_cache_store_error)?;
    let mut chunk_ids = Vec::new();
    for (expected_index, row) in rows.enumerate() {
        let (chunk_id, chunk_index) = row.map_err(state_cache_store_error)?;
        if chunk_index != first_chunk_index + expected_index as i64 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid RWKV state delta chunk sequence",
            ));
        }
        chunk_ids.push(chunk_id);
    }
    if chunk_ids.is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "missing RWKV state delta chunks",
        ));
    }
    Ok(chunk_ids)
}

struct StateCacheDeltaChunkReader<'conn> {
    blob: Blob<'conn>,
    remaining_chunk_ids: std::vec::IntoIter<i64>,
    /// Length of the chunks before the open one, for stream positions.
    previous_chunks_len: u64,
}

impl<'conn> StateCacheDeltaChunkReader<'conn> {
    fn new(connection: &'conn Connection, chunk_ids: Vec<i64>) -> io::Result<Self> {
        let mut remaining_chunk_ids = chunk_ids.into_iter();
        let first_chunk_id = remaining_chunk_ids.next().ok_or_else(|| {
            io::Error::new(io::ErrorKind::InvalidData, "missing RWKV state delta chunk")
        })?;
        let blob = connection
            .blob_open(
                MAIN_DB,
                "segment_state_chunks",
                "state_delta",
                first_chunk_id,
                true,
            )
            .map_err(state_cache_store_error)?;
        Ok(Self {
            blob,
            remaining_chunk_ids,
            previous_chunks_len: 0,
        })
    }

    /// Moves to the start of the next chunk; false when none is left.
    fn next_chunk(&mut self) -> io::Result<bool> {
        let Some(next_chunk_id) = self.remaining_chunk_ids.next() else {
            return Ok(false);
        };
        self.previous_chunks_len += self.blob.len() as u64;
        self.blob
            .reopen(next_chunk_id)
            .map_err(state_cache_store_error)?;
        Ok(true)
    }
}

impl io::Read for StateCacheDeltaChunkReader<'_> {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let mut read = 0;
        while read < buf.len() {
            let chunk_read = io::Read::read(&mut self.blob, &mut buf[read..])?;
            if chunk_read == 0 {
                if !self.next_chunk()? {
                    break;
                }
            } else {
                read += chunk_read;
            }
        }
        Ok(read)
    }
}

/// Forward seeks only, which is all that skipping shadowed entries needs.
impl io::Seek for StateCacheDeltaChunkReader<'_> {
    fn seek(&mut self, position: io::SeekFrom) -> io::Result<u64> {
        let io::SeekFrom::Current(mut offset @ 0..) = position else {
            return Err(io::Error::new(
                io::ErrorKind::Unsupported,
                "RWKV state delta reader only seeks forward",
            ));
        };
        loop {
            let chunk_position = self.blob.stream_position()?;
            let chunk_remaining = self.blob.len() as u64 - chunk_position;
            if offset as u64 <= chunk_remaining {
                let chunk_position = self.blob.seek(io::SeekFrom::Current(offset))?;
                return Ok(self.previous_chunks_len + chunk_position);
            }
            offset -= chunk_remaining as i64;
            if !self.next_chunk()? {
                return Err(io::Error::new(
                    io::ErrorKind::UnexpectedEof,
                    "RWKV state delta ended early",
                ));
            }
        }
    }
}

fn state_cache_segment_chain(
    connection: &Connection,
    target_segment_id: i64,
) -> io::Result<Vec<i64>> {
    let mut chain = Vec::new();
    let mut seen = HashSet::new();
    let mut current = Some(target_segment_id);
    while let Some(segment_id) = current {
        if !seen.insert(segment_id) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "cyclic RWKV state-cache segment chain",
            ));
        }
        let parent = connection
            .query_row(
                "select parent_id from segments where id = ?",
                [segment_id],
                |row| row.get::<_, Option<i64>>(0),
            )
            .optional()
            .map_err(state_cache_store_error)?
            .ok_or_else(|| {
                io::Error::new(
                    io::ErrorKind::InvalidData,
                    "missing RWKV state-cache segment",
                )
            })?;
        chain.push(segment_id);
        current = parent;
    }
    Ok(chain)
}

fn read_state_cache_maps(
    connection: &Connection,
    segment_chain: &[i64],
    first_chunk_index: i64,
) -> io::Result<ReviewStateMaps> {
    let mut result = ReviewStateMaps::default();
    let mut seen_card = HashSet::new();
    let mut seen_note = HashSet::new();
    let mut seen_deck = HashSet::new();
    let mut seen_preset = HashSet::new();
    let mut seen_global = false;
    let mut state_bytes = Vec::new();
    for segment_id in segment_chain {
        let chunk_ids = state_cache_delta_chunk_ids(connection, *segment_id, first_chunk_index)?;
        // SQLite streams large blob reads close to disk speed, while small
        // reads make a cold restore several times slower.
        let mut state_delta = io::BufReader::with_capacity(
            STATE_CACHE_READ_BUFFER_BYTES,
            StateCacheDeltaChunkReader::new(connection, chunk_ids)?,
        );
        let mut magic = vec![0; STATE_CACHE_DELTA_MAGIC.len()];
        io::Read::read_exact(&mut state_delta, &mut magic)?;
        if magic != STATE_CACHE_DELTA_MAGIC {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid RWKV state-cache delta header",
            ));
        }
        let maps: [(&mut dyn StateMap, _); 4] = [
            (&mut result.card, &mut seen_card),
            (&mut result.note, &mut seen_note),
            (&mut result.deck, &mut seen_deck),
            (&mut result.preset, &mut seen_preset),
        ];
        for (states, seen) in maps {
            read_state_cache_map_delta(&mut state_delta, states, seen, &mut state_bytes)?;
        }
        match read_state_cache_u8(&mut state_delta)? {
            0 => {}
            1 => {
                if !seen_global {
                    seen_global = true;
                    result.global = None;
                }
            }
            2 => {
                let size = read_state_cache_u32(&mut state_delta)?;
                if seen_global {
                    io::Seek::seek_relative(&mut state_delta, size.into())?;
                } else {
                    seen_global = true;
                    result.global =
                        read_state_cache_module_state(&mut state_delta, size, &mut state_bytes)?;
                }
            }
            _ => return Err(invalid_state_cache_delta_marker()),
        }
        let mut trailing = [0];
        if io::Read::read(&mut state_delta, &mut trailing)? != 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "trailing RWKV state-cache delta data",
            ));
        }
    }
    Ok(result)
}

fn read_state_cache_map_delta(
    input: &mut (impl io::Read + io::Seek),
    states: &mut dyn StateMap,
    seen: &mut HashSet<i64>,
    state_bytes: &mut Vec<u8>,
) -> io::Result<()> {
    for _ in 0..read_state_cache_u32(input)? {
        let identity = read_state_cache_i64(input)?;
        match read_state_cache_u8(input)? {
            0 => {
                seen.insert(identity);
            }
            1 => {
                let size = read_state_cache_u32(input)?;
                if seen.insert(identity) {
                    if let Some(state) = read_state_cache_module_state(input, size, state_bytes)? {
                        states.put(identity, state);
                    }
                } else {
                    // A newer segment already provided this state.
                    input.seek_relative(size.into())?;
                }
            }
            _ => return Err(invalid_state_cache_delta_marker()),
        }
    }
    Ok(())
}

fn read_state_cache_module_state(
    input: &mut impl io::Read,
    size: u32,
    state_bytes: &mut Vec<u8>,
) -> io::Result<Option<ModuleState>> {
    state_bytes.resize(size as usize, 0);
    io::Read::read_exact(input, state_bytes)?;
    deserialize_module_state(Some(state_bytes))
}

fn read_state_cache_u8(input: &mut impl io::Read) -> io::Result<u8> {
    let mut value = [0];
    io::Read::read_exact(input, &mut value)?;
    Ok(value[0])
}

fn read_state_cache_u32(input: &mut impl io::Read) -> io::Result<u32> {
    let mut value = [0; 4];
    io::Read::read_exact(input, &mut value)?;
    Ok(u32::from_le_bytes(value))
}

fn read_state_cache_i64(input: &mut impl io::Read) -> io::Result<i64> {
    let mut value = [0; 8];
    io::Read::read_exact(input, &mut value)?;
    Ok(i64::from_le_bytes(value))
}

fn invalid_state_cache_delta_marker() -> io::Error {
    io::Error::new(
        io::ErrorKind::InvalidData,
        "invalid RWKV state-cache delta marker",
    )
}

fn state_cache_store_error(error: rusqlite::Error) -> io::Error {
    io::Error::other(error)
}

fn deserialize_module_state(bytes: Option<&[u8]>) -> io::Result<Option<ModuleState>> {
    let Some(bytes) = bytes else {
        return Ok(None);
    };
    if bytes.starts_with(precision::MAGIC) {
        // Validate before retaining an opaque packed state. Later inference can
        // decode it infallibly into bounded, short-lived scratch storage.
        precision::decode(bytes)?;
        return Ok(Some(ModuleState {
            layers: Vec::new(),
            packed: Some(bytes.to_vec()),
        }));
    }
    let mut cursor = Cursor::new(bytes);
    cursor.expect_magic(b"ARWKVMODSTATE1")?;
    let layer_count = cursor.u32()? as usize;
    if layer_count > MODULE_LAYERS.iter().copied().max().unwrap() {
        return Err(WarmUpStateSpill::invalid_data());
    }
    let mut layers = Vec::with_capacity(layer_count);
    for _ in 0..layer_count {
        let x_shift = cursor.f32_vec()?;
        let matrix = cursor.f32_vec()?;
        let channel_shift = cursor.f32_vec()?;
        layers.push(LayerState {
            time: Some(TimeState {
                x_shift,
                matrix,
                packed_matrix: None,
            }),
            channel_shift: (!channel_shift.is_empty()).then_some(channel_shift),
        });
    }
    cursor.expect_end()?;
    Ok(Some(ModuleState {
        layers,
        packed: None,
    }))
}

fn write_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn state_cache_optional_i64_len(value: Option<i64>) -> usize {
    if value.is_some() {
        9
    } else {
        1
    }
}

fn state_cache_i64_set_len(count: usize) -> usize {
    4 + 8 * count
}

fn state_cache_i64_map_len(count: usize) -> usize {
    4 + 16 * count
}

fn state_cache_id_encodings_len(values: &HashMap<(IdKind, i64), Vec<f32>>) -> usize {
    4 + values
        .values()
        .map(|encoding| 13 + 4 * encoding.len())
        .sum::<usize>()
}

fn write_state_cache_u32(out: &mut impl io::Write, value: usize) -> io::Result<()> {
    let value = u32::try_from(value)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "RWKV state is too large"))?;
    out.write_all(&value.to_le_bytes())
}

fn write_state_cache_optional_i64(out: &mut impl io::Write, value: Option<i64>) -> io::Result<()> {
    match value {
        Some(value) => {
            out.write_all(&[1])?;
            out.write_all(&value.to_le_bytes())
        }
        None => out.write_all(&[0]),
    }
}

fn write_state_cache_i64_set(
    out: &mut impl io::Write,
    values: impl Iterator<Item = i64>,
) -> io::Result<()> {
    let mut values: Vec<_> = values.collect();
    values.sort_unstable();
    write_state_cache_u32(out, values.len())?;
    for value in values {
        out.write_all(&value.to_le_bytes())?;
    }
    Ok(())
}

fn read_i64_set(cursor: &mut Cursor<'_>) -> io::Result<HashMap<i64, ()>> {
    let count = cursor.count(8)?;
    let mut values = HashMap::with_capacity(count);
    for _ in 0..count {
        values.insert(cursor.i64()?, ());
    }
    Ok(values)
}

fn write_state_cache_i64_map(
    out: &mut impl io::Write,
    values: &HashMap<i64, i64>,
) -> io::Result<()> {
    let mut values: Vec<_> = values.iter().collect();
    values.sort_by_key(|(key, _)| *key);
    write_state_cache_u32(out, values.len())?;
    for (key, value) in values {
        out.write_all(&key.to_le_bytes())?;
        out.write_all(&value.to_le_bytes())?;
    }
    Ok(())
}

fn read_i64_map(cursor: &mut Cursor<'_>) -> io::Result<HashMap<i64, i64>> {
    let count = cursor.count(16)?;
    let mut values = HashMap::with_capacity(count);
    for _ in 0..count {
        values.insert(cursor.i64()?, cursor.i64()?);
    }
    Ok(values)
}

fn write_f32_slice(out: &mut Vec<u8>, values: &[f32]) {
    write_u32(out, values.len() as u32);
    let start = out.len();
    out.resize(start + std::mem::size_of_val(values), 0);
    for (bytes, value) in out[start..]
        .as_chunks_mut::<{ std::mem::size_of::<f32>() }>()
        .0
        .iter_mut()
        .zip(values)
    {
        bytes.copy_from_slice(&value.to_le_bytes());
    }
}

fn write_state_cache_f32_slice(out: &mut impl io::Write, values: &[f32]) -> io::Result<()> {
    write_state_cache_u32(out, values.len())?;
    let mut buffer = [0_u8; 8192];
    for values in values.chunks(buffer.len() / 4) {
        for (destination, value) in buffer.as_chunks_mut::<4>().0.iter_mut().zip(values) {
            destination.copy_from_slice(&value.to_le_bytes());
        }
        out.write_all(&buffer[..values.len() * 4])?;
    }
    Ok(())
}

fn write_state_cache_id_encodings(
    out: &mut impl io::Write,
    values: &HashMap<(IdKind, i64), Vec<f32>>,
) -> io::Result<()> {
    let mut values: Vec<_> = values.iter().collect();
    values.sort_by_key(|((kind, value), _)| (kind.cache_code(), *value));
    write_state_cache_u32(out, values.len())?;
    for ((kind, value), encoding) in values {
        out.write_all(&[kind.cache_code()])?;
        out.write_all(&value.to_le_bytes())?;
        write_state_cache_f32_slice(out, encoding)?;
    }
    Ok(())
}

fn interval_for_curve(
    curve: &ReviewCurve,
    target_retention: f32,
    max_interval_days: u32,
) -> Option<u32> {
    unrounded_interval_for_curve(curve, target_retention, max_interval_days)
        .map(|days| clamped_interval_days(days, max_interval_days))
}

fn unrounded_interval_for_curve(
    curve: &ReviewCurve,
    target_retention: f32,
    max_interval_days: u32,
) -> Option<f32> {
    if !(0.0..=1.0).contains(&target_retention) || max_interval_days < 1 {
        return None;
    }
    let margins_at =
        |days: f32| [predict_curve(curve, days * SECONDS_PER_DAY as f32) - target_retention];
    let [crossing] = crossings_on_grid(max_interval_days, margins_at);
    Some(crossing)
}

const SUB_DAY_SEARCH_POINTS: [f32; 15] = [
    // Already-at-target curves return this earliest supported model time.
    1.0 / SECONDS_PER_DAY as f32,
    1.0 / 1440.0,
    5.0 / 1440.0,
    10.0 / 1440.0,
    20.0 / 1440.0,
    30.0 / 1440.0,
    1.0 / 24.0,
    2.0 / 24.0,
    3.0 / 24.0,
    4.0 / 24.0,
    6.0 / 24.0,
    8.0 / 24.0,
    12.0 / 24.0,
    16.0 / 24.0,
    20.0 / 24.0,
];

fn unrounded_intervals_for_answer_curves(
    curves: [&ReviewCurve; 4],
    target_retentions: [f32; 4],
    max_interval_days: u32,
    enforce_grade_order: bool,
) -> [Option<f32>; 4] {
    let valid = target_retentions.map(|target| (0.0..=1.0).contains(&target));
    if max_interval_days < 1 || (enforce_grade_order && valid.iter().any(|valid| !valid)) {
        return [None; 4];
    }
    let margins_at = |days: f32| -> [f32; 4] {
        let margins = std::array::from_fn(|index| {
            predict_curve(curves[index], days * SECONDS_PER_DAY as f32) - target_retentions[index]
        });
        if enforce_grade_order {
            pava_non_decreasing(margins)
        } else {
            margins
        }
    };
    let crossings = crossings_on_grid(max_interval_days, margins_at);
    std::array::from_fn(|index| valid[index].then_some(crossings[index]))
}

fn crossings_on_grid<const N: usize>(
    max_interval_days: u32,
    margins_at: impl Fn(f32) -> [f32; N],
) -> [f32; N] {
    let mut points = Vec::new();
    if margins_at(1.0).iter().any(|margin| *margin <= 0.0) {
        points.extend(SUB_DAY_SEARCH_POINTS);
    }
    points.extend(
        interval_search_days(max_interval_days)
            .into_iter()
            .map(|day| day as f32),
    );

    let maximum = max_interval_days as f32;
    let mut crossings: [Option<f32>; N] = [None; N];
    let mut previous: [Option<(f32, f32)>; N] = [None; N];
    let mut at_zero: Option<[f32; N]> = None;
    for point in points {
        let margins = margins_at(point);
        for index in 0..N {
            if crossings[index].is_some() {
                continue;
            }
            let margin = margins[index];
            if margin <= 0.0 {
                let (low, low_margin) = previous[index].unwrap_or_else(|| {
                    (0.0, at_zero.get_or_insert_with(|| margins_at(0.0))[index])
                });
                let crossing = if low_margin > 0.0 {
                    crossing_between(low, low_margin, point, margin, |days| {
                        margins_at(days)[index]
                    })
                } else {
                    point
                };
                crossings[index] = Some(crossing.min(maximum));
            }
            previous[index] = Some((point, margin));
        }
        if crossings.iter().all(Option::is_some) {
            break;
        }
    }
    crossings.map(|crossing| crossing.unwrap_or(maximum))
}

fn crossing_between(
    low: f32,
    low_margin: f32,
    high: f32,
    high_margin: f32,
    margin: impl Fn(f32) -> f32,
) -> f32 {
    const MAX_STEPS: usize = 64;
    let (mut low, mut low_margin) = (low as f64, low_margin as f64);
    let (mut high, mut high_margin) = (high as f64, high_margin as f64);
    let mut last_moved = 0;
    for _ in 0..MAX_STEPS {
        let tolerance = (1.0 / SECONDS_PER_DAY as f64).max(high * 1e-6);
        if high - low <= tolerance || low_margin <= high_margin {
            break;
        }
        let mut day = (low * high_margin - high * low_margin) / (high_margin - low_margin);
        if !(day > low && day < high) {
            day = 0.5 * (low + high);
        }
        let day_margin = margin(day as f32) as f64;
        if day_margin > 0.0 {
            low = day;
            low_margin = day_margin;
            if last_moved == -1 {
                high_margin *= 0.5;
            }
            last_moved = -1;
        } else {
            high = day;
            high_margin = day_margin;
            if last_moved == 1 {
                low_margin *= 0.5;
            }
            last_moved = 1;
        }
    }
    high as f32
}

fn pava_non_decreasing(values: [f32; 4]) -> [f32; 4] {
    #[derive(Clone, Copy)]
    struct Block {
        first: usize,
        last: usize,
        sum: f32,
        count: usize,
    }

    impl Block {
        fn mean(self) -> f32 {
            self.sum / self.count as f32
        }
    }

    let empty_block = Block {
        first: 0,
        last: 0,
        sum: 0.0,
        count: 0,
    };
    let mut blocks = [empty_block; 4];
    let mut block_count = 0;
    for (index, value) in values.into_iter().enumerate() {
        blocks[block_count] = Block {
            first: index,
            last: index,
            sum: value,
            count: 1,
        };
        block_count += 1;
        while block_count >= 2 {
            let right = blocks[block_count - 1];
            let left = blocks[block_count - 2];
            if left.mean() <= right.mean() {
                break;
            }

            blocks[block_count - 2] = Block {
                first: left.first,
                last: right.last,
                sum: left.sum + right.sum,
                count: left.count + right.count,
            };
            block_count -= 1;
        }
    }

    let mut adjusted = [0.0; 4];
    for block in &blocks[..block_count] {
        adjusted[block.first..=block.last].fill(block.mean());
    }
    adjusted
}

fn interval_search_days(max_interval_days: u32) -> Vec<u32> {
    if max_interval_days <= 30 {
        return (1..=max_interval_days).collect();
    }

    let mut days = (1..=30).collect::<Vec<_>>();
    let mut day = 45;
    while day < max_interval_days {
        days.push(day);
        day = ((day as f32) * 1.5) as u32;
    }
    days.push(max_interval_days);
    days
}

fn clamped_interval_days(elapsed_days: f32, max_interval_days: u32) -> u32 {
    elapsed_days.ceil().clamp(1.0, max_interval_days as f32) as u32
}

fn current_curve_retrievability(input: &ReviewInput, curve: &ReviewCurve) -> Option<f32> {
    let elapsed_seconds = input
        .current_elapsed_seconds
        .filter(|seconds| *seconds >= 0)
        .or_else(|| {
            input
                .current_elapsed_days
                .filter(|days| *days >= 0)
                .map(|days| days.saturating_mul(SECONDS_PER_DAY))
        })?;
    Some(predict_curve(curve, elapsed_seconds as f32))
}

fn predict_curve(curve: &ReviewCurve, elapsed_seconds: f32) -> f32 {
    let elapsed_seconds = elapsed_seconds.max(1.0);
    if let Some((scales, decays)) = &curve.power {
        let raw: f32 = curve
            .weights
            .iter()
            .zip(scales)
            .zip(decays)
            .map(|((&w, &s), &d)| {
                w * (-d.clamp(-25.0, 25.0).exp()
                    * (elapsed_seconds / (1e-7 + s.clamp(-25.0, 25.0).exp())).ln_1p())
                .exp()
            })
            .sum();
        let raw = 1e-5 + (1.0 - 2e-5) * raw;
        return sigmoid((raw / (1.0 - raw)).ln() + 1e-5);
    }
    let mut raw_probability = 0.0;
    for (index, weight) in curve.weights.iter().enumerate() {
        let s_space_raw = linspace_exp(index, 128, 18.5);
        let s_space = 0.1 + (s_space_raw - 1.0) * (22.0_f32 - 18.5).exp();
        raw_probability += weight * 0.9_f32.powf(elapsed_seconds / s_space);
    }
    1e-5 + (1.0 - 2e-5) * raw_probability
}

fn linspace_exp(index: usize, count: usize, point_spread: f32) -> f32 {
    let value = if count <= 1 {
        0.0
    } else {
        point_spread * index as f32 / (count - 1) as f32
    };
    value.exp()
}

struct RwkvModule {
    layers: Vec<RwkvLayer>,
    precision: StatePrecision,
}

/// Scratch of the one-at-a-time query path: one value residual per module.
#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
struct ModuleQueryScratch {
    next: [f32; D_MODEL],
    layer: LayerQueryScratch,
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
#[derive(Default)]
struct ModuleQueryBatchScratch {
    next: Vec<f32>,
    layer: LayerQueryBatchScratch,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
impl Default for ModuleQueryScratch {
    fn default() -> Self {
        Self {
            next: [0.0; D_MODEL],
            layer: LayerQueryScratch::default(),
        }
    }
}

impl RwkvModule {
    fn load(weights: &WeightMap, module_id: usize, layer_count: usize) -> io::Result<Self> {
        let mut layers = Vec::with_capacity(layer_count);
        for layer_id in 0..layer_count {
            layers.push(RwkvLayer::load(weights, module_id, layer_id)?);
        }
        Ok(Self {
            layers,
            precision: StatePrecision::F32,
        })
    }
}

#[derive(Clone)]
struct ModuleState {
    layers: Vec<LayerState>,
    /// Compact states have no decoded layers; queries decode only their batch.
    packed: Option<Vec<u8>>,
}

struct RwkvLayer {
    time_mixer: TimeMixer,
    channel_mixer: Option<ChannelMixer>,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
struct LayerQueryScratch {
    time_output: [f32; D_MODEL],
    time: TimeMixerQueryScratch,
    channel: ChannelMixerQueryScratch,
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
#[derive(Default)]
struct LayerQueryBatchScratch {
    time_output: Vec<f32>,
    time: TimeMixerQueryBatchScratch,
    channel: ChannelMixerQueryBatchScratch,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
impl Default for LayerQueryScratch {
    fn default() -> Self {
        Self {
            time_output: [0.0; D_MODEL],
            time: TimeMixerQueryScratch::default(),
            channel: ChannelMixerQueryScratch::default(),
        }
    }
}

impl RwkvLayer {
    fn load(weights: &WeightMap, module_id: usize, layer_id: usize) -> io::Result<Self> {
        Ok(Self {
            time_mixer: TimeMixer::load(weights, module_id, layer_id)?,
            channel_mixer: if channel_mixer_present(module_id, layer_id) {
                Some(ChannelMixer::load(weights, module_id, layer_id)?)
            } else {
                None
            },
        })
    }

    fn run(
        &self,
        input: &[f32],
        v0: &[f32],
        state: Option<&LayerState>,
    ) -> (Vec<f32>, Vec<f32>, LayerState) {
        let (x, v0, time_state) =
            self.time_mixer
                .run(input, v0, state.and_then(|state| state.time.as_ref()));
        let (x, channel_shift) = match &self.channel_mixer {
            Some(mixer) => {
                let (x, shift) =
                    mixer.run(&x, state.and_then(|state| state.channel_shift.as_ref()));
                (x, Some(shift))
            }
            None => (x, None),
        };
        (
            x,
            v0,
            LayerState {
                time: Some(time_state),
                channel_shift,
            },
        )
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn run_query_into(
        &self,
        input: &[f32],
        v0: &mut [f32; D_MODEL],
        state: Option<&LayerState>,
        out: &mut [f32; D_MODEL],
        scratch: &mut LayerQueryScratch,
    ) {
        self.time_mixer.run_query_into(
            input,
            v0,
            state.and_then(|state| state.time.as_ref()),
            &mut scratch.time_output,
            &mut scratch.time,
        );
        let Some(mixer) = &self.channel_mixer else {
            *out = scratch.time_output;
            return;
        };
        mixer.run_query_into(
            &scratch.time_output,
            state.and_then(|state| state.channel_shift.as_deref()),
            out,
            &mut scratch.channel,
        );
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    #[allow(clippy::too_many_arguments)]
    fn run_query_batch(
        &self,
        input: &[f32],
        v0: &mut [f32],
        items: &[ReviewPredictionQueryRef<'_>],
        module_id: usize,
        layer_id: usize,
        out: &mut Vec<f32>,
        scratch: &mut LayerQueryBatchScratch,
    ) {
        self.time_mixer.run_query_batch(
            input,
            v0,
            items,
            module_id,
            layer_id,
            &mut scratch.time_output,
            &mut scratch.time,
        );
        let Some(mixer) = &self.channel_mixer else {
            out.clone_from(&scratch.time_output);
            return;
        };
        mixer.run_query_batch(
            &scratch.time_output,
            items,
            module_id,
            layer_id,
            out,
            &mut scratch.channel,
        );
    }
}

/// Card and note states: one per card and note, so they are most of the RWKV
/// memory (3 GB for a 101k-card collection in f32 on a Pixel 8a). They are
/// kept as the model makes them: compact (int8 by default, [StatePrecision])
/// and shared (`Arc`), so copying a map copies pointers: the state file is
/// written from such a copy on another thread ([RwkvStateSnapshot]). A state
/// is never changed in place, only replaced, so the copy stays as it was.
#[derive(Clone, Default)]
struct SharedStateMap(HashMap<i64, Arc<ModuleState>>);

/// Queries at a time when scoring many cards: compact states are decoded
/// for their batch only.
const STATE_VIEW_CHUNK: usize = 512;

/// What the state helpers need from a map of states, f32 or half.
trait StateMap {
    fn state(&self, id: &i64) -> Option<Cow<'_, ModuleState>>;
    fn put(&mut self, id: i64, state: ModuleState);
    fn take(&mut self, id: &i64) -> Option<ModuleState>;
    fn ids(&self) -> Vec<i64>;
    fn count(&self) -> usize;
    /// The length [serialize_module_state] gives the state.
    fn serialized_len(&self, id: &i64) -> Option<usize>;
    /// Writes the state (it must be there) for kuma3's offline state file, as
    /// [write_half_module_state] does.
    fn write_file_state(&self, id: &i64, mut out: &mut dyn io::Write) -> io::Result<()> {
        write_half_module_state(&mut out, &self.state(id).unwrap())
    }
}

impl StateMap for HashMap<i64, ModuleState> {
    fn state(&self, id: &i64) -> Option<Cow<'_, ModuleState>> {
        self.get(id).map(Cow::Borrowed)
    }
    fn put(&mut self, id: i64, state: ModuleState) {
        self.insert(id, state);
    }
    fn take(&mut self, id: &i64) -> Option<ModuleState> {
        self.remove(id)
    }
    fn ids(&self) -> Vec<i64> {
        self.keys().copied().collect()
    }
    fn count(&self) -> usize {
        self.len()
    }
    fn serialized_len(&self, id: &i64) -> Option<usize> {
        self.get(id).map(serialized_module_state_len)
    }
}

impl StateMap for SharedStateMap {
    fn state(&self, id: &i64) -> Option<Cow<'_, ModuleState>> {
        self.0.get(id).map(|state| Cow::Borrowed(&**state))
    }
    fn put(&mut self, id: i64, state: ModuleState) {
        self.0.insert(id, Arc::new(state));
    }
    fn take(&mut self, id: &i64) -> Option<ModuleState> {
        self.0.remove(id).map(Arc::unwrap_or_clone)
    }
    fn ids(&self) -> Vec<i64> {
        self.0.keys().copied().collect()
    }
    fn count(&self) -> usize {
        self.0.len()
    }
    fn serialized_len(&self, id: &i64) -> Option<usize> {
        self.0
            .get(id)
            .map(|state| serialized_module_state_len(state))
    }
    /// As the engine keeps it (u64 length + [serialize_module_state]), so a
    /// compact state is written as it is.
    fn write_file_state(&self, id: &i64, out: &mut dyn io::Write) -> io::Result<()> {
        let bytes = serialize_module_state(&self.0[id]);
        out.write_all(&(bytes.len() as u64).to_le_bytes())?;
        out.write_all(&bytes)
    }
}

/// The fork's tests read card and note maps as `HashMap<i64, ModuleState>`.
#[cfg(test)]
impl std::ops::Index<&i64> for SharedStateMap {
    type Output = ModuleState;
    fn index(&self, id: &i64) -> &ModuleState {
        &self.0[id]
    }
}

#[cfg(test)]
impl<'a> IntoIterator for &'a SharedStateMap {
    type Item = (&'a i64, &'a ModuleState);
    type IntoIter = Box<dyn Iterator<Item = (&'a i64, &'a ModuleState)> + 'a>;
    fn into_iter(self) -> Self::IntoIter {
        Box::new(self.iter())
    }
}

impl SharedStateMap {
    #[cfg(test)]
    fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
    fn len(&self) -> usize {
        self.0.len()
    }
    #[cfg(test)]
    fn capacity(&self) -> usize {
        self.0.capacity()
    }
    fn iter(&self) -> impl Iterator<Item = (&i64, &ModuleState)> {
        self.0.iter().map(|(id, state)| (id, &**state))
    }
    #[cfg(test)]
    fn values(&self) -> impl Iterator<Item = &ModuleState> {
        self.0.values().map(|state| &**state)
    }
}

impl FromIterator<(i64, ModuleState)> for SharedStateMap {
    fn from_iter<T: IntoIterator<Item = (i64, ModuleState)>>(iter: T) -> Self {
        Self(
            iter.into_iter()
                .map(|(id, state)| (id, Arc::new(state)))
                .collect(),
        )
    }
}

#[derive(Clone)]
struct LayerState {
    time: Option<TimeState>,
    channel_shift: Option<Vec<f32>>,
}

struct TimeMixer {
    #[cfg(test)]
    module_id: usize,
    layer_id: usize,
    layer_norm: Norm,
    rkvdag_lerp: Vec<f32>,
    bonus: Vec<f32>,
    w_r: Linear,
    w_k: Linear,
    w_v: Linear,
    w_o: Linear,
    k_scale_linear: Linear,
    v_scale_linear: Linear,
    /// None in layer 0, which takes v as it is
    v_lora: LoraSimple,
    a_lora: LoraSimple,
    d_lora: LoraSimple,
    lora_a_g: Linear,
    lora_b_g: Linear,
    out_group_norm: Norm,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
struct TimeMixerQueryScratch {
    x: [f32; D_MODEL],
    mixed: [[f32; D_MODEL]; 8],
    r: [f32; D_MODEL],
    k: [f32; D_MODEL],
    v: [f32; D_MODEL],
    k_scale: [f32; HEADS],
    v_scale: [f32; HEADS],
    a: [f32; D_MODEL],
    g: [f32; D_MODEL],
    w: [f32; D_MODEL],
    k_deformed: [f32; D_MODEL],
    timestep_out: [f32; D_MODEL],
    temp: [f32; D_MODEL],
    lora_hidden: [f32; MAX_LORA_RANK],
    next_row: [f32; HEAD_SIZE],
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
#[derive(Default)]
struct TimeMixerQueryBatchScratch {
    x: Vec<f32>,
    mixed: [Vec<f32>; 8],
    r: Vec<f32>,
    k: Vec<f32>,
    v: Vec<f32>,
    k_scale: Vec<f32>,
    v_scale: Vec<f32>,
    a: Vec<f32>,
    g: Vec<f32>,
    w: Vec<f32>,
    k_deformed: Vec<f32>,
    timestep_out: Vec<f32>,
    temp: Vec<f32>,
    lora_hidden: Vec<f32>,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
impl Default for TimeMixerQueryScratch {
    fn default() -> Self {
        Self {
            x: [0.0; D_MODEL],
            mixed: [[0.0; D_MODEL]; 8],
            r: [0.0; D_MODEL],
            k: [0.0; D_MODEL],
            v: [0.0; D_MODEL],
            k_scale: [0.0; HEADS],
            v_scale: [0.0; HEADS],
            a: [0.0; D_MODEL],
            g: [0.0; D_MODEL],
            w: [0.0; D_MODEL],
            k_deformed: [0.0; D_MODEL],
            timestep_out: [0.0; D_MODEL],
            temp: [0.0; D_MODEL],
            lora_hidden: [0.0; MAX_LORA_RANK],
            next_row: [0.0; HEAD_SIZE],
        }
    }
}

impl TimeMixer {
    fn load(weights: &WeightMap, module_id: usize, layer_id: usize) -> io::Result<Self> {
        let prefix = format!("rwkv_modules.{module_id}.blocks.{layer_id}.time_mixer");
        Ok(Self {
            #[cfg(test)]
            module_id,
            layer_id,
            layer_norm: weights.layer_norm(&format!("{prefix}.layer_norm"), D_MODEL, 1e-5)?,
            rkvdag_lerp: weights.values(&format!("{prefix}.rkvdag_lerp"))?,
            bonus: weights.values(&format!("{prefix}.bonus"))?,
            w_r: weights.linear(&format!("{prefix}.W_r"), D_MODEL, D_MODEL, false)?,
            w_k: weights.linear(&format!("{prefix}.W_k"), D_MODEL, D_MODEL, false)?,
            w_v: weights.linear(&format!("{prefix}.W_v"), D_MODEL, D_MODEL, false)?,
            w_o: weights.linear(&format!("{prefix}.W_o"), D_MODEL, D_MODEL, false)?,
            k_scale_linear: weights.linear(
                &format!("{prefix}.k_scale_linear"),
                D_MODEL,
                HEADS,
                true,
            )?,
            v_scale_linear: weights.linear(
                &format!("{prefix}.v_scale_linear"),
                D_MODEL,
                HEADS,
                true,
            )?,
            v_lora: if SMALL_MODEL && layer_id == 0 {
                LoraSimple::zero(2)
            } else {
                LoraSimple::load(
                    weights,
                    &format!("{prefix}.v_lora_simple"),
                    if SMALL_MODEL { 2 } else { 8 },
                )?
            },
            a_lora: LoraSimple::load(
                weights,
                &format!("{prefix}.a_lora_simple"),
                if SMALL_MODEL { 4 } else { 16 },
            )?,
            d_lora: LoraSimple::load(
                weights,
                &format!("{prefix}.d_lora_mlp"),
                if SMALL_MODEL { 4 } else { 16 },
            )?,
            lora_a_g: weights.linear(
                &format!("{prefix}.lora_A_g"),
                D_MODEL,
                if SMALL_MODEL { 4 } else { 16 },
                false,
            )?,
            lora_b_g: weights.linear(
                &format!("{prefix}.lora_B_g"),
                if SMALL_MODEL { 4 } else { 16 },
                D_MODEL,
                false,
            )?,
            out_group_norm: weights.group_norm(
                &format!("{prefix}.out_group_norm"),
                HEADS,
                D_MODEL,
                64e-5,
            )?,
        })
    }

    fn run(
        &self,
        input: &[f32],
        v0: &[f32],
        state: Option<&TimeState>,
    ) -> (Vec<f32>, Vec<f32>, TimeState) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();

        let x = self.layer_norm.apply(input);
        let (x_shift, state_matrix) = match state {
            Some(state) => (state.x_shift.as_slice(), state.matrix.as_slice()),
            None => (x.as_slice(), &[0.0; HEADS * HEAD_SIZE * HEAD_SIZE][..]),
        };

        let parts = self.mix_parts(&x, x_shift, v0);

        #[cfg(test)]
        record_rwkv_scan_step(
            self.module_id,
            self.layer_id,
            &parts.r,
            &parts.k,
            &parts.v,
            &parts.w,
            &parts.a,
            &parts.k_deformed,
        );

        let (out, next_matrix) = single_timestep(
            &parts.r,
            &parts.k,
            &parts.v,
            &parts.w,
            &parts.a,
            &parts.k_deformed,
            state_matrix,
        );
        let out = self.mix_output(&parts, out, input);

        let output = (
            out,
            parts.next_v0,
            TimeState {
                x_shift: x,
                matrix: next_matrix,
                packed_matrix: None,
            },
        );
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::TimeMixer, profile_started);
        output
    }

    /// The pre-recurrence per-timestep math shared by the per-review and bulk
    /// replay paths: lerp mixes, projections, loras, activations, and head
    /// normalization. `x` must already be layer-normed; `x_shift` is the
    /// previous timestep's normed input for this stream (or `x` itself when
    /// the stream has no state yet).
    fn mix_parts(&self, x: &[f32], x_shift: &[f32], v0: &[f32]) -> TimeMixParts {
        let mut mixed = [0.0; D_MODEL];
        let fill_mixed = |mix_id: usize, mixed: &mut [f32; D_MODEL]| {
            let lerp_offset = mix_id * D_MODEL;
            for channel in 0..D_MODEL {
                mixed[channel] = lerp(
                    x[channel],
                    x_shift[channel],
                    self.rkvdag_lerp[lerp_offset + channel],
                );
            }
        };

        fill_mixed(0, &mut mixed);
        let r = self.w_r.apply(&mixed);
        fill_mixed(1, &mut mixed);
        let mut k = self.w_k.apply(&mixed);
        fill_mixed(6, &mut mixed);
        let mut k_scale = self.k_scale_linear.apply(&mixed);
        sigmoid_in_place(&mut k_scale);
        fill_mixed(7, &mut mixed);
        let mut v_scale = self.v_scale_linear.apply(&mixed);
        sigmoid_in_place(&mut v_scale);

        fill_mixed(2, &mut mixed);
        let (v, next_v0) = if self.layer_id == 0 {
            let v = self.w_v.apply(&mixed);
            (v.clone(), v)
        } else {
            let mut v_lerp = self.v_lora.apply_sigmoid(&mixed);
            let w_v = self.w_v.apply(&mixed);
            for channel in 0..D_MODEL {
                v_lerp[channel] = lerp(w_v[channel], v0[channel], v_lerp[channel]);
            }
            (v_lerp, v0.to_vec())
        };

        fill_mixed(4, &mut mixed);
        let a = self.a_lora.apply_sigmoid(&mixed);
        fill_mixed(5, &mut mixed);
        let mut g = self.lora_a_g.apply(&mixed);
        sigmoid_in_place(&mut g);
        g = self.lora_b_g.apply(&g);

        fill_mixed(3, &mut mixed);
        let mut w = self.d_lora.apply_tanh(&mixed);
        for value in &mut w {
            let d = -0.5 - softplus(-*value);
            *value = (-d.exp()).exp();
        }

        normalize_heads_in_place(&mut k);
        for head in 0..HEADS {
            for index in 0..HEAD_SIZE {
                k[head * HEAD_SIZE + index] *= k_scale[head];
            }
        }

        let mut v = v;
        normalize_heads_in_place(&mut v);
        for head in 0..HEADS {
            for index in 0..HEAD_SIZE {
                v[head * HEAD_SIZE + index] *= v_scale[head];
            }
        }

        let k_deformed = k.clone();
        for channel in 0..D_MODEL {
            k[channel] *= a[channel];
        }

        TimeMixParts {
            r,
            k,
            v,
            w,
            a,
            k_deformed,
            g,
            next_v0,
        }
    }

    /// The post-recurrence per-timestep math shared by the per-review and
    /// bulk replay paths: group norm, bonus, gate, output projection, and the
    /// residual add against the un-normed layer input.
    fn mix_output(
        &self,
        parts: &TimeMixParts,
        recurrence_out: Vec<f32>,
        input: &[f32],
    ) -> Vec<f32> {
        let mut out = self.out_group_norm.apply(&recurrence_out);

        let mut bonus = [0.0; D_MODEL];
        for head in 0..HEADS {
            let base = head * HEAD_SIZE;
            let mut bonus_scale = 0.0;
            for index in 0..HEAD_SIZE {
                bonus_scale +=
                    parts.r[base + index] * self.bonus[base + index] * parts.k[base + index];
            }
            for index in 0..HEAD_SIZE {
                bonus[base + index] = bonus_scale * parts.v[base + index];
            }
        }

        for channel in 0..D_MODEL {
            out[channel] = parts.g[channel] * (out[channel] + bonus[channel]);
        }
        let mut out = self.w_o.apply(&out);
        for channel in 0..D_MODEL {
            out[channel] += input[channel];
        }
        out
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn run_query_into(
        &self,
        input: &[f32],
        v0: &mut [f32; D_MODEL],
        state: Option<&TimeState>,
        out: &mut [f32; D_MODEL],
        scratch: &mut TimeMixerQueryScratch,
    ) {
        let TimeMixerQueryScratch {
            x,
            mixed,
            r,
            k,
            v,
            k_scale,
            v_scale,
            a,
            g,
            w,
            k_deformed,
            timestep_out,
            temp,
            lora_hidden,
            next_row,
        } = scratch;

        self.layer_norm.apply_into(input, x);
        let x_shift = state.map_or(x.as_slice(), |state| state.x_shift.as_slice());
        for (mix_id, mixed_row) in mixed.iter_mut().enumerate() {
            let lerp_offset = mix_id * D_MODEL;
            for channel in 0..D_MODEL {
                mixed_row[channel] = lerp(
                    x[channel],
                    x_shift[channel],
                    self.rkvdag_lerp[lerp_offset + channel],
                );
            }
        }

        self.w_r.apply_into(&mixed[0], r);
        self.w_k.apply_into(&mixed[1], k);
        self.k_scale_linear.apply_into(&mixed[6], k_scale);
        sigmoid_in_place(k_scale);
        self.v_scale_linear.apply_into(&mixed[7], v_scale);
        sigmoid_in_place(v_scale);

        if self.layer_id == 0 {
            self.w_v.apply_into(&mixed[2], v);
            v0.copy_from_slice(v);
        } else {
            self.v_lora.apply_sigmoid_into(&mixed[2], lora_hidden, v);
            self.w_v.apply_into(&mixed[2], temp);
            for channel in 0..D_MODEL {
                v[channel] = lerp(temp[channel], v0[channel], v[channel]);
            }
        }

        self.a_lora.apply_sigmoid_into(&mixed[4], lora_hidden, a);
        let gate_hidden = &mut lora_hidden[..self.lora_a_g.output];
        self.lora_a_g.apply_into(&mixed[5], gate_hidden);
        sigmoid_in_place(gate_hidden);
        self.lora_b_g.apply_into(gate_hidden, g);

        self.d_lora.apply_tanh_into(&mixed[3], lora_hidden, w);
        for value in w.iter_mut() {
            let decay = -0.5 - softplus(-*value);
            *value = (-decay.exp()).exp();
        }

        normalize_heads_in_place(k);
        for head in 0..HEADS {
            for index in 0..HEAD_SIZE {
                k[head * HEAD_SIZE + index] *= k_scale[head];
            }
        }

        normalize_heads_in_place(v);
        for head in 0..HEADS {
            for index in 0..HEAD_SIZE {
                v[head * HEAD_SIZE + index] *= v_scale[head];
            }
        }

        k_deformed.copy_from_slice(k);
        for channel in 0..D_MODEL {
            k[channel] *= a[channel];
        }

        single_timestep_query_into(
            r,
            k,
            v,
            w,
            a,
            k_deformed,
            state.map(|state| state.matrix.as_slice()),
            timestep_out,
            next_row,
        );
        self.out_group_norm.apply_into(timestep_out, temp);

        for head in 0..HEADS {
            let base = head * HEAD_SIZE;
            let mut bonus_scale = 0.0;
            for index in 0..HEAD_SIZE {
                bonus_scale += r[base + index] * self.bonus[base + index] * k[base + index];
            }
            for index in 0..HEAD_SIZE {
                let channel = base + index;
                temp[channel] = g[channel] * (temp[channel] + bonus_scale * v[channel]);
            }
        }

        self.w_o.apply_into(temp, out);
        for channel in 0..D_MODEL {
            out[channel] += input[channel];
        }
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    #[allow(clippy::too_many_arguments)]
    fn run_query_batch(
        &self,
        input: &[f32],
        v0: &mut [f32],
        items: &[ReviewPredictionQueryRef<'_>],
        module_id: usize,
        layer_id: usize,
        out: &mut Vec<f32>,
        scratch: &mut TimeMixerQueryBatchScratch,
    ) {
        let rows = items.len();
        debug_assert_eq!(input.len(), rows * D_MODEL);
        debug_assert_eq!(v0.len(), rows * D_MODEL);

        self.layer_norm.apply_batch(input, rows, &mut scratch.x);
        for mixed in &mut scratch.mixed {
            mixed.resize(rows * D_MODEL, 0.0);
        }
        // Resolve each row's shift state once for all eight mixes.
        for (row, item) in items.iter().enumerate() {
            let x = &scratch.x[row * D_MODEL..(row + 1) * D_MODEL];
            let x_shift = item
                .layer_state(module_id, layer_id)
                .and_then(|state| state.time.as_ref())
                .map_or(x, |state| state.x_shift.as_slice());
            time_shift_mix_row(x, x_shift, &self.rkvdag_lerp, &mut scratch.mixed, row);
        }

        self.w_r
            .apply_batch(&scratch.mixed[0], rows, &mut scratch.r);
        self.w_k
            .apply_batch(&scratch.mixed[1], rows, &mut scratch.k);
        self.k_scale_linear
            .apply_batch(&scratch.mixed[6], rows, &mut scratch.k_scale);
        query_sigmoid_batch_in_place(&mut scratch.k_scale);
        self.v_scale_linear
            .apply_batch(&scratch.mixed[7], rows, &mut scratch.v_scale);
        query_sigmoid_batch_in_place(&mut scratch.v_scale);

        if self.layer_id == 0 {
            self.w_v
                .apply_batch(&scratch.mixed[2], rows, &mut scratch.v);
            v0.copy_from_slice(&scratch.v);
        } else {
            self.v_lora.apply_sigmoid_batch(
                &scratch.mixed[2],
                rows,
                &mut scratch.lora_hidden,
                &mut scratch.v,
            );
            self.w_v
                .apply_batch(&scratch.mixed[2], rows, &mut scratch.temp);
            scratch
                .v
                .chunks_mut(D_MODEL)
                .enumerate()
                .for_each(|(row, v)| {
                    let projected = &scratch.temp[row * D_MODEL..(row + 1) * D_MODEL];
                    let v0 = &v0[row * D_MODEL..(row + 1) * D_MODEL];
                    for channel in 0..D_MODEL {
                        v[channel] = lerp(projected[channel], v0[channel], v[channel]);
                    }
                });
        }

        self.a_lora.apply_sigmoid_batch(
            &scratch.mixed[4],
            rows,
            &mut scratch.lora_hidden,
            &mut scratch.a,
        );
        self.lora_a_g
            .apply_batch(&scratch.mixed[5], rows, &mut scratch.lora_hidden);
        query_sigmoid_batch_in_place(&mut scratch.lora_hidden);
        self.lora_b_g
            .apply_batch(&scratch.lora_hidden, rows, &mut scratch.g);

        self.d_lora.apply_tanh_batch(
            &scratch.mixed[3],
            rows,
            &mut scratch.lora_hidden,
            &mut scratch.w,
        );
        query_decay_batch_in_place(&mut scratch.w);

        scratch
            .k
            .chunks_mut(D_MODEL)
            .zip(scratch.k_scale.chunks(HEADS))
            .for_each(|(k, scales)| normalize_scale_heads_in_place(k, scales));
        scratch
            .v
            .chunks_mut(D_MODEL)
            .zip(scratch.v_scale.chunks(HEADS))
            .for_each(|(v, scales)| normalize_scale_heads_in_place(v, scales));

        scratch.k_deformed.resize(rows * D_MODEL, 0.0);
        scratch.k_deformed.copy_from_slice(&scratch.k);
        scratch
            .k
            .iter_mut()
            .zip(scratch.a.iter())
            .for_each(|(k, a)| *k *= *a);

        scratch.timestep_out.resize(rows * D_MODEL, 0.0);
        scratch
            .timestep_out
            .chunks_mut(D_MODEL)
            .enumerate()
            .for_each(|(row, timestep_out)| {
                let range = row * D_MODEL..(row + 1) * D_MODEL;
                let state = items[row]
                    .layer_state(module_id, layer_id)
                    .and_then(|state| state.time.as_ref())
                    .map(|state| state.matrix.as_slice());
                single_timestep_query_fast_into(
                    &scratch.r[range.clone()],
                    &scratch.k[range.clone()],
                    &scratch.v[range.clone()],
                    &scratch.w[range.clone()],
                    &scratch.a[range.clone()],
                    &scratch.k_deformed[range],
                    state,
                    timestep_out,
                );
            });
        self.out_group_norm
            .apply_batch(&scratch.timestep_out, rows, &mut scratch.temp);

        scratch
            .temp
            .chunks_mut(D_MODEL)
            .enumerate()
            .for_each(|(row, output)| {
                let range = row * D_MODEL..(row + 1) * D_MODEL;
                bonus_gate_in_place(
                    output,
                    &scratch.r[range.clone()],
                    &self.bonus,
                    &scratch.k[range.clone()],
                    &scratch.v[range.clone()],
                    &scratch.g[range],
                );
            });

        self.w_o.apply_batch(&scratch.temp, rows, out);
        out.iter_mut()
            .zip(input.iter())
            .for_each(|(output, input)| *output += *input);
    }
}

/// Per-timestep time-mixer coefficients produced ahead of the recurrence.
struct TimeMixParts {
    r: Vec<f32>,
    k: Vec<f32>,
    v: Vec<f32>,
    w: Vec<f32>,
    a: Vec<f32>,
    k_deformed: Vec<f32>,
    g: Vec<f32>,
    next_v0: Vec<f32>,
}

#[derive(Clone)]
struct TimeState {
    x_shift: Vec<f32>,
    matrix: Vec<f32>,
    packed_matrix: Option<precision::PackedMatrix>,
}

struct ChannelMixer {
    layer_norm: Norm,
    lerp_k: Vec<f32>,
    w_k: Linear,
    w_v: Linear,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
struct ChannelMixerQueryScratch {
    x: [f32; D_MODEL],
    mixed: [f32; D_MODEL],
    hidden: [f32; MAX_CHANNEL_MIXER_DIM],
    projected: [f32; D_MODEL],
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
#[derive(Default)]
struct ChannelMixerQueryBatchScratch {
    x: Vec<f32>,
    mixed: Vec<f32>,
    hidden: Vec<f32>,
    projected: Vec<f32>,
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
impl Default for ChannelMixerQueryScratch {
    fn default() -> Self {
        Self {
            x: [0.0; D_MODEL],
            mixed: [0.0; D_MODEL],
            hidden: [0.0; MAX_CHANNEL_MIXER_DIM],
            projected: [0.0; D_MODEL],
        }
    }
}

impl ChannelMixer {
    fn load(weights: &WeightMap, module_id: usize, layer_id: usize) -> io::Result<Self> {
        let channel_dim = CHANNEL_MIXER_DIMS[module_id];
        let prefix = format!("rwkv_modules.{module_id}.blocks.{layer_id}.channel_mixer");
        Ok(Self {
            layer_norm: weights.layer_norm(&format!("{prefix}.layer_norm"), D_MODEL, 1e-5)?,
            lerp_k: weights.values(&format!("{prefix}.lerp_k"))?,
            w_k: weights.linear(&format!("{prefix}.W_k"), D_MODEL, channel_dim, false)?,
            w_v: weights.linear(&format!("{prefix}.W_v"), channel_dim, D_MODEL, false)?,
        })
    }

    fn run(&self, input: &[f32], state: Option<&Vec<f32>>) -> (Vec<f32>, Vec<f32>) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();

        let x = self.layer_norm.apply(input);
        let x_shift = state.map_or(x.as_slice(), |state| state.as_slice());
        let out = self.mix_value(&x, x_shift, input);
        let output = (out, x);
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::ChannelMixer, profile_started);
        output
    }

    /// The per-timestep channel-mixer math shared by the per-review and bulk
    /// replay paths. `x` must already be layer-normed; `x_shift` is the
    /// previous timestep's normed input for this stream (or `x` itself when
    /// the stream has no state yet); `input` is the un-normed layer input
    /// used for the residual add.
    fn mix_value(&self, x: &[f32], x_shift: &[f32], input: &[f32]) -> Vec<f32> {
        let mut mixed = [0.0; D_MODEL];
        for channel in 0..D_MODEL {
            mixed[channel] = lerp(x[channel], x_shift[channel], self.lerp_k[channel]);
        }

        let mut k = self.w_k.apply(&mixed);
        for value in &mut k {
            *value = value.max(0.0).powi(2);
        }
        let mut out = self.w_v.apply(&k);
        for channel in 0..D_MODEL {
            out[channel] += input[channel];
        }
        out
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn run_query_into(
        &self,
        input: &[f32],
        state: Option<&[f32]>,
        out: &mut [f32; D_MODEL],
        scratch: &mut ChannelMixerQueryScratch,
    ) {
        self.layer_norm.apply_into(input, &mut scratch.x);
        let x_shift = state.unwrap_or(&scratch.x);
        for (channel, mixed) in scratch.mixed.iter_mut().enumerate() {
            *mixed = lerp(scratch.x[channel], x_shift[channel], self.lerp_k[channel]);
        }

        let hidden = &mut scratch.hidden[..self.w_k.output];
        self.w_k.apply_into(&scratch.mixed, hidden);
        for value in hidden.iter_mut() {
            *value = value.max(0.0).powi(2);
        }
        self.w_v.apply_into(hidden, &mut scratch.projected);
        for channel in 0..D_MODEL {
            out[channel] = input[channel] + scratch.projected[channel];
        }
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn run_query_batch(
        &self,
        input: &[f32],
        items: &[ReviewPredictionQueryRef<'_>],
        module_id: usize,
        layer_id: usize,
        out: &mut Vec<f32>,
        scratch: &mut ChannelMixerQueryBatchScratch,
    ) {
        let rows = items.len();
        self.layer_norm.apply_batch(input, rows, &mut scratch.x);
        scratch.mixed.resize(rows * D_MODEL, 0.0);
        scratch
            .mixed
            .chunks_mut(D_MODEL)
            .enumerate()
            .for_each(|(row, mixed)| {
                let x = &scratch.x[row * D_MODEL..(row + 1) * D_MODEL];
                let x_shift = items[row]
                    .layer_state(module_id, layer_id)
                    .and_then(|state| state.channel_shift.as_deref())
                    .unwrap_or(x);
                for channel in 0..D_MODEL {
                    mixed[channel] = lerp(x[channel], x_shift[channel], self.lerp_k[channel]);
                }
            });

        self.w_k
            .apply_batch(&scratch.mixed, rows, &mut scratch.hidden);
        scratch
            .hidden
            .iter_mut()
            .for_each(|value| *value = value.max(0.0).powi(2));
        self.w_v
            .apply_batch(&scratch.hidden, rows, &mut scratch.projected);
        out.resize(rows * D_MODEL, 0.0);
        out.iter_mut()
            .zip(input.iter())
            .zip(scratch.projected.iter())
            .for_each(|((output, input), projected)| *output = *input + *projected);
    }
}

struct LoraSimple {
    a: Linear,
    b: Linear,
}

impl LoraSimple {
    fn zero(rank: usize) -> Self {
        let linear = |input, output, bias| Linear {
            input,
            output,
            weight_by_input: vec![0.0; input * output],
            bias: if bias { Some(vec![0.0; output]) } else { None },
            #[cfg(test)]
            packed_weight: None,
        };
        Self {
            a: linear(D_MODEL, rank, false),
            b: linear(rank, D_MODEL, true),
        }
    }

    fn load(weights: &WeightMap, prefix: &str, rank: usize) -> io::Result<Self> {
        Ok(Self {
            a: weights.linear(&format!("{prefix}.A"), D_MODEL, rank, false)?,
            b: weights.linear(&format!("{prefix}.B_and_lamb"), rank, D_MODEL, true)?,
        })
    }

    fn apply_sigmoid(&self, input: &[f32]) -> Vec<f32> {
        let mut out = self.b.apply(&self.a.apply(input));
        sigmoid_in_place(&mut out);
        out
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn apply_sigmoid_into(&self, input: &[f32], hidden: &mut [f32], out: &mut [f32]) {
        let hidden = &mut hidden[..self.a.output];
        self.a.apply_into(input, hidden);
        self.b.apply_into(hidden, out);
        sigmoid_in_place(out);
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn apply_sigmoid_batch(
        &self,
        input: &[f32],
        rows: usize,
        hidden: &mut Vec<f32>,
        out: &mut Vec<f32>,
    ) {
        self.a.apply_batch(input, rows, hidden);
        self.b.apply_batch(hidden, rows, out);
        query_sigmoid_batch_in_place(out);
    }

    fn apply_tanh(&self, input: &[f32]) -> Vec<f32> {
        let mut hidden = self.a.apply(input);
        for value in &mut hidden {
            *value = value.tanh();
        }
        self.b.apply(&hidden)
    }

    /// Block variant of `apply_sigmoid`; bit-identical per row.
    fn apply_sigmoid_block_into(&self, inputs: &[f32], outs: &mut [f32], rows: usize) {
        let rank = self.a.output;
        debug_assert!(rank <= 16);
        debug_assert!(rows <= LINEAR_BLOCK_ROWS);
        let mut hidden = [0.0; 16 * LINEAR_BLOCK_ROWS];
        let hidden = &mut hidden[..rows * rank];
        self.a.apply_block_into(inputs, hidden, rows);
        self.b.apply_block_into(hidden, outs, rows);
        sigmoid_in_place(outs);
    }

    /// Block variant of `apply_tanh`; bit-identical per row.
    fn apply_tanh_block_into(&self, inputs: &[f32], outs: &mut [f32], rows: usize) {
        let rank = self.a.output;
        debug_assert!(rank <= 16);
        debug_assert!(rows <= LINEAR_BLOCK_ROWS);
        let mut hidden = [0.0; 16 * LINEAR_BLOCK_ROWS];
        let hidden = &mut hidden[..rows * rank];
        self.a.apply_block_into(inputs, hidden, rows);
        for value in hidden.iter_mut() {
            *value = value.tanh();
        }
        self.b.apply_block_into(hidden, outs, rows);
    }

    #[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
    fn apply_tanh_into(&self, input: &[f32], hidden: &mut [f32], out: &mut [f32]) {
        let hidden = &mut hidden[..self.a.output];
        self.a.apply_into(input, hidden);
        for value in hidden.iter_mut() {
            *value = value.tanh();
        }
        self.b.apply_into(hidden, out);
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn apply_tanh_batch(
        &self,
        input: &[f32],
        rows: usize,
        hidden: &mut Vec<f32>,
        out: &mut Vec<f32>,
    ) {
        self.a.apply_batch(input, rows, hidden);
        query_tanh_batch_in_place(hidden);
        self.b.apply_batch(hidden, rows, out);
    }
}

struct Linear {
    input: usize,
    output: usize,
    weight_by_input: Vec<f32>,
    bias: Option<Vec<f32>>,
    #[cfg(test)]
    packed_weight: Option<packed_weights::PackedLinear>,
}

impl Linear {
    #[inline(always)]
    fn weight_values(&self) -> Cow<'_, [f32]> {
        #[cfg(test)]
        if let Some(packed) = &self.packed_weight {
            return Cow::Owned(packed.decode_by_input());
        }
        Cow::Borrowed(&self.weight_by_input)
    }

    fn apply(&self, input: &[f32]) -> Vec<f32> {
        let mut out = vec![0.0; self.output];
        self.apply_into(input, &mut out);
        out
    }

    fn apply_into(&self, input: &[f32], out: &mut [f32]) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();
        #[cfg(test)]
        if let Some(fused) = self
            .packed_weight
            .as_ref()
            .and_then(|packed| packed.fused.as_ref())
        {
            fused.apply(input, out, 1, self.bias.as_deref());
            rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
            return;
        }
        let weight_by_input = self.weight_values();

        debug_assert_eq!(input.len(), self.input);
        debug_assert_eq!(out.len(), self.output);
        if let Some(bias) = &self.bias {
            out.copy_from_slice(bias);
        } else {
            out.fill(0.0);
        }

        #[cfg(target_arch = "aarch64")]
        {
            // SAFETY: aarch64 guarantees NEON support. The helper receives
            // slices whose dimensions were checked above and only accesses
            // complete weight columns and output elements within them.
            unsafe { linear_apply_row_neon(input, &weight_by_input, out) };
            #[cfg(test)]
            rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
            return;
        }

        #[allow(unreachable_code)]
        for (column, scale) in input.iter().copied().enumerate() {
            if scale == 0.0 {
                continue;
            }
            let weight_column = &weight_by_input[column * self.output..(column + 1) * self.output];
            add_scaled_in_place(out, weight_column, scale);
        }
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn apply_batch(&self, input: &[f32], rows: usize, out: &mut Vec<f32>) {
        let input_len = rows
            .checked_mul(self.input)
            .expect("linear batch input is too large");
        let output_len = rows
            .checked_mul(self.output)
            .expect("linear batch output is too large");
        assert_eq!(input.len(), input_len);
        out.resize(output_len, 0.0);
        if rows == 0 {
            return;
        }
        #[cfg(test)]
        if let Some(fused) = self
            .packed_weight
            .as_ref()
            .and_then(|packed| packed.fused.as_ref())
        {
            fused.apply(input, out, rows, self.bias.as_deref());
            return;
        }

        #[cfg(target_os = "macos")]
        let weight_by_input = self.weight_values();
        #[cfg(target_os = "macos")]
        matmul::matrix_times_matrix(input, &weight_by_input, rows, self.output, self.input, out);

        // kuma3: phones (aarch64) take the batched query path too; the block
        // kernel streams each weight column once per block of rows (NEON),
        // bit-identical to `apply_into` per row. Callers already run batches
        // in parallel.
        #[cfg(not(target_os = "macos"))]
        self.apply_block_into(input, out, rows);

        #[cfg(target_os = "macos")]
        if let Some(bias) = &self.bias {
            out.chunks_mut(self.output).for_each(|output| {
                for (output, bias) in output.iter_mut().zip(bias) {
                    *output += bias;
                }
            });
        }
    }

    /// Applies the projection to `rows` packed input rows at once, streaming
    /// each weight column once per block instead of once per row. For every
    /// output element this performs the identical operation sequence as
    /// `apply_into` (bias init, then ascending-column fused accumulation with
    /// the same zero-column skip), so results are bit-identical to calling
    /// `apply_into` per row.
    fn apply_block_into(&self, inputs: &[f32], outs: &mut [f32], rows: usize) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();
        #[cfg(test)]
        if let Some(fused) = self
            .packed_weight
            .as_ref()
            .and_then(|packed| packed.fused.as_ref())
        {
            fused.apply(inputs, outs, rows, self.bias.as_deref());
            rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
            return;
        }
        let weight_by_input = self.weight_values();

        debug_assert_eq!(inputs.len(), rows * self.input);
        debug_assert_eq!(outs.len(), rows * self.output);
        for out in outs.chunks_exact_mut(self.output) {
            if let Some(bias) = &self.bias {
                out.copy_from_slice(bias);
            } else {
                out.fill(0.0);
            }
        }

        #[cfg(target_arch = "aarch64")]
        if rows >= 4 {
            // SAFETY: aarch64 guarantees NEON support. The helper receives
            // slices whose dimensions were checked above and only accesses
            // complete rows and output vectors within those dimensions.
            unsafe {
                linear_apply_block_neon(
                    inputs,
                    &weight_by_input,
                    outs,
                    rows,
                    self.input,
                    self.output,
                );
            }
            #[cfg(test)]
            rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
            return;
        }

        #[cfg(target_arch = "x86_64")]
        if rows >= 4 && x86_avx2_fma_available() {
            // SAFETY: availability was checked above. The helper receives
            // slices whose dimensions were checked above and only accesses
            // complete rows and output vectors within those dimensions.
            unsafe {
                linear_apply_block_avx2_fma(
                    inputs,
                    &weight_by_input,
                    outs,
                    rows,
                    self.input,
                    self.output,
                );
            }
            #[cfg(test)]
            rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
            return;
        }

        for column in 0..self.input {
            let weight_column = &weight_by_input[column * self.output..(column + 1) * self.output];
            for (row, out) in outs.chunks_exact_mut(self.output).enumerate() {
                let scale = inputs[row * self.input + column];
                if scale == 0.0 {
                    continue;
                }
                add_scaled_in_place(out, weight_column, scale);
            }
        }
        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Linear, profile_started);
    }

    /// Uses the native matrix backend for non-persisted bulk query rows when
    /// requested. Answer rows retain `apply_block_into()`'s exact accumulation
    /// order so warm-up state snapshots remain byte-identical.
    fn apply_block_approx_into(
        &self,
        inputs: &[f32],
        outs: &mut [f32],
        rows: usize,
        approximate: bool,
    ) {
        #[cfg(test)]
        if self
            .packed_weight
            .as_ref()
            .is_some_and(|packed| packed.fused.is_some())
        {
            self.apply_block_into(inputs, outs, rows);
            return;
        }
        #[cfg(not(target_os = "macos"))]
        let _ = approximate;

        #[cfg(target_os = "macos")]
        if approximate && rows >= 16 && self.input * self.output >= 4096 {
            let weight_by_input = self.weight_values();
            matmul::matrix_times_matrix(
                inputs,
                &weight_by_input,
                rows,
                self.output,
                self.input,
                outs,
            );
            if let Some(bias) = &self.bias {
                for out in outs.chunks_exact_mut(self.output) {
                    for (value, bias) in out.iter_mut().zip(bias) {
                        *value += bias;
                    }
                }
            }
            return;
        }

        self.apply_block_into(inputs, outs, rows);
    }
}

#[inline(always)]
fn add_scaled_in_place(out: &mut [f32], weights: &[f32], scale: f32) {
    debug_assert_eq!(out.len(), weights.len());
    add_scaled_in_place_arch(out, weights, scale)
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
fn add_scaled_in_place_arch(out: &mut [f32], weights: &[f32], scale: f32) {
    // SAFETY: aarch64 guarantees NEON support, and the helper only uses
    // unaligned loads/stores within the bounds checked by its loop conditions.
    unsafe { add_scaled_in_place_neon(out, weights, scale) }
}

#[cfg(target_arch = "x86_64")]
#[inline(always)]
fn add_scaled_in_place_arch(out: &mut [f32], weights: &[f32], scale: f32) {
    if x86_avx2_fma_available() {
        // SAFETY: availability was checked above, and the helper only uses
        // unaligned loads/stores within the bounds checked by its loops.
        unsafe { add_scaled_in_place_avx2_fma(out, weights, scale) }
    } else {
        add_scaled_in_place_scalar(out, weights, scale)
    }
}

#[cfg(all(not(target_arch = "aarch64"), not(target_arch = "x86_64")))]
#[inline(always)]
fn add_scaled_in_place_arch(out: &mut [f32], weights: &[f32], scale: f32) {
    add_scaled_in_place_scalar(out, weights, scale)
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
unsafe fn add_scaled_in_place_neon(out: &mut [f32], weights: &[f32], scale: f32) {
    use std::arch::aarch64::*;

    let mut offset = 0;
    let len = out.len();
    let scale = vdupq_n_f32(scale);
    while offset + 16 <= len {
        let out_ptr = out.as_mut_ptr().add(offset);
        let weight_ptr = weights.as_ptr().add(offset);
        vst1q_f32(
            out_ptr,
            vfmaq_f32(vld1q_f32(out_ptr), vld1q_f32(weight_ptr), scale),
        );
        vst1q_f32(
            out_ptr.add(4),
            vfmaq_f32(
                vld1q_f32(out_ptr.add(4)),
                vld1q_f32(weight_ptr.add(4)),
                scale,
            ),
        );
        vst1q_f32(
            out_ptr.add(8),
            vfmaq_f32(
                vld1q_f32(out_ptr.add(8)),
                vld1q_f32(weight_ptr.add(8)),
                scale,
            ),
        );
        vst1q_f32(
            out_ptr.add(12),
            vfmaq_f32(
                vld1q_f32(out_ptr.add(12)),
                vld1q_f32(weight_ptr.add(12)),
                scale,
            ),
        );
        offset += 16;
    }
    while offset + 4 <= len {
        let out_ptr = out.as_mut_ptr().add(offset);
        let weight_ptr = weights.as_ptr().add(offset);
        vst1q_f32(
            out_ptr,
            vfmaq_f32(vld1q_f32(out_ptr), vld1q_f32(weight_ptr), scale),
        );
        offset += 4;
    }
    while offset < len {
        out[offset] += weights[offset] * vgetq_lane_f32(scale, 0);
        offset += 1;
    }
}

/// Exact single-row linear projection microkernel for AArch64 NEON machines.
///
/// Keeps a tile of up to 64 output accumulators in registers while walking
/// every input column, so the output is loaded and stored once per tile
/// instead of once per column. Each output element still starts from `out`'s
/// initial value and accumulates ascending columns with the same fused
/// multiply-add and zero-scale skip as `add_scaled_in_place()`, so results
/// are bit-identical to the column-at-a-time loop.
#[cfg(target_arch = "aarch64")]
unsafe fn linear_apply_row_neon(input: &[f32], weights: &[f32], out: &mut [f32]) {
    use std::arch::aarch64::*;

    let output = out.len();
    debug_assert_eq!(weights.len(), input.len() * output);
    let out_ptr = out.as_mut_ptr();
    let weight_ptr = weights.as_ptr();
    let mut tile = 0;

    macro_rules! tile {
        ($lanes:literal) => {
            while tile + 4 * $lanes <= output {
                let mut acc = [vdupq_n_f32(0.0); $lanes];
                for (lane, acc) in acc.iter_mut().enumerate() {
                    *acc = vld1q_f32(out_ptr.add(tile + 4 * lane));
                }
                for (column, &scale) in input.iter().enumerate() {
                    if scale == 0.0 {
                        continue;
                    }
                    let scale = vdupq_n_f32(scale);
                    let column_ptr = weight_ptr.add(column * output + tile);
                    for (lane, acc) in acc.iter_mut().enumerate() {
                        *acc = vfmaq_f32(*acc, vld1q_f32(column_ptr.add(4 * lane)), scale);
                    }
                }
                for (lane, acc) in acc.iter().enumerate() {
                    vst1q_f32(out_ptr.add(tile + 4 * lane), *acc);
                }
                tile += 4 * $lanes;
            }
        };
    }
    tile!(16);
    tile!(4);
    tile!(1);

    for offset in tile..output {
        let mut value = *out_ptr.add(offset);
        for (column, &scale) in input.iter().enumerate() {
            if scale == 0.0 {
                continue;
            }
            value += *weight_ptr.add(column * output + offset) * scale;
        }
        *out_ptr.add(offset) = value;
    }
}

/// Exact four-row linear projection microkernel for AArch64 NEON machines.
///
/// Each output accumulator still visits input columns in ascending order and
/// skips zero scales, matching `Linear::apply_into()`. Four rows share each
/// weight load while retaining the column-major loop order, so input scales
/// are loaded only once per projection column.
#[cfg(target_arch = "aarch64")]
unsafe fn linear_apply_block_neon(
    inputs: &[f32],
    weights: &[f32],
    outs: &mut [f32],
    rows: usize,
    input: usize,
    output: usize,
) {
    use std::arch::aarch64::*;

    let tiled_rows = rows / 4 * 4;
    for row_base in (0..tiled_rows).step_by(4) {
        for column in 0..input {
            let scale0 = *inputs.get_unchecked(row_base * input + column);
            let scale1 = *inputs.get_unchecked((row_base + 1) * input + column);
            let scale2 = *inputs.get_unchecked((row_base + 2) * input + column);
            let scale3 = *inputs.get_unchecked((row_base + 3) * input + column);
            if scale0 == 0.0 && scale1 == 0.0 && scale2 == 0.0 && scale3 == 0.0 {
                continue;
            }

            let weight_column = weights.as_ptr().add(column * output);
            let mut output_offset = 0;
            while output_offset + 16 <= output {
                let weights = [
                    vld1q_f32(weight_column.add(output_offset)),
                    vld1q_f32(weight_column.add(output_offset + 4)),
                    vld1q_f32(weight_column.add(output_offset + 8)),
                    vld1q_f32(weight_column.add(output_offset + 12)),
                ];
                if scale0 != 0.0 {
                    add_scaled_16_neon(
                        outs.as_mut_ptr().add(row_base * output + output_offset),
                        weights,
                        scale0,
                    );
                }
                if scale1 != 0.0 {
                    add_scaled_16_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 1) * output + output_offset),
                        weights,
                        scale1,
                    );
                }
                if scale2 != 0.0 {
                    add_scaled_16_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 2) * output + output_offset),
                        weights,
                        scale2,
                    );
                }
                if scale3 != 0.0 {
                    add_scaled_16_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 3) * output + output_offset),
                        weights,
                        scale3,
                    );
                }
                output_offset += 16;
            }
            while output_offset + 4 <= output {
                let weight = vld1q_f32(weight_column.add(output_offset));
                if scale0 != 0.0 {
                    add_scaled_4_neon(
                        outs.as_mut_ptr().add(row_base * output + output_offset),
                        weight,
                        scale0,
                    );
                }
                if scale1 != 0.0 {
                    add_scaled_4_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 1) * output + output_offset),
                        weight,
                        scale1,
                    );
                }
                if scale2 != 0.0 {
                    add_scaled_4_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 2) * output + output_offset),
                        weight,
                        scale2,
                    );
                }
                if scale3 != 0.0 {
                    add_scaled_4_neon(
                        outs.as_mut_ptr()
                            .add((row_base + 3) * output + output_offset),
                        weight,
                        scale3,
                    );
                }
                output_offset += 4;
            }
            while output_offset < output {
                let weight = *weight_column.add(output_offset);
                if scale0 != 0.0 {
                    *outs.get_unchecked_mut(row_base * output + output_offset) += weight * scale0;
                }
                if scale1 != 0.0 {
                    *outs.get_unchecked_mut((row_base + 1) * output + output_offset) +=
                        weight * scale1;
                }
                if scale2 != 0.0 {
                    *outs.get_unchecked_mut((row_base + 2) * output + output_offset) +=
                        weight * scale2;
                }
                if scale3 != 0.0 {
                    *outs.get_unchecked_mut((row_base + 3) * output + output_offset) +=
                        weight * scale3;
                }
                output_offset += 1;
            }
        }
    }

    for row in tiled_rows..rows {
        let out = &mut outs[row * output..(row + 1) * output];
        for column in 0..input {
            let scale = *inputs.get_unchecked(row * input + column);
            if scale == 0.0 {
                continue;
            }
            let weight_column = &weights[column * output..(column + 1) * output];
            add_scaled_in_place_neon(out, weight_column, scale);
        }
    }
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
unsafe fn add_scaled_16_neon(
    out: *mut f32,
    weights: [std::arch::aarch64::float32x4_t; 4],
    scale: f32,
) {
    use std::arch::aarch64::*;

    let scale = vdupq_n_f32(scale);
    vst1q_f32(out, vfmaq_f32(vld1q_f32(out), weights[0], scale));
    vst1q_f32(
        out.add(4),
        vfmaq_f32(vld1q_f32(out.add(4)), weights[1], scale),
    );
    vst1q_f32(
        out.add(8),
        vfmaq_f32(vld1q_f32(out.add(8)), weights[2], scale),
    );
    vst1q_f32(
        out.add(12),
        vfmaq_f32(vld1q_f32(out.add(12)), weights[3], scale),
    );
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
unsafe fn add_scaled_4_neon(out: *mut f32, weights: std::arch::aarch64::float32x4_t, scale: f32) {
    use std::arch::aarch64::*;

    vst1q_f32(out, vfmaq_f32(vld1q_f32(out), weights, vdupq_n_f32(scale)));
}

#[cfg(target_arch = "x86_64")]
#[inline(always)]
fn x86_avx2_fma_available() -> bool {
    use std::sync::atomic::AtomicU8;
    use std::sync::atomic::Ordering;

    static AVAILABLE: AtomicU8 = AtomicU8::new(0);

    match AVAILABLE.load(Ordering::Relaxed) {
        1 => false,
        2 => true,
        _ => {
            let available =
                std::is_x86_feature_detected!("avx2") && std::is_x86_feature_detected!("fma");
            AVAILABLE.store(if available { 2 } else { 1 }, Ordering::Relaxed);
            available
        }
    }
}

#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn add_scaled_in_place_avx2_fma(out: &mut [f32], weights: &[f32], scale: f32) {
    use std::arch::x86_64::*;

    let mut offset = 0;
    let len = out.len();
    let scale_vector = _mm256_set1_ps(scale);
    while offset + 32 <= len {
        let out_ptr = out.as_mut_ptr().add(offset);
        let weight_ptr = weights.as_ptr().add(offset);
        _mm256_storeu_ps(
            out_ptr,
            _mm256_fmadd_ps(
                _mm256_loadu_ps(weight_ptr),
                scale_vector,
                _mm256_loadu_ps(out_ptr),
            ),
        );
        _mm256_storeu_ps(
            out_ptr.add(8),
            _mm256_fmadd_ps(
                _mm256_loadu_ps(weight_ptr.add(8)),
                scale_vector,
                _mm256_loadu_ps(out_ptr.add(8)),
            ),
        );
        _mm256_storeu_ps(
            out_ptr.add(16),
            _mm256_fmadd_ps(
                _mm256_loadu_ps(weight_ptr.add(16)),
                scale_vector,
                _mm256_loadu_ps(out_ptr.add(16)),
            ),
        );
        _mm256_storeu_ps(
            out_ptr.add(24),
            _mm256_fmadd_ps(
                _mm256_loadu_ps(weight_ptr.add(24)),
                scale_vector,
                _mm256_loadu_ps(out_ptr.add(24)),
            ),
        );
        offset += 32;
    }
    while offset + 8 <= len {
        let out_ptr = out.as_mut_ptr().add(offset);
        let weight_ptr = weights.as_ptr().add(offset);
        _mm256_storeu_ps(
            out_ptr,
            _mm256_fmadd_ps(
                _mm256_loadu_ps(weight_ptr),
                scale_vector,
                _mm256_loadu_ps(out_ptr),
            ),
        );
        offset += 8;
    }
    while offset < len {
        out[offset] += weights[offset] * scale;
        offset += 1;
    }
}

/// Exact four-row linear projection microkernel for AVX2/FMA machines.
///
/// Each output accumulator still visits input columns in ascending order and
/// skips zero scales, matching `Linear::apply_into()`. Changing only the row
/// and output-vector loop order lets four reviews share each weight load. Two
/// input columns are handled per inner-loop iteration to reduce loop-control
/// overhead without changing the per-output accumulation order.
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn linear_apply_block_avx2_fma(
    inputs: &[f32],
    weights: &[f32],
    outs: &mut [f32],
    rows: usize,
    input: usize,
    output: usize,
) {
    use std::arch::x86_64::*;

    let tiled_rows = rows / 4 * 4;
    for row_base in (0..tiled_rows).step_by(4) {
        let mut output_offset = 0;
        while output_offset + 8 <= output {
            let mut acc0 = _mm256_loadu_ps(outs.as_ptr().add(row_base * output + output_offset));
            let mut acc1 =
                _mm256_loadu_ps(outs.as_ptr().add((row_base + 1) * output + output_offset));
            let mut acc2 =
                _mm256_loadu_ps(outs.as_ptr().add((row_base + 2) * output + output_offset));
            let mut acc3 =
                _mm256_loadu_ps(outs.as_ptr().add((row_base + 3) * output + output_offset));

            let mut column = 0;
            while column + 2 <= input {
                let weight0 =
                    _mm256_loadu_ps(weights.as_ptr().add(column * output + output_offset));
                let scale00 = *inputs.get_unchecked(row_base * input + column);
                let scale10 = *inputs.get_unchecked((row_base + 1) * input + column);
                let scale20 = *inputs.get_unchecked((row_base + 2) * input + column);
                let scale30 = *inputs.get_unchecked((row_base + 3) * input + column);
                if scale00 != 0.0 {
                    acc0 = _mm256_fmadd_ps(weight0, _mm256_set1_ps(scale00), acc0);
                }
                if scale10 != 0.0 {
                    acc1 = _mm256_fmadd_ps(weight0, _mm256_set1_ps(scale10), acc1);
                }
                if scale20 != 0.0 {
                    acc2 = _mm256_fmadd_ps(weight0, _mm256_set1_ps(scale20), acc2);
                }
                if scale30 != 0.0 {
                    acc3 = _mm256_fmadd_ps(weight0, _mm256_set1_ps(scale30), acc3);
                }

                let weight1 =
                    _mm256_loadu_ps(weights.as_ptr().add((column + 1) * output + output_offset));
                let scale01 = *inputs.get_unchecked(row_base * input + column + 1);
                let scale11 = *inputs.get_unchecked((row_base + 1) * input + column + 1);
                let scale21 = *inputs.get_unchecked((row_base + 2) * input + column + 1);
                let scale31 = *inputs.get_unchecked((row_base + 3) * input + column + 1);
                if scale01 != 0.0 {
                    acc0 = _mm256_fmadd_ps(weight1, _mm256_set1_ps(scale01), acc0);
                }
                if scale11 != 0.0 {
                    acc1 = _mm256_fmadd_ps(weight1, _mm256_set1_ps(scale11), acc1);
                }
                if scale21 != 0.0 {
                    acc2 = _mm256_fmadd_ps(weight1, _mm256_set1_ps(scale21), acc2);
                }
                if scale31 != 0.0 {
                    acc3 = _mm256_fmadd_ps(weight1, _mm256_set1_ps(scale31), acc3);
                }

                column += 2;
            }
            if column < input {
                let weight = _mm256_loadu_ps(weights.as_ptr().add(column * output + output_offset));
                let scale0 = *inputs.get_unchecked(row_base * input + column);
                let scale1 = *inputs.get_unchecked((row_base + 1) * input + column);
                let scale2 = *inputs.get_unchecked((row_base + 2) * input + column);
                let scale3 = *inputs.get_unchecked((row_base + 3) * input + column);
                if scale0 != 0.0 {
                    acc0 = _mm256_fmadd_ps(weight, _mm256_set1_ps(scale0), acc0);
                }
                if scale1 != 0.0 {
                    acc1 = _mm256_fmadd_ps(weight, _mm256_set1_ps(scale1), acc1);
                }
                if scale2 != 0.0 {
                    acc2 = _mm256_fmadd_ps(weight, _mm256_set1_ps(scale2), acc2);
                }
                if scale3 != 0.0 {
                    acc3 = _mm256_fmadd_ps(weight, _mm256_set1_ps(scale3), acc3);
                }
            }

            _mm256_storeu_ps(
                outs.as_mut_ptr().add(row_base * output + output_offset),
                acc0,
            );
            _mm256_storeu_ps(
                outs.as_mut_ptr()
                    .add((row_base + 1) * output + output_offset),
                acc1,
            );
            _mm256_storeu_ps(
                outs.as_mut_ptr()
                    .add((row_base + 2) * output + output_offset),
                acc2,
            );
            _mm256_storeu_ps(
                outs.as_mut_ptr()
                    .add((row_base + 3) * output + output_offset),
                acc3,
            );
            output_offset += 8;
        }

        while output_offset < output {
            let mut acc0 = *outs.get_unchecked(row_base * output + output_offset);
            let mut acc1 = *outs.get_unchecked((row_base + 1) * output + output_offset);
            let mut acc2 = *outs.get_unchecked((row_base + 2) * output + output_offset);
            let mut acc3 = *outs.get_unchecked((row_base + 3) * output + output_offset);
            let mut column = 0;
            while column + 2 <= input {
                let weight0 = *weights.get_unchecked(column * output + output_offset);
                let scale00 = *inputs.get_unchecked(row_base * input + column);
                let scale10 = *inputs.get_unchecked((row_base + 1) * input + column);
                let scale20 = *inputs.get_unchecked((row_base + 2) * input + column);
                let scale30 = *inputs.get_unchecked((row_base + 3) * input + column);
                if scale00 != 0.0 {
                    acc0 += weight0 * scale00;
                }
                if scale10 != 0.0 {
                    acc1 += weight0 * scale10;
                }
                if scale20 != 0.0 {
                    acc2 += weight0 * scale20;
                }
                if scale30 != 0.0 {
                    acc3 += weight0 * scale30;
                }

                let weight1 = *weights.get_unchecked((column + 1) * output + output_offset);
                let scale01 = *inputs.get_unchecked(row_base * input + column + 1);
                let scale11 = *inputs.get_unchecked((row_base + 1) * input + column + 1);
                let scale21 = *inputs.get_unchecked((row_base + 2) * input + column + 1);
                let scale31 = *inputs.get_unchecked((row_base + 3) * input + column + 1);
                if scale01 != 0.0 {
                    acc0 += weight1 * scale01;
                }
                if scale11 != 0.0 {
                    acc1 += weight1 * scale11;
                }
                if scale21 != 0.0 {
                    acc2 += weight1 * scale21;
                }
                if scale31 != 0.0 {
                    acc3 += weight1 * scale31;
                }

                column += 2;
            }
            if column < input {
                let weight = *weights.get_unchecked(column * output + output_offset);
                let scale0 = *inputs.get_unchecked(row_base * input + column);
                let scale1 = *inputs.get_unchecked((row_base + 1) * input + column);
                let scale2 = *inputs.get_unchecked((row_base + 2) * input + column);
                let scale3 = *inputs.get_unchecked((row_base + 3) * input + column);
                if scale0 != 0.0 {
                    acc0 += weight * scale0;
                }
                if scale1 != 0.0 {
                    acc1 += weight * scale1;
                }
                if scale2 != 0.0 {
                    acc2 += weight * scale2;
                }
                if scale3 != 0.0 {
                    acc3 += weight * scale3;
                }
            }
            *outs.get_unchecked_mut(row_base * output + output_offset) = acc0;
            *outs.get_unchecked_mut((row_base + 1) * output + output_offset) = acc1;
            *outs.get_unchecked_mut((row_base + 2) * output + output_offset) = acc2;
            *outs.get_unchecked_mut((row_base + 3) * output + output_offset) = acc3;
            output_offset += 1;
        }
    }

    for row in tiled_rows..rows {
        let out = &mut outs[row * output..(row + 1) * output];
        for column in 0..input {
            let scale = *inputs.get_unchecked(row * input + column);
            if scale == 0.0 {
                continue;
            }
            let weight_column = &weights[column * output..(column + 1) * output];
            add_scaled_in_place_avx2_fma(out, weight_column, scale);
        }
    }
}

#[cfg(not(target_arch = "aarch64"))]
#[inline(always)]
fn add_scaled_in_place_scalar(out: &mut [f32], weights: &[f32], scale: f32) {
    for (output, weight) in out.iter_mut().zip(weights) {
        *output += weight * scale;
    }
}

#[inline(always)]
fn dot_product(left: &[f32], right: &[f32]) -> f32 {
    debug_assert_eq!(left.len(), right.len());
    dot_product_arch(left, right)
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
fn dot_product_arch(left: &[f32], right: &[f32]) -> f32 {
    // SAFETY: aarch64 guarantees NEON support, and the helper only uses
    // unaligned loads within the bounds checked by its loop conditions.
    unsafe { dot_product_neon(left, right) }
}

#[cfg(target_arch = "x86_64")]
#[inline(always)]
fn dot_product_arch(left: &[f32], right: &[f32]) -> f32 {
    if x86_avx2_fma_available() {
        // SAFETY: availability was checked above, and the helper only uses
        // unaligned loads within the bounds checked by its loops.
        unsafe { dot_product_avx2_fma(left, right) }
    } else {
        dot_product_scalar(left, right)
    }
}

#[cfg(all(not(target_arch = "aarch64"), not(target_arch = "x86_64")))]
#[inline(always)]
fn dot_product_arch(left: &[f32], right: &[f32]) -> f32 {
    dot_product_scalar(left, right)
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
unsafe fn dot_product_neon(left: &[f32], right: &[f32]) -> f32 {
    use std::arch::aarch64::*;

    let mut offset = 0;
    let len = left.len();
    let mut acc0 = vdupq_n_f32(0.0);
    let mut acc1 = vdupq_n_f32(0.0);
    let mut acc2 = vdupq_n_f32(0.0);
    let mut acc3 = vdupq_n_f32(0.0);

    while offset + 16 <= len {
        let left_ptr = left.as_ptr().add(offset);
        let right_ptr = right.as_ptr().add(offset);
        acc0 = vfmaq_f32(acc0, vld1q_f32(left_ptr), vld1q_f32(right_ptr));
        acc1 = vfmaq_f32(
            acc1,
            vld1q_f32(left_ptr.add(4)),
            vld1q_f32(right_ptr.add(4)),
        );
        acc2 = vfmaq_f32(
            acc2,
            vld1q_f32(left_ptr.add(8)),
            vld1q_f32(right_ptr.add(8)),
        );
        acc3 = vfmaq_f32(
            acc3,
            vld1q_f32(left_ptr.add(12)),
            vld1q_f32(right_ptr.add(12)),
        );
        offset += 16;
    }

    let mut acc = vaddq_f32(vaddq_f32(acc0, acc1), vaddq_f32(acc2, acc3));
    while offset + 4 <= len {
        acc = vfmaq_f32(
            acc,
            vld1q_f32(left.as_ptr().add(offset)),
            vld1q_f32(right.as_ptr().add(offset)),
        );
        offset += 4;
    }

    let mut sum = vaddvq_f32(acc);
    while offset < len {
        sum += left[offset] * right[offset];
        offset += 1;
    }
    sum
}

#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn dot_product_avx2_fma(left: &[f32], right: &[f32]) -> f32 {
    use std::arch::x86_64::*;

    let mut offset = 0;
    let len = left.len();
    let mut acc0 = _mm256_setzero_ps();
    let mut acc1 = _mm256_setzero_ps();
    let mut acc2 = _mm256_setzero_ps();
    let mut acc3 = _mm256_setzero_ps();

    while offset + 32 <= len {
        let left_ptr = left.as_ptr().add(offset);
        let right_ptr = right.as_ptr().add(offset);
        acc0 = _mm256_fmadd_ps(_mm256_loadu_ps(left_ptr), _mm256_loadu_ps(right_ptr), acc0);
        acc1 = _mm256_fmadd_ps(
            _mm256_loadu_ps(left_ptr.add(8)),
            _mm256_loadu_ps(right_ptr.add(8)),
            acc1,
        );
        acc2 = _mm256_fmadd_ps(
            _mm256_loadu_ps(left_ptr.add(16)),
            _mm256_loadu_ps(right_ptr.add(16)),
            acc2,
        );
        acc3 = _mm256_fmadd_ps(
            _mm256_loadu_ps(left_ptr.add(24)),
            _mm256_loadu_ps(right_ptr.add(24)),
            acc3,
        );
        offset += 32;
    }

    let mut acc = _mm256_add_ps(_mm256_add_ps(acc0, acc1), _mm256_add_ps(acc2, acc3));
    while offset + 8 <= len {
        acc = _mm256_fmadd_ps(
            _mm256_loadu_ps(left.as_ptr().add(offset)),
            _mm256_loadu_ps(right.as_ptr().add(offset)),
            acc,
        );
        offset += 8;
    }

    let mut lanes = [0.0_f32; 8];
    _mm256_storeu_ps(lanes.as_mut_ptr(), acc);
    let mut sum = lanes.iter().copied().sum::<f32>();
    while offset < len {
        sum += left[offset] * right[offset];
        offset += 1;
    }
    sum
}

#[cfg(not(target_arch = "aarch64"))]
#[inline(always)]
fn dot_product_scalar(left: &[f32], right: &[f32]) -> f32 {
    left.iter()
        .zip(right)
        .map(|(left, right)| left * right)
        .sum()
}

struct Norm {
    groups: usize,
    dim: usize,
    eps: f32,
    weight: Vec<f32>,
    bias: Vec<f32>,
}

impl Norm {
    fn apply(&self, input: &[f32]) -> Vec<f32> {
        let mut out = vec![0.0; self.dim];
        self.apply_into(input, &mut out);
        out
    }

    fn apply_into(&self, input: &[f32], out: &mut [f32]) {
        #[cfg(test)]
        let profile_started = rwkv_warmup_profile_start();
        debug_assert_eq!(input.len(), self.dim);
        debug_assert_eq!(out.len(), self.dim);
        let group_size = self.dim / self.groups;

        for group in 0..self.groups {
            let start = group * group_size;
            let end = start + group_size;
            let values = &input[start..end];
            let mean = values.iter().sum::<f32>() / group_size as f32;
            let variance = values
                .iter()
                .map(|value| {
                    let diff = value - mean;
                    diff * diff
                })
                .sum::<f32>()
                / group_size as f32;
            let scale = (variance + self.eps).sqrt().recip();
            for index in start..end {
                out[index] = (input[index] - mean) * scale * self.weight[index] + self.bias[index];
            }
        }

        #[cfg(test)]
        rwkv_warmup_profile_record(RwkvWarmupProfileBucket::Norm, profile_started);
    }

    #[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
    fn apply_batch(&self, input: &[f32], rows: usize, out: &mut Vec<f32>) {
        assert_eq!(input.len(), rows * self.dim);
        out.resize(rows * self.dim, 0.0);
        // Accumulate independent rows together, keeping each row's reduction
        // order unchanged so SIMD does not change normalization results.
        const LANES: usize = 4;
        let group_size = self.dim / self.groups;

        #[cfg(target_arch = "aarch64")]
        if group_size.is_multiple_of(4) && group_size * self.groups == self.dim {
            assert!(self.weight.len() == self.dim && self.bias.len() == self.dim);
            let mut row = 0;
            // SAFETY: aarch64 guarantees NEON support. Each call reads and
            // writes the `4 * BLOCKS` complete rows starting at `row`, which
            // lie within the lengths checked above.
            unsafe {
                while row + 16 <= rows {
                    query_kernels::norm_rows_neon::<4>(
                        input.as_ptr().add(row * self.dim),
                        out.as_mut_ptr().add(row * self.dim),
                        self.dim,
                        self.groups,
                        self.eps,
                        &self.weight,
                        &self.bias,
                    );
                    row += 16;
                }
                while row + 4 <= rows {
                    query_kernels::norm_rows_neon::<1>(
                        input.as_ptr().add(row * self.dim),
                        out.as_mut_ptr().add(row * self.dim),
                        self.dim,
                        self.groups,
                        self.eps,
                        &self.weight,
                        &self.bias,
                    );
                    row += 4;
                }
            }
            out[row * self.dim..]
                .chunks_mut(self.dim)
                .zip(input[row * self.dim..].chunks(self.dim))
                .for_each(|(output, input)| self.apply_into(input, output));
            return;
        }

        let mut inputs = input.chunks_exact(self.dim * LANES);
        let mut outputs = out.chunks_exact_mut(self.dim * LANES);
        for (input, output) in inputs.by_ref().zip(outputs.by_ref()) {
            for group in 0..self.groups {
                let start = group * group_size;
                let end = start + group_size;
                let mut means = [-0.0; LANES];
                for channel in start..end {
                    for lane in 0..LANES {
                        means[lane] += input[lane * self.dim + channel];
                    }
                }
                for mean in &mut means {
                    *mean /= group_size as f32;
                }
                let mut scales = [-0.0; LANES];
                for channel in start..end {
                    for lane in 0..LANES {
                        let diff = input[lane * self.dim + channel] - means[lane];
                        scales[lane] += diff * diff;
                    }
                }
                for scale in &mut scales {
                    *scale = (*scale / group_size as f32 + self.eps).sqrt().recip();
                }
                for lane in 0..LANES {
                    for channel in start..end {
                        let index = lane * self.dim + channel;
                        output[index] =
                            (input[index] - means[lane]) * scales[lane] * self.weight[channel]
                                + self.bias[channel];
                    }
                }
            }
        }
        outputs
            .into_remainder()
            .chunks_mut(self.dim)
            .zip(inputs.remainder().chunks(self.dim))
            .for_each(|(output, input)| self.apply_into(input, output));
    }
}

fn single_timestep(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: &[f32],
) -> (Vec<f32>, Vec<f32>) {
    let mut next_state = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
    let mut out = vec![0.0; D_MODEL];
    single_timestep_into(r, k, v, w, a, k_deformed, state, &mut out, &mut next_state);
    (out, next_state)
}

#[allow(clippy::too_many_arguments)]
fn single_timestep_into(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: &[f32],
    out: &mut [f32],
    next_state: &mut [f32],
) {
    #[cfg(test)]
    let profile_started =
        if RWKV_SINGLE_TIMESTEP_PROFILE_ENABLED.load(std::sync::atomic::Ordering::Relaxed) {
            Some(std::time::Instant::now())
        } else {
            None
        };
    #[cfg(test)]
    let warmup_profile_started = rwkv_warmup_profile_start();

    debug_assert_eq!(state.len(), HEADS * HEAD_SIZE * HEAD_SIZE);
    debug_assert_eq!(next_state.len(), HEADS * HEAD_SIZE * HEAD_SIZE);
    debug_assert_eq!(out.len(), D_MODEL);

    for head in 0..HEADS {
        let head_base = head * HEAD_SIZE;
        let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
        let mut state_dot_k = [0.0_f32; HEAD_SIZE];
        let key_deformed = &k_deformed[head_base..head_base + HEAD_SIZE];
        let receptance = &r[head_base..head_base + HEAD_SIZE];

        for (row, value) in state_dot_k.iter_mut().enumerate() {
            let row_start = matrix_base + row * HEAD_SIZE;
            let state_row = &state[row_start..row_start + HEAD_SIZE];
            *value = dot_product(state_row, key_deformed);
        }

        update_recurrence_head(
            &k[head_base..head_base + HEAD_SIZE],
            &v[head_base..head_base + HEAD_SIZE],
            &w[head_base..head_base + HEAD_SIZE],
            &a[head_base..head_base + HEAD_SIZE],
            key_deformed,
            &state[matrix_base..matrix_base + HEAD_SIZE * HEAD_SIZE],
            &state_dot_k,
            &mut next_state[matrix_base..matrix_base + HEAD_SIZE * HEAD_SIZE],
        );

        for row in 0..HEAD_SIZE {
            let row_start = matrix_base + row * HEAD_SIZE;
            let state_row = &next_state[row_start..row_start + HEAD_SIZE];
            out[head_base + row] = dot_product(state_row, receptance);
        }
    }

    #[cfg(test)]
    if let Some(started) = profile_started {
        RWKV_SINGLE_TIMESTEP_PROFILE_CALLS.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        RWKV_SINGLE_TIMESTEP_PROFILE_NANOS.fetch_add(
            started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64,
            std::sync::atomic::Ordering::Relaxed,
        );
    }
    #[cfg(test)]
    rwkv_warmup_profile_record(
        RwkvWarmupProfileBucket::SingleTimestep,
        warmup_profile_started,
    );
}

#[allow(clippy::too_many_arguments)]
#[inline(always)]
fn update_recurrence_head(
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    key_deformed: &[f32],
    state: &[f32],
    state_dot_k: &[f32; HEAD_SIZE],
    next_state: &mut [f32],
) {
    #[cfg(target_arch = "x86_64")]
    if x86_avx2_fma_available() {
        // SAFETY: availability was checked above. Every head is exactly
        // `HEAD_SIZE` square and `HEAD_SIZE` is a multiple of eight.
        unsafe {
            update_recurrence_head_avx2(k, v, w, a, key_deformed, state, state_dot_k, next_state);
        }
        return;
    }

    update_recurrence_head_scalar(k, v, w, a, key_deformed, state, state_dot_k, next_state);
}

#[allow(clippy::too_many_arguments)]
fn update_recurrence_head_scalar(
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    key_deformed: &[f32],
    state: &[f32],
    state_dot_k: &[f32; HEAD_SIZE],
    next_state: &mut [f32],
) {
    for row in 0..HEAD_SIZE {
        for column in 0..HEAD_SIZE {
            let old = state[row * HEAD_SIZE + column];
            next_state[row * HEAD_SIZE + column] = old * w[column]
                - state_dot_k[row] * a[column] * key_deformed[column]
                + v[row] * k[column];
        }
    }
}

#[cfg(target_arch = "x86_64")]
#[allow(clippy::too_many_arguments)]
#[target_feature(enable = "avx2")]
unsafe fn update_recurrence_head_avx2(
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    key_deformed: &[f32],
    state: &[f32],
    state_dot_k: &[f32; HEAD_SIZE],
    next_state: &mut [f32],
) {
    use std::arch::x86_64::*;

    debug_assert_eq!(k.len(), HEAD_SIZE);
    debug_assert_eq!(v.len(), HEAD_SIZE);
    debug_assert_eq!(w.len(), HEAD_SIZE);
    debug_assert_eq!(a.len(), HEAD_SIZE);
    debug_assert_eq!(key_deformed.len(), HEAD_SIZE);
    debug_assert_eq!(state.len(), HEAD_SIZE * HEAD_SIZE);
    debug_assert_eq!(next_state.len(), HEAD_SIZE * HEAD_SIZE);

    for row in 0..HEAD_SIZE {
        let state_dot = _mm256_set1_ps(state_dot_k[row]);
        let value = _mm256_set1_ps(v[row]);
        let row_base = row * HEAD_SIZE;
        for column in (0..HEAD_SIZE).step_by(8) {
            let old = _mm256_loadu_ps(state.as_ptr().add(row_base + column));
            let weighted = _mm256_mul_ps(old, _mm256_loadu_ps(w.as_ptr().add(column)));
            let penalty = _mm256_mul_ps(
                _mm256_mul_ps(state_dot, _mm256_loadu_ps(a.as_ptr().add(column))),
                _mm256_loadu_ps(key_deformed.as_ptr().add(column)),
            );
            let injected = _mm256_mul_ps(value, _mm256_loadu_ps(k.as_ptr().add(column)));
            let updated = _mm256_add_ps(_mm256_sub_ps(weighted, penalty), injected);
            _mm256_storeu_ps(next_state.as_mut_ptr().add(row_base + column), updated);
        }
    }
}

#[cfg(any(not(any(target_os = "macos", target_arch = "aarch64")), test))]
#[allow(clippy::too_many_arguments)]
fn single_timestep_query_into(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: Option<&[f32]>,
    out: &mut [f32],
    next_row: &mut [f32],
) {
    debug_assert_eq!(out.len(), D_MODEL);
    debug_assert_eq!(next_row.len(), HEAD_SIZE);
    debug_assert!(
        state.is_none() || state.is_some_and(|state| state.len() == HEADS * HEAD_SIZE * HEAD_SIZE)
    );

    for head in 0..HEADS {
        let head_base = head * HEAD_SIZE;
        let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
        let mut state_dot_k = [0.0_f32; HEAD_SIZE];
        let key_deformed = &k_deformed[head_base..head_base + HEAD_SIZE];
        let receptance = &r[head_base..head_base + HEAD_SIZE];

        if let Some(state) = state {
            for (row, value) in state_dot_k.iter_mut().enumerate() {
                let row_start = matrix_base + row * HEAD_SIZE;
                *value = dot_product(&state[row_start..row_start + HEAD_SIZE], key_deformed);
            }
        }

        for row in 0..HEAD_SIZE {
            for (column, next) in next_row.iter_mut().enumerate().take(HEAD_SIZE) {
                let channel = head_base + column;
                let matrix_index = matrix_base + row * HEAD_SIZE + column;
                let old = state.map_or(0.0, |state| state[matrix_index]);
                *next = old * w[channel] - state_dot_k[row] * a[channel] * k_deformed[channel]
                    + v[head_base + row] * k[channel];
            }
            out[head_base + row] = dot_product(next_row, receptance);
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn single_timestep_query_fast_into(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: Option<&[f32]>,
    out: &mut [f32],
) {
    assert!(
        [r, k, v, w, a, k_deformed]
            .iter()
            .all(|values| values.len() == D_MODEL)
            && out.len() == D_MODEL
    );
    assert!(
        state.is_none() || state.is_some_and(|state| state.len() == HEADS * HEAD_SIZE * HEAD_SIZE)
    );

    #[cfg(target_arch = "aarch64")]
    // SAFETY: aarch64 guarantees NEON support, and every slice length was
    // checked above; the kernel only reads and writes within those lengths.
    unsafe {
        query_kernels::single_timestep_query_fast_neon(r, k, v, w, a, k_deformed, state, out);
    }

    #[cfg(not(target_arch = "aarch64"))]
    single_timestep_query_fast_scalar(r, k, v, w, a, k_deformed, state, out);
}

/// Portable reference for `single_timestep_query_fast_into()`.
#[cfg(any(not(target_arch = "aarch64"), test))]
#[allow(clippy::too_many_arguments)]
fn single_timestep_query_fast_scalar(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: Option<&[f32]>,
    out: &mut [f32],
) {
    for head in 0..HEADS {
        let head_base = head * HEAD_SIZE;
        let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
        let receptance = &r[head_base..head_base + HEAD_SIZE];
        let key = &k[head_base..head_base + HEAD_SIZE];
        let key_deformed = &k_deformed[head_base..head_base + HEAD_SIZE];
        let key_receptance = dot_product(key, receptance);

        let Some(state) = state else {
            for row in 0..HEAD_SIZE {
                out[head_base + row] = v[head_base + row] * key_receptance;
            }
            continue;
        };

        let mut decayed_receptance = [0.0; HEAD_SIZE];
        let mut adaptation_receptance = 0.0;
        for column in 0..HEAD_SIZE {
            let channel = head_base + column;
            decayed_receptance[column] = w[channel] * receptance[column];
            adaptation_receptance += a[channel] * key_deformed[column] * receptance[column];
        }

        for row in 0..HEAD_SIZE {
            let row_start = matrix_base + row * HEAD_SIZE;
            let state_row = &state[row_start..row_start + HEAD_SIZE];
            let state_dot_key = dot_product(state_row, key_deformed);
            let retained = dot_product(state_row, &decayed_receptance);
            out[head_base + row] = retained - state_dot_key * adaptation_receptance
                + v[head_base + row] * key_receptance;
        }
    }
}

fn normalize_heads_in_place(values: &mut [f32]) {
    for head in 0..HEADS {
        let start = head * HEAD_SIZE;
        let end = start + HEAD_SIZE;
        let norm = values[start..end]
            .iter()
            .map(|value| value * value)
            .sum::<f32>()
            .sqrt()
            .max(1e-12);
        for value in &mut values[start..end] {
            *value /= norm;
        }
    }
}

/// Writes row `row` of all eight time-mix inputs,
/// `mixed[mix] = lerp(x, x_shift, lerp_weights[mix])`, in one pass. Sharing
/// `x_shift - x` between the mixes keeps `lerp()`'s exact arithmetic.
#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn time_shift_mix_row(
    x: &[f32],
    x_shift: &[f32],
    lerp_weights: &[f32],
    mixed: &mut [Vec<f32>; 8],
    row: usize,
) {
    assert!(x.len() == D_MODEL && x_shift.len() == D_MODEL);
    assert_eq!(lerp_weights.len(), mixed.len() * D_MODEL);
    let mut shift_delta = [0.0; D_MODEL];
    for channel in 0..D_MODEL {
        shift_delta[channel] = x_shift[channel] - x[channel];
    }
    for (mixed, weights) in mixed.iter_mut().zip(lerp_weights.as_chunks::<D_MODEL>().0) {
        let mixed = &mut mixed[row * D_MODEL..(row + 1) * D_MODEL];
        for channel in 0..D_MODEL {
            mixed[channel] = x[channel] + weights[channel] * shift_delta[channel];
        }
    }
}

/// `normalize_heads_in_place()` followed by `scale_heads_in_place()`.
#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn normalize_scale_heads_in_place(values: &mut [f32], scales: &[f32]) {
    assert!(values.len() == D_MODEL && scales.len() == HEADS);

    #[cfg(target_arch = "aarch64")]
    // SAFETY: aarch64 guarantees NEON support, and both lengths were checked
    // above; the kernel only accesses elements within them.
    unsafe {
        query_kernels::normalize_scale_heads_neon(values, scales);
    }

    #[cfg(not(target_arch = "aarch64"))]
    {
        normalize_heads_in_place(values);
        scale_heads_in_place(values, scales);
    }
}

/// Adds the per-head bonus to the group-normed recurrence output and applies
/// the output gate: `output = g * (output + sum_head(r * bonus * k) * v)`.
#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn bonus_gate_in_place(
    output: &mut [f32],
    r: &[f32],
    bonus: &[f32],
    k: &[f32],
    v: &[f32],
    g: &[f32],
) {
    assert!([&*output, r, bonus, k, v, g]
        .iter()
        .all(|values| values.len() == D_MODEL));

    #[cfg(target_arch = "aarch64")]
    // SAFETY: aarch64 guarantees NEON support, and every length was checked
    // above; the kernel only accesses elements within them.
    unsafe {
        query_kernels::bonus_gate_neon(output, r, bonus, k, v, g);
    }

    #[cfg(not(target_arch = "aarch64"))]
    bonus_gate_scalar(output, r, bonus, k, v, g);
}

/// Portable reference for `bonus_gate_in_place()`.
#[cfg(any(all(target_os = "macos", not(target_arch = "aarch64")), test))]
fn bonus_gate_scalar(
    output: &mut [f32],
    r: &[f32],
    bonus: &[f32],
    k: &[f32],
    v: &[f32],
    g: &[f32],
) {
    for head in 0..HEADS {
        let head_base = head * HEAD_SIZE;
        let mut bonus_scale = 0.0;
        for channel in head_base..head_base + HEAD_SIZE {
            bonus_scale += r[channel] * bonus[channel] * k[channel];
        }
        for channel in head_base..head_base + HEAD_SIZE {
            output[channel] = g[channel] * (output[channel] + bonus_scale * v[channel]);
        }
    }
}

#[cfg(any(all(target_os = "macos", not(target_arch = "aarch64")), test))]
fn scale_heads_in_place(values: &mut [f32], scales: &[f32]) {
    debug_assert_eq!(values.len(), D_MODEL);
    debug_assert_eq!(scales.len(), HEADS);
    for head in 0..HEADS {
        for index in 0..HEAD_SIZE {
            values[head * HEAD_SIZE + index] *= scales[head];
        }
    }
}

fn softmax(input: &[f32]) -> Vec<f32> {
    let max = input
        .iter()
        .copied()
        .fold(f32::NEG_INFINITY, |a, b| a.max(b));
    let mut out = input
        .iter()
        .map(|value| (*value - max).exp())
        .collect::<Vec<_>>();
    let sum = out.iter().sum::<f32>();
    for value in &mut out {
        *value /= sum;
    }
    out
}

fn softmax_into(input: &[f32], out: &mut [f32]) {
    debug_assert_eq!(input.len(), out.len());
    let max = input
        .iter()
        .copied()
        .fold(f32::NEG_INFINITY, |a, b| a.max(b));
    for (output, value) in out.iter_mut().zip(input) {
        *output = (*value - max).exp();
    }
    let sum = out.iter().sum::<f32>();
    for value in out {
        *value /= sum;
    }
}

fn sigmoid_in_place(values: &mut [f32]) {
    for value in values {
        *value = sigmoid(*value);
    }
}

fn sigmoid(value: f32) -> f32 {
    if value >= 0.0 {
        1.0 / (1.0 + (-value).exp())
    } else {
        let exp = value.exp();
        exp / (1.0 + exp)
    }
}

// exp(-0.5), rounded to f32.
#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
const QUERY_DECAY_SCALE: f32 = 0.606_530_67;

/// Scalar reference for `query_decay_batch_in_place()`, which also uses it off
/// macOS (phones).
#[cfg(any(all(target_arch = "aarch64", not(target_os = "macos")), test))]
fn query_decay(value: f32) -> f32 {
    // exp(-exp(-0.5 - softplus(-x))) = exp(-exp(-0.5) * sigmoid(x)).
    // Query-only: recurrent state updates keep their original arithmetic.
    (-QUERY_DECAY_SCALE * sigmoid(value)).exp()
}

// Batched query-only activations. On macOS they use Accelerate's vectorized
// transcendental functions, which may differ from the scalar ones in the last
// bit; answer rows and warm-up state updates keep the scalar functions.

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn query_sigmoid_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::sigmoid_in_place(values);
    #[cfg(not(target_os = "macos"))]
    sigmoid_in_place(values);
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn query_silu_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::silu_in_place(values);
    #[cfg(not(target_os = "macos"))]
    silu_in_place(values);
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn query_tanh_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::tanh_in_place(values);
    #[cfg(not(target_os = "macos"))]
    values.iter_mut().for_each(|value| *value = value.tanh());
}

#[cfg(any(target_os = "macos", target_arch = "aarch64", test))]
fn query_decay_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::scaled_sigmoid_decay_in_place(values, QUERY_DECAY_SCALE);
    #[cfg(not(target_os = "macos"))]
    values
        .iter_mut()
        .for_each(|value| *value = query_decay(*value));
}

fn softplus(value: f32) -> f32 {
    if value > 20.0 {
        value
    } else if value < -20.0 {
        value.exp()
    } else {
        value.exp().ln_1p()
    }
}

fn silu_in_place(values: &mut [f32]) {
    for value in values {
        *value *= sigmoid(*value);
    }
}

fn relu_in_place(values: &mut [f32]) {
    for value in values {
        *value = value.max(0.0);
    }
}

fn lerp(start: f32, end: f32, weight: f32) -> f32 {
    start + weight * (end - start)
}

#[cfg(test)]
#[derive(Clone, Copy)]
struct StateCompression {
    shift_bits: Option<u8>,
    matrix_rank: usize,
    matrix_bits: Option<u8>,
    power_iterations: usize,
}

#[cfg(test)]
fn compressed_srs_state(state: SrsState, compression: StateCompression) -> SrsState {
    SrsState {
        card: compressed_module_state(state.card, compression),
        deck: compressed_module_state(state.deck, compression),
        note: compressed_module_state(state.note, compression),
        preset: compressed_module_state(state.preset, compression),
        global: compressed_module_state(state.global, compression),
    }
}

#[cfg(test)]
fn compressed_module_state(mut state: ModuleState, compression: StateCompression) -> ModuleState {
    for layer in &mut state.layers {
        if let Some(time) = layer.time.as_mut() {
            if let Some(bits) = compression.shift_bits {
                quantize_dequantize_symmetric(&mut time.x_shift, bits);
            }
            time.matrix = low_rank_matrix_state(&time.matrix, compression);
        }
        if let (Some(bits), Some(channel_shift)) =
            (compression.shift_bits, layer.channel_shift.as_mut())
        {
            quantize_dequantize_symmetric(channel_shift, bits);
        }
    }
    state
}

#[cfg(test)]
fn low_rank_matrix_state(matrix: &[f32], compression: StateCompression) -> Vec<f32> {
    debug_assert_eq!(matrix.len(), HEADS * HEAD_SIZE * HEAD_SIZE);
    let mut out = vec![0.0; matrix.len()];
    for head in 0..HEADS {
        let start = head * HEAD_SIZE * HEAD_SIZE;
        let end = start + HEAD_SIZE * HEAD_SIZE;
        low_rank_head_matrix(
            &matrix[start..end],
            &mut out[start..end],
            compression.matrix_rank,
            compression.matrix_bits,
            compression.power_iterations,
        );
    }
    out
}

#[cfg(test)]
fn low_rank_head_matrix(
    matrix: &[f32],
    out: &mut [f32],
    rank: usize,
    bits: Option<u8>,
    power_iterations: usize,
) {
    debug_assert_eq!(matrix.len(), HEAD_SIZE * HEAD_SIZE);
    debug_assert_eq!(out.len(), HEAD_SIZE * HEAD_SIZE);
    if rank == 0 {
        return;
    }

    let mut residual = matrix.to_vec();
    let mut left = vec![0.0; HEAD_SIZE];
    let mut right = vec![0.0; HEAD_SIZE];
    let mut next_left = vec![0.0; HEAD_SIZE];

    for component in 0..rank {
        initialize_power_vector(&residual, &mut left, component);
        for _ in 0..power_iterations {
            multiply_transpose_matrix_vector(&residual, &left, &mut right);
            let right_norm = normalize_vector(&mut right);
            if right_norm <= 1e-12 {
                break;
            }
            multiply_matrix_vector(&residual, &right, &mut next_left);
            let left_norm = normalize_vector(&mut next_left);
            if left_norm <= 1e-12 {
                break;
            }
            left.copy_from_slice(&next_left);
        }

        multiply_transpose_matrix_vector(&residual, &left, &mut right);
        let sigma = normalize_vector(&mut right);
        if sigma <= 1e-12 || !sigma.is_finite() {
            break;
        }

        let root = sigma.sqrt();
        let mut left_factor = left.iter().map(|value| value * root).collect::<Vec<_>>();
        let mut right_factor = right.iter().map(|value| value * root).collect::<Vec<_>>();
        if let Some(bits) = bits {
            quantize_dequantize_symmetric(&mut left_factor, bits);
            quantize_dequantize_symmetric(&mut right_factor, bits);
        }

        for (row, &left) in left_factor.iter().enumerate() {
            for (column, &right) in right_factor.iter().enumerate() {
                let index = row * HEAD_SIZE + column;
                let value = left * right;
                out[index] += value;
                residual[index] -= value;
            }
        }
    }
}

#[cfg(test)]
fn initialize_power_vector(matrix: &[f32], out: &mut [f32], component: usize) {
    for (row, out) in out.iter_mut().enumerate().take(HEAD_SIZE) {
        let row_start = row * HEAD_SIZE;
        let row_sum = matrix[row_start..row_start + HEAD_SIZE]
            .iter()
            .copied()
            .sum::<f32>();
        *out = row_sum + 0.001 * ((row + component + 1) as f32);
    }
    if normalize_vector(out) <= 1e-12 {
        out.fill(0.0);
        out[component % HEAD_SIZE] = 1.0;
    }
}

#[cfg(test)]
fn multiply_matrix_vector(matrix: &[f32], vector: &[f32], out: &mut [f32]) {
    for (row, out) in out.iter_mut().enumerate().take(HEAD_SIZE) {
        let row_start = row * HEAD_SIZE;
        *out = matrix[row_start..row_start + HEAD_SIZE]
            .iter()
            .zip(vector)
            .map(|(left, right)| left * right)
            .sum();
    }
}

#[cfg(test)]
fn multiply_transpose_matrix_vector(matrix: &[f32], vector: &[f32], out: &mut [f32]) {
    out.fill(0.0);
    for (row, &value) in vector.iter().enumerate().take(HEAD_SIZE) {
        let row_start = row * HEAD_SIZE;
        for (column, out) in out.iter_mut().enumerate().take(HEAD_SIZE) {
            *out += matrix[row_start + column] * value;
        }
    }
}

#[cfg(test)]
fn normalize_vector(values: &mut [f32]) -> f32 {
    let norm = values.iter().map(|value| value * value).sum::<f32>().sqrt();
    if norm > 0.0 && norm.is_finite() {
        for value in values {
            *value /= norm;
        }
    }
    norm
}

#[cfg(test)]
fn quantize_dequantize_symmetric(values: &mut [f32], bits: u8) {
    let qmax = (1_i32 << (bits.saturating_sub(1) as u32)) - 1;
    if qmax <= 0 {
        values.fill(0.0);
        return;
    }
    let amax = values.iter().copied().map(f32::abs).fold(0.0, f32::max);
    if amax == 0.0 || !amax.is_finite() {
        values.fill(0.0);
        return;
    }
    let scale = amax / qmax as f32;
    for value in values {
        let quantized = (*value / scale).round().clamp(-(qmax as f32), qmax as f32);
        *value = quantized * scale;
    }
}

#[cfg(test)]
mod tests {
    use std::env;
    use std::path::PathBuf;
    use std::time::Instant;

    use rusqlite::Connection;

    use super::collection_benchmark::deck_config_ids_by_deck;
    use super::*;

    struct TimeMixerAffineTransform {
        matrix: Vec<f32>,
        bias: Vec<f32>,
    }

    struct TimeMixerStateAffineTransform {
        matrix_by_head: Vec<f32>,
        bias: Vec<f32>,
    }

    fn deterministic_values(len: usize, seed: f32) -> Vec<f32> {
        (0..len)
            .map(|index| {
                let value = index as f32 + 1.0;
                (value * seed).sin() * 0.25 + (value * seed * 0.37).cos() * 0.1
            })
            .collect()
    }

    fn deterministic_step_vectors(step: usize) -> RwkvScanCapturedStep {
        let offset = step as f32 * 0.001;
        RwkvScanCapturedStep {
            r: deterministic_values(D_MODEL, 0.013 + offset),
            k: deterministic_values(D_MODEL, 0.017 + offset),
            v: deterministic_values(D_MODEL, 0.019 + offset),
            w: deterministic_values(D_MODEL, 0.023 + offset),
            a: deterministic_values(D_MODEL, 0.029 + offset),
            k_deformed: deterministic_values(D_MODEL, 0.031 + offset),
        }
    }

    fn time_mixer_transform_for_head_row(
        head: usize,
        row: usize,
        k: &[f32],
        v: &[f32],
        w: &[f32],
        a: &[f32],
        k_deformed: &[f32],
    ) -> TimeMixerAffineTransform {
        let head_base = head * HEAD_SIZE;
        let mut matrix = vec![0.0; HEAD_SIZE * HEAD_SIZE];
        let mut bias = vec![0.0; HEAD_SIZE];

        for input_column in 0..HEAD_SIZE {
            for output_column in 0..HEAD_SIZE {
                let output_channel = head_base + output_column;
                matrix[input_column * HEAD_SIZE + output_column] =
                    if input_column == output_column {
                        w[output_channel]
                    } else {
                        0.0
                    } - k_deformed[head_base + input_column]
                        * a[output_channel]
                        * k_deformed[output_channel];
            }
        }

        for output_column in 0..HEAD_SIZE {
            bias[output_column] = v[head_base + row] * k[head_base + output_column];
        }

        TimeMixerAffineTransform { matrix, bias }
    }

    fn time_mixer_state_transform(
        k: &[f32],
        v: &[f32],
        w: &[f32],
        a: &[f32],
        k_deformed: &[f32],
    ) -> TimeMixerStateAffineTransform {
        let mut matrix_by_head = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
        let mut bias = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];

        for head in 0..HEADS {
            let head_base = head * HEAD_SIZE;
            let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
            for input_column in 0..HEAD_SIZE {
                for output_column in 0..HEAD_SIZE {
                    let output_channel = head_base + output_column;
                    matrix_by_head[matrix_base + input_column * HEAD_SIZE + output_column] =
                        if input_column == output_column {
                            w[output_channel]
                        } else {
                            0.0
                        } - k_deformed[head_base + input_column]
                            * a[output_channel]
                            * k_deformed[output_channel];
                }
            }

            for row in 0..HEAD_SIZE {
                let row_start = matrix_base + row * HEAD_SIZE;
                for output_column in 0..HEAD_SIZE {
                    bias[row_start + output_column] =
                        v[head_base + row] * k[head_base + output_column];
                }
            }
        }

        TimeMixerStateAffineTransform {
            matrix_by_head,
            bias,
        }
    }

    fn compose_affine_transforms(
        first: &TimeMixerAffineTransform,
        second: &TimeMixerAffineTransform,
    ) -> TimeMixerAffineTransform {
        let mut matrix = vec![0.0; HEAD_SIZE * HEAD_SIZE];
        let mut bias = vec![0.0; HEAD_SIZE];

        for input_column in 0..HEAD_SIZE {
            for output_column in 0..HEAD_SIZE {
                matrix[input_column * HEAD_SIZE + output_column] = (0..HEAD_SIZE)
                    .map(|middle| {
                        first.matrix[input_column * HEAD_SIZE + middle]
                            * second.matrix[middle * HEAD_SIZE + output_column]
                    })
                    .sum();
            }
        }

        for (output_column, value) in bias.iter_mut().enumerate() {
            *value = second.bias[output_column]
                + (0..HEAD_SIZE)
                    .map(|middle| {
                        first.bias[middle] * second.matrix[middle * HEAD_SIZE + output_column]
                    })
                    .sum::<f32>();
        }

        TimeMixerAffineTransform { matrix, bias }
    }

    fn compose_time_mixer_state_transforms(
        first: &TimeMixerStateAffineTransform,
        second: &TimeMixerStateAffineTransform,
    ) -> TimeMixerStateAffineTransform {
        let mut matrix_by_head = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
        let mut bias = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];

        for head in 0..HEADS {
            let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
            for input_column in 0..HEAD_SIZE {
                for output_column in 0..HEAD_SIZE {
                    matrix_by_head[matrix_base + input_column * HEAD_SIZE + output_column] = (0
                        ..HEAD_SIZE)
                        .map(|middle| {
                            first.matrix_by_head[matrix_base + input_column * HEAD_SIZE + middle]
                                * second.matrix_by_head
                                    [matrix_base + middle * HEAD_SIZE + output_column]
                        })
                        .sum();
                }
            }

            for row in 0..HEAD_SIZE {
                let row_start = matrix_base + row * HEAD_SIZE;
                for output_column in 0..HEAD_SIZE {
                    bias[row_start + output_column] = second.bias[row_start + output_column]
                        + (0..HEAD_SIZE)
                            .map(|middle| {
                                first.bias[row_start + middle]
                                    * second.matrix_by_head
                                        [matrix_base + middle * HEAD_SIZE + output_column]
                            })
                            .sum::<f32>();
                }
            }
        }

        TimeMixerStateAffineTransform {
            matrix_by_head,
            bias,
        }
    }

    fn compose_time_mixer_step_range(
        steps: &[RwkvScanCapturedStep],
        start: usize,
        end: usize,
    ) -> TimeMixerStateAffineTransform {
        assert!(start < end);
        let first = &steps[start];
        let mut transform =
            time_mixer_state_transform(&first.k, &first.v, &first.w, &first.a, &first.k_deformed);
        for step in &steps[start + 1..end] {
            let next =
                time_mixer_state_transform(&step.k, &step.v, &step.w, &step.a, &step.k_deformed);
            transform = compose_time_mixer_state_transforms(&transform, &next);
        }

        transform
    }

    fn chunked_time_mixer_state_transform(
        steps: &[RwkvScanCapturedStep],
        chunk_size: usize,
    ) -> TimeMixerStateAffineTransform {
        assert!(!steps.is_empty());
        assert!(chunk_size > 0);
        let ranges = (0..steps.len())
            .step_by(chunk_size)
            .map(|start| {
                let end = (start + chunk_size).min(steps.len());
                (start, end)
            })
            .collect::<Vec<_>>();
        let chunk_transforms = std::thread::scope(|scope| {
            let handles = ranges
                .iter()
                .map(|&(start, end)| {
                    scope.spawn(move || compose_time_mixer_step_range(steps, start, end))
                })
                .collect::<Vec<_>>();

            handles
                .into_iter()
                .map(|handle| handle.join().unwrap())
                .collect::<Vec<_>>()
        });

        let mut transforms = chunk_transforms.into_iter();
        let mut transform = transforms.next().unwrap();
        for next in transforms {
            transform = compose_time_mixer_state_transforms(&transform, &next);
        }

        transform
    }

    fn apply_affine_transform(row_state: &[f32], transform: &TimeMixerAffineTransform) -> Vec<f32> {
        (0..HEAD_SIZE)
            .map(|output_column| {
                transform.bias[output_column]
                    + (0..HEAD_SIZE)
                        .map(|input_column| {
                            row_state[input_column]
                                * transform.matrix[input_column * HEAD_SIZE + output_column]
                        })
                        .sum::<f32>()
            })
            .collect()
    }

    fn apply_time_mixer_state_transform(
        state: &[f32],
        transform: &TimeMixerStateAffineTransform,
    ) -> Vec<f32> {
        let mut output = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];

        for head in 0..HEADS {
            let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
            for row in 0..HEAD_SIZE {
                let row_start = matrix_base + row * HEAD_SIZE;
                for output_column in 0..HEAD_SIZE {
                    output[row_start + output_column] = transform.bias[row_start + output_column]
                        + (0..HEAD_SIZE)
                            .map(|input_column| {
                                state[row_start + input_column]
                                    * transform.matrix_by_head
                                        [matrix_base + input_column * HEAD_SIZE + output_column]
                            })
                            .sum::<f32>();
                }
            }
        }

        output
    }

    fn assert_close(left: &[f32], right: &[f32], tolerance: f32) {
        assert_eq!(left.len(), right.len());
        for (index, (left, right)) in left.iter().zip(right).enumerate() {
            let diff = (left - right).abs();
            assert!(
                diff <= tolerance,
                "values differ at {index}: left={left}, right={right}, diff={diff}, tolerance={tolerance}"
            );
        }
    }

    #[cfg(any(target_arch = "aarch64", target_arch = "x86_64"))]
    fn deterministic_simd_values(len: usize, period: usize, scale: f32) -> Vec<f32> {
        (0..len)
            .map(|index| {
                let centered = index as i32 % period as i32 - period as i32 / 2;
                centered as f32 * scale
            })
            .collect()
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn add_scaled_in_place_avx2_fma_matches_scalar() {
        if !x86_avx2_fma_available() {
            eprintln!("skipping: AVX2/FMA not available");
            return;
        }

        let mut scalar = deterministic_simd_values(101, 17, 0.03125);
        let mut simd = scalar.clone();
        let weights = deterministic_simd_values(101, 23, -0.015625);

        add_scaled_in_place_scalar(&mut scalar, &weights, 0.75);
        // SAFETY: the runtime feature check above confirms AVX2/FMA support.
        unsafe { add_scaled_in_place_avx2_fma(&mut simd, &weights, 0.75) };

        assert_close(&simd, &scalar, 1e-6);
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn dot_product_avx2_fma_matches_scalar() {
        if !x86_avx2_fma_available() {
            eprintln!("skipping: AVX2/FMA not available");
            return;
        }

        let left = deterministic_simd_values(101, 19, 0.046875);
        let right = deterministic_simd_values(101, 29, -0.0234375);

        let scalar = dot_product_scalar(&left, &right);
        // SAFETY: the runtime feature check above confirms AVX2/FMA support.
        let simd = unsafe { dot_product_avx2_fma(&left, &right) };

        assert!(
            (simd - scalar).abs() <= 1e-5,
            "simd={simd}, scalar={scalar}, diff={}",
            (simd - scalar).abs()
        );
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn update_recurrence_head_avx2_matches_scalar_bit_exactly() {
        if !x86_avx2_fma_available() {
            eprintln!("skipping: AVX2/FMA not available");
            return;
        }

        let k = deterministic_simd_values(HEAD_SIZE, 11, 0.0078125);
        let v = deterministic_simd_values(HEAD_SIZE, 13, -0.015625);
        let w = deterministic_simd_values(HEAD_SIZE, 17, 0.9921875);
        let a = deterministic_simd_values(HEAD_SIZE, 19, -0.00390625);
        let key_deformed = deterministic_simd_values(HEAD_SIZE, 23, 0.01171875);
        let state = deterministic_simd_values(HEAD_SIZE * HEAD_SIZE, 29, -0.001953125);
        let state_dot_k_values = deterministic_simd_values(HEAD_SIZE, 31, 0.005859375);
        let state_dot_k: [f32; HEAD_SIZE] = state_dot_k_values.try_into().unwrap();
        let mut expected = vec![0.0; HEAD_SIZE * HEAD_SIZE];
        let mut actual = vec![0.0; HEAD_SIZE * HEAD_SIZE];

        update_recurrence_head_scalar(
            &k,
            &v,
            &w,
            &a,
            &key_deformed,
            &state,
            &state_dot_k,
            &mut expected,
        );
        // SAFETY: the runtime feature check above confirms AVX2 support.
        unsafe {
            update_recurrence_head_avx2(
                &k,
                &v,
                &w,
                &a,
                &key_deformed,
                &state,
                &state_dot_k,
                &mut actual,
            );
        }

        assert_eq!(actual, expected);
    }

    #[cfg(any(target_arch = "aarch64", target_arch = "x86_64"))]
    #[test]
    fn linear_apply_block_arch_matches_per_row_path_bit_exactly() {
        #[cfg(target_arch = "x86_64")]
        if !x86_avx2_fma_available() {
            eprintln!("skipping: AVX2/FMA not available");
            return;
        }

        let input = 13;
        let output = 19;
        let rows = 7;
        let linear = Linear {
            input,
            output,
            weight_by_input: deterministic_simd_values(input * output, 29, -0.0078125),
            bias: Some(deterministic_simd_values(output, 11, 0.015625)),
            packed_weight: None,
        };
        let mut inputs = deterministic_simd_values(rows * input, 17, 0.03125);
        for index in (0..inputs.len()).step_by(9) {
            inputs[index] = 0.0;
        }

        let mut expected = vec![0.0; rows * output];
        for (input_row, output_row) in inputs
            .chunks_exact(input)
            .zip(expected.chunks_exact_mut(output))
        {
            linear.apply_into(input_row, output_row);
        }
        let mut actual = vec![0.0; rows * output];
        linear.apply_block_into(&inputs, &mut actual, rows);

        assert_eq!(actual, expected);
    }

    #[test]
    fn linear_apply_into_matches_column_loop_bit_exactly() {
        // Output widths cover the 64-, 16- and 4-lane tiles plus a scalar tail.
        for (input, output) in [(128, 128), (92, 512), (16, 128), (128, 4), (13, 87), (7, 3)] {
            let linear = Linear {
                input,
                output,
                weight_by_input: deterministic_simd_values(input * output, 29, -0.0078125),
                bias: Some(deterministic_simd_values(output, 11, 0.015625)),
                packed_weight: None,
            };
            let mut values = deterministic_simd_values(input, 17, 0.03125);
            for index in (0..values.len()).step_by(5) {
                values[index] = 0.0;
            }

            let mut expected = linear.bias.clone().unwrap();
            for (column, scale) in values.iter().copied().enumerate() {
                if scale != 0.0 {
                    add_scaled_in_place(
                        &mut expected,
                        &linear.weight_by_input[column * output..(column + 1) * output],
                        scale,
                    );
                }
            }
            let actual = linear.apply(&values);

            let bits = |values: &[f32]| {
                values
                    .iter()
                    .map(|value| value.to_bits())
                    .collect::<Vec<_>>()
            };
            assert_eq!(bits(&actual), bits(&expected), "{input}x{output}");
        }
    }

    fn sequential_time_mixer_state(steps: &[RwkvScanCapturedStep]) -> Vec<f32> {
        let mut state = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
        for step in steps {
            let (_, next_state) = single_timestep(
                &step.r,
                &step.k,
                &step.v,
                &step.w,
                &step.a,
                &step.k_deformed,
                &state,
            );
            state = next_state;
        }

        state
    }

    fn scan_reduce_time_mixer_state(
        steps: &[RwkvScanCapturedStep],
        bulk_size: usize,
        leaf_size: usize,
    ) -> Vec<f32> {
        assert!(bulk_size > 0);
        assert!(leaf_size > 0);
        let mut state = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];

        for bulk in steps.chunks(bulk_size) {
            let leaf_transforms = bulk
                .par_chunks(leaf_size)
                .map(|chunk| compose_time_mixer_step_range(chunk, 0, chunk.len()))
                .collect::<Vec<_>>();
            let mut leaf_transforms = leaf_transforms.into_iter();
            let Some(mut bulk_transform) = leaf_transforms.next() else {
                continue;
            };
            for next in leaf_transforms {
                bulk_transform = compose_time_mixer_state_transforms(&bulk_transform, &next);
            }
            state = apply_time_mixer_state_transform(&state, &bulk_transform);
        }

        state
    }

    fn max_abs_diff(left: &[f32], right: &[f32]) -> f32 {
        left.iter()
            .zip(right)
            .map(|(left, right)| (left - right).abs())
            .fold(0.0, f32::max)
    }

    fn parse_scan_bench_sizes(value: &str) -> Vec<usize> {
        value
            .split(',')
            .filter_map(|value| value.trim().parse::<usize>().ok())
            .filter(|value| *value > 0)
            .collect()
    }

    fn auto_scan_bench_sizes(
        step_count: usize,
        memory_budget_bytes: u64,
        bytes_per_review_all_layers: usize,
    ) -> Vec<usize> {
        let max_by_memory = (memory_budget_bytes / bytes_per_review_all_layers as u64)
            .max(1)
            .try_into()
            .unwrap_or(usize::MAX);
        let max_size = step_count.min(max_by_memory).max(1);
        let mut sizes = Vec::new();
        let mut size = 64_usize;
        while size < max_size {
            sizes.push(size);
            size = size.saturating_mul(2);
        }
        sizes.push(max_size);
        sizes.sort_unstable();
        sizes.dedup();
        sizes
    }

    fn scan_bench_env_usize(name: &str, default: usize) -> usize {
        std::env::var(name)
            .ok()
            .and_then(|value| value.parse().ok())
            .unwrap_or(default)
    }

    fn scan_bench_env_u64(name: &str, default: u64) -> u64 {
        std::env::var(name)
            .ok()
            .and_then(|value| value.parse().ok())
            .unwrap_or(default)
    }

    #[derive(Clone, Copy)]
    struct ScanBenchTiming {
        days_elapsed: i64,
        next_day_at: i64,
    }

    fn scan_bench_timing() -> ScanBenchTiming {
        let now_secs = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        let days_elapsed = now_secs / SECONDS_PER_DAY;
        ScanBenchTiming {
            days_elapsed,
            next_day_at: (days_elapsed + 1) * SECONDS_PER_DAY,
        }
    }

    struct ScanBenchHistoricalReviewRow {
        review_id: i64,
        card_id: i64,
        note_id: i64,
        deck_id: i64,
        ease: i64,
        duration_millis: i64,
        review_kind: i64,
        is_learning_start: bool,
    }

    fn collection_reviews_for_scan_bench(
        path: &std::path::Path,
        limit: usize,
        deck_id: Option<i64>,
    ) -> rusqlite::Result<Vec<ReviewInput>> {
        let uri = format!("file:{}?mode=ro&immutable=1", path.to_string_lossy());
        let db = rusqlite::Connection::open_with_flags(
            uri,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_URI,
        )?;
        let preset_by_deck = deck_config_ids_by_deck(&db)?;
        let current_deck_id_sql = scan_bench_current_deck_id_sql(&db)?;
        let limit_clause = if limit == 0 {
            String::new()
        } else {
            format!(" limit {limit}")
        };
        let deck_clause = deck_id.map_or(String::new(), |deck_id| {
            format!(" and {current_deck_id_sql} = {deck_id}")
        });
        let sql = format!(
            "
with eligible as (
  select
    r.id,
    r.cid,
    c.nid,
    {current_deck_id_sql} as deck_id,
    r.ease,
    r.time,
    r.type,
    lag(r.type) over (partition by r.cid order by r.id) as previous_type
  from revlog r
  join cards c on c.id = r.cid
  where r.ease between 1 and 4
    and r.type in (0, 1, 2, 3, 4, 5)
    and not (r.type = 3 and r.factor = 0)
    {deck_clause}
), retained_starts as (
  select cid, max(id) as start_id
  from eligible
  where type = 0 and (previous_type is null or previous_type != 0)
  group by cid
)
select
  e.id,
  e.cid,
  e.nid,
  e.deck_id,
  e.ease,
  e.time,
  e.type,
  e.id = s.start_id
from eligible e
join retained_starts s on s.cid = e.cid
where e.id >= s.start_id
order by e.id, e.cid
{limit_clause}"
        );
        let timing = scan_bench_timing();
        let mut stmt = db.prepare(&sql)?;
        let rows = stmt.query_map([], |row| {
            Ok(ScanBenchHistoricalReviewRow {
                review_id: row.get(0)?,
                card_id: row.get(1)?,
                note_id: row.get(2)?,
                deck_id: row.get(3)?,
                ease: row.get(4)?,
                duration_millis: row.get(5)?,
                review_kind: row.get(6)?,
                is_learning_start: row.get(7)?,
            })
        })?;

        let mut previous_review_id_by_card = HashMap::new();
        let mut reviews = Vec::new();
        for row in rows {
            let row = row?;
            let previous_review_id = previous_review_id_by_card.insert(row.card_id, row.review_id);
            let day_offset = scan_bench_historical_day_offset(row.review_id, &timing);
            let elapsed_seconds = previous_review_id
                .map_or(-1, |previous| ((row.review_id - previous) / 1000).max(0));
            let elapsed_days = previous_review_id.map_or(-1, |previous| {
                (day_offset - scan_bench_historical_day_offset(previous, &timing)).max(0)
            });
            reviews.push(ReviewInput {
                card_id: row.card_id,
                note_id: Some(row.note_id),
                deck_id: Some(row.deck_id),
                preset_id: preset_by_deck.get(&row.deck_id).copied().flatten(),
                is_query: false,
                ease: Some(row.ease as u8),
                duration_millis: Some(row.duration_millis),
                card_type: Some(scan_bench_historical_state(
                    row.review_kind,
                    row.is_learning_start,
                )),
                day_offset: Some(day_offset),
                current_elapsed_days: Some(elapsed_days),
                current_elapsed_seconds: Some(elapsed_seconds),
                target_retentions: [Some(0.9), Some(0.9), Some(0.9), Some(0.9)],
                enforce_grade_order: true,
            });
        }

        Ok(reviews)
    }

    fn scan_bench_current_deck_id_sql(db: &rusqlite::Connection) -> rusqlite::Result<&'static str> {
        Ok(if scan_bench_table_has_column(db, "cards", "odid")? {
            "case when c.odid != 0 then c.odid else c.did end"
        } else {
            "c.did"
        })
    }

    fn scan_bench_table_has_column(
        db: &rusqlite::Connection,
        table: &str,
        column: &str,
    ) -> rusqlite::Result<bool> {
        let mut stmt = db.prepare(&format!("pragma table_info({table})"))?;
        let columns = stmt.query_map([], |row| row.get::<_, String>(1))?;
        for name in columns {
            if name? == column {
                return Ok(true);
            }
        }
        Ok(false)
    }

    fn scan_bench_historical_state(review_kind: i64, is_learning_start: bool) -> i64 {
        if is_learning_start {
            0
        } else {
            review_kind + 1
        }
    }

    fn scan_bench_historical_day_offset(review_id: i64, timing: &ScanBenchTiming) -> i64 {
        let review_secs = review_id / 1000;
        let days_before_today = (timing.next_day_at - 1 - review_secs).max(0) / SECONDS_PER_DAY;
        (timing.days_elapsed - days_before_today).max(0)
    }

    fn scan_bench_global_layer_matrix(
        inference: &RwkvInference,
        layer_id: usize,
    ) -> Option<&[f32]> {
        inference
            .warm_up_states
            .global
            .as_ref()?
            .layers
            .get(layer_id)?
            .time
            .as_ref()
            .map(|state| state.matrix.as_slice())
    }

    fn write_scan_bench_capture(
        path: &std::path::Path,
        steps: &[RwkvScanCapturedStep],
        final_state: &[f32],
    ) -> std::io::Result<()> {
        let mut file = std::io::BufWriter::new(std::fs::File::create(path)?);
        std::io::Write::write_all(&mut file, b"ARWKVSCAN1")?;
        std::io::Write::write_all(&mut file, &(D_MODEL as u32).to_le_bytes())?;
        std::io::Write::write_all(&mut file, &(HEADS as u32).to_le_bytes())?;
        std::io::Write::write_all(&mut file, &(HEAD_SIZE as u32).to_le_bytes())?;
        std::io::Write::write_all(&mut file, &(steps.len() as u64).to_le_bytes())?;
        for step in steps {
            for values in [
                &step.r,
                &step.k,
                &step.v,
                &step.w,
                &step.a,
                &step.k_deformed,
            ] {
                for value in values {
                    std::io::Write::write_all(&mut file, &value.to_le_bytes())?;
                }
            }
        }
        std::io::Write::write_all(&mut file, &(final_state.len() as u32).to_le_bytes())?;
        for value in final_state {
            std::io::Write::write_all(&mut file, &value.to_le_bytes())?;
        }
        Ok(())
    }

    #[test]
    #[ignore]
    fn rwkv_bulk_collection_benchmark() {
        let Ok(collection_path) = std::env::var("ANKI_RWKV_BULK_BENCH_COLLECTION")
            .or_else(|_| std::env::var("ANKI_RWKV_SCAN_BENCH_COLLECTION"))
        else {
            eprintln!(
                "set ANKI_RWKV_BULK_BENCH_COLLECTION to a copied collection.anki2 path to run this benchmark"
            );
            return;
        };
        let weights_path = std::env::var("ANKI_RWKV_BULK_BENCH_MODEL")
            .or_else(|_| std::env::var("ANKI_RWKV_SCAN_BENCH_MODEL"))
            .unwrap_or_else(|_| {
                PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                    .join(MODEL_PATH)
                    .display()
                    .to_string()
            });
        let review_limit = scan_bench_env_usize("ANKI_RWKV_BULK_BENCH_LIMIT", 0);
        let deck_id = std::env::var("ANKI_RWKV_BULK_BENCH_DECK_ID")
            .or_else(|_| std::env::var("ANKI_RWKV_SCAN_BENCH_DECK_ID"))
            .ok()
            .and_then(|value| value.parse().ok());
        let chunk_rows = std::env::var("ANKI_RWKV_BULK_BENCH_CHUNK_ROWS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok());
        let call_rows = std::env::var("ANKI_RWKV_BULK_BENCH_CALL_ROWS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .filter(|&value| value > 0);
        let record_predictions = std::env::var("ANKI_RWKV_BULK_BENCH_RECORD_PREDICTIONS")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let force_fast_query = std::env::var("ANKI_RWKV_BULK_BENCH_FAST_QUERY")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let profile = std::env::var("ANKI_RWKV_BULK_BENCH_PROFILE").map_or(true, |value| {
            !matches!(value.as_str(), "0" | "false" | "FALSE" | "no" | "NO")
        });

        let collection_path = std::path::PathBuf::from(collection_path);
        let weights_path = std::path::PathBuf::from(weights_path);
        let load_reviews_started = std::time::Instant::now();
        let reviews = collection_reviews_for_scan_bench(&collection_path, review_limit, deck_id)
            .expect("collection review load failed");
        let load_reviews_ms = load_reviews_started.elapsed().as_secs_f64() * 1000.0;
        assert!(
            !reviews.is_empty(),
            "collection review load returned no rows"
        );

        let mut inference =
            RwkvInference::load(weights_path.clone(), 0.9, 36_500).expect("RWKV load failed");
        if profile {
            start_rwkv_single_timestep_profile();
            start_rwkv_warmup_profile();
        }
        let warmup_started = std::time::Instant::now();
        let predictions = if let Some(call_rows) = call_rows {
            let mut predictions = Vec::new();
            for (call_index, call) in reviews.chunks(call_rows).enumerate() {
                let offset = call_index * call_rows;
                let call_predictions = if force_fast_query {
                    bulk::warm_up_reviews_bulk_fast_query_chunked(
                        &mut inference,
                        call.to_vec(),
                        record_predictions,
                        chunk_rows.unwrap_or(16_384),
                    )
                } else if let Some(chunk_rows) = chunk_rows {
                    bulk::warm_up_reviews_bulk_chunked(
                        &mut inference,
                        call.to_vec(),
                        record_predictions,
                        chunk_rows,
                    )
                } else {
                    inference.warm_up_reviews(call.to_vec(), record_predictions)
                }
                .expect("RWKV warm-up call failed");
                predictions.extend(
                    call_predictions
                        .into_iter()
                        .map(|(index, value)| (index + offset, value)),
                );
            }
            Ok::<_, std::io::Error>(predictions)
        } else if force_fast_query {
            bulk::warm_up_reviews_bulk_fast_query_chunked(
                &mut inference,
                reviews.clone(),
                record_predictions,
                chunk_rows.unwrap_or(16_384),
            )
        } else if let Some(chunk_rows) = chunk_rows {
            bulk::warm_up_reviews_bulk_chunked(
                &mut inference,
                reviews.clone(),
                record_predictions,
                chunk_rows,
            )
        } else {
            inference.warm_up_reviews(reviews.clone(), record_predictions)
        }
        .expect("RWKV warm-up failed");
        let warmup_ms = warmup_started.elapsed().as_secs_f64() * 1000.0;
        let warmup_profile = profile.then(stop_rwkv_warmup_profile);
        let single_timestep_profile = profile.then(stop_rwkv_single_timestep_profile);

        println!("collection={}", collection_path.display());
        println!("weights={}", weights_path.display());
        println!("collection_reviews={}", reviews.len());
        println!("record_predictions={record_predictions}");
        println!(
            "query_path={}",
            if force_fast_query {
                "forced-fast-test-hook"
            } else {
                "production-default"
            }
        );
        println!("profile={profile}");
        if let Some(chunk_rows) = chunk_rows {
            println!("chunk_rows={chunk_rows}");
        }
        if let Some(call_rows) = call_rows {
            println!("call_rows={call_rows}");
        }
        println!("predictions={}", predictions.len());
        if record_predictions {
            let prediction_values = predictions
                .iter()
                .map(|(_, prediction)| *prediction)
                .collect::<Vec<_>>();
            let outcomes = predictions
                .iter()
                .map(|(index, _)| reviews[*index].ease != Some(1))
                .collect::<Vec<_>>();
            let metrics = MetricAccumulator::from_predictions(&prediction_values, &outcomes);
            println!("log_loss={:.15}", metrics.log_loss);
        }
        println!("load_reviews_ms={load_reviews_ms:.3}");
        println!("warmup_ms={warmup_ms:.3}");
        if let Ok(threads) = std::env::var("RAYON_NUM_THREADS") {
            println!("rayon_num_threads={threads}");
        }
        if let Some(warmup_profile) = warmup_profile {
            for (name, calls, nanos) in warmup_profile {
                let ms = nanos as f64 / 1_000_000.0;
                println!("warmup_profile_{name}_calls={calls}");
                println!("warmup_profile_{name}_ms={ms:.3}");
                println!(
                    "warmup_profile_{name}_fraction={:.6}",
                    ms / warmup_ms.max(f64::MIN_POSITIVE)
                );
            }
        }
        if let Some((calls, nanos)) = single_timestep_profile {
            println!("single_timestep_calls={calls}");
            println!("single_timestep_ms={:.3}", nanos as f64 / 1_000_000.0);
            println!(
                "single_timestep_warmup_fraction={:.6}",
                (nanos as f64 / 1_000_000.0) / warmup_ms.max(f64::MIN_POSITIVE)
            );
        }
    }

    #[test]
    #[ignore]
    fn rwkv_workload_forecast_collection_benchmark() {
        let Ok(collection_path) = std::env::var("ANKI_RWKV_FORECAST_BENCH_COLLECTION") else {
            eprintln!(
                "set ANKI_RWKV_FORECAST_BENCH_COLLECTION to a copied collection.anki2 path to run this benchmark"
            );
            return;
        };
        let weights_path = std::env::var("ANKI_RWKV_FORECAST_BENCH_MODEL").unwrap_or_else(|_| {
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join(MODEL_PATH)
                .display()
                .to_string()
        });
        let days = scan_bench_env_usize("ANKI_RWKV_FORECAST_BENCH_DAYS", 30) as u32;
        let paths = scan_bench_env_usize("ANKI_RWKV_FORECAST_BENCH_PATHS", 200) as u32;
        let review_limit = scan_bench_env_usize("ANKI_RWKV_FORECAST_BENCH_REVIEW_LIMIT", 9_999);
        let state_update_interval =
            scan_bench_env_usize("ANKI_RWKV_FORECAST_BENCH_STATE_UPDATE_INTERVAL", 1) as u32;
        let single_step_answers =
            scan_bench_env_usize("ANKI_RWKV_FORECAST_BENCH_SINGLE_STEP", 0) == 1;

        let reviews =
            collection_reviews_for_scan_bench(std::path::Path::new(&collection_path), 0, None)
                .expect("collection review load failed");
        let mut inference =
            RwkvInference::load(std::path::PathBuf::from(weights_path), 0.9, 36_500)
                .expect("RWKV load failed");
        let warmup_started = std::time::Instant::now();
        inference
            .warm_up_reviews(reviews.clone(), false)
            .expect("RWKV warm-up failed");
        let warmup_ms = warmup_started.elapsed().as_secs_f64() * 1000.0;
        let today = reviews
            .iter()
            .filter_map(|review| review.day_offset)
            .max()
            .unwrap_or(0);
        let mut latest_by_card: HashMap<i64, (&ReviewInput, i64)> = HashMap::new();
        for review in &reviews {
            let entry = latest_by_card.entry(review.card_id).or_insert((review, 0));
            entry.0 = review;
            entry.1 += 1;
        }
        let mut card_ids = latest_by_card.keys().copied().collect::<Vec<_>>();
        card_ids.sort_unstable();
        // Every reviewed card, due where the curve says (the collection copy
        // carries no due dates in its review inputs).
        let cards = card_ids
            .iter()
            .map(|card_id| {
                let (review, reps) = latest_by_card[card_id];
                let elapsed_days = (today - review.day_offset.unwrap_or(today)).max(0);
                RwkvForecastCard {
                    input: RwkvWorkloadSimulationInput {
                        review_input: ReviewInput {
                            is_query: true,
                            ease: None,
                            duration_millis: None,
                            card_type: Some(CardType::Review as i64),
                            day_offset: Some(today),
                            current_elapsed_days: Some(elapsed_days),
                            current_elapsed_seconds: Some(elapsed_days * 86_400),
                            target_retentions: [None; 4],
                            ..review.clone()
                        },
                        interval_days: Some(elapsed_days.max(1)),
                        ease_factor: Some(2500),
                        reps: Some(reps),
                        lapses: Some(0),
                    },
                    due_day: None,
                    group: review.deck_id.unwrap_or(0),
                    desired_retention: 0.9,
                    scheduler: RwkvForecastScheduler::Rwkv,
                    fsrs_memory: None,
                    fsrs_parameters: vec![],
                    historical_retention: 0.9,
                }
            })
            .collect::<Vec<_>>();
        println!("collection_reviews={}", reviews.len());
        println!("warmup_ms={warmup_ms:.1}");
        println!("forecast_cards={}", cards.len());
        let started = std::time::Instant::now();
        let output = inference
            .forecast_workload_from_warm_up(
                cards,
                RwkvWorkloadForecastConfig {
                    days,
                    paths,
                    seed: 0,
                    groups: vec![],
                    default_review_limit: review_limit as u32,
                    default_new_limit: 0,
                    new_cards_ignore_review_limit: false,
                    max_interval: 36_500,
                    review_order: ReviewCardOrder::Day,
                    suspend_after_lapses: None,
                    state_update_interval,
                    single_step_answers,
                    review_model: RwkvWorkloadReviewModel {
                        grade_seconds: [12.0, 9.0, 7.0, 5.0],
                        bucket_probabilities: vec![],
                    },
                },
                &mut |_, _| Ok(()),
            )
            .expect("forecast failed");
        let elapsed_ms = started.elapsed().as_secs_f64() * 1000.0;
        let total_done = output.days.iter().map(|day| day.done.mean).sum::<f32>();
        println!(
            "days={days} paths={paths} state_update_interval={state_update_interval} single_step={single_step_answers} forecast_ms={elapsed_ms:.1} mean_reviews={total_done:.1} reviews_per_s={:.0}",
            total_done as f64 * paths as f64 / (elapsed_ms / 1000.0)
        );
        for day in output.days.iter().take(days as usize) {
            println!(
                "day={} due_mean={:.1} due_p1={:.0} due_p5={:.0} due_p50={:.0} due_p95={:.0} due_p99={:.0} done_mean={:.1} minutes_mean={:.1}",
                day.day,
                day.due.mean,
                day.due.p1,
                day.due.p5,
                day.due.p50,
                day.due.p95,
                day.due.p99,
                day.done.mean,
                day.seconds.mean / 60.0
            );
        }
    }

    #[test]
    #[ignore]
    fn rwkv_workload_simulation_collection_benchmark() {
        let Ok(collection_path) = std::env::var("ANKI_RWKV_SIM_BENCH_COLLECTION") else {
            eprintln!(
                "set ANKI_RWKV_SIM_BENCH_COLLECTION to a copied collection.anki2 path to run this benchmark"
            );
            return;
        };
        let weights_path = std::env::var("ANKI_RWKV_SIM_BENCH_MODEL").unwrap_or_else(|_| {
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join(MODEL_PATH)
                .display()
                .to_string()
        });
        let cards = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_CARDS", 250);
        let days = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_DAYS", 365) as u32;
        let min_dr = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_MIN_DR", 30) as u32;
        let max_dr = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_MAX_DR", 99) as u32;
        let dr_step = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_DR_STEP", 1) as u32;
        let state_update_interval =
            scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_STATE_UPDATE_INTERVAL", 1) as u32;
        let rounds = scan_bench_env_usize("ANKI_RWKV_SIM_BENCH_ROUNDS", 1);

        let reviews =
            collection_reviews_for_scan_bench(std::path::Path::new(&collection_path), 0, None)
                .expect("collection review load failed");
        let mut inference =
            RwkvInference::load(std::path::PathBuf::from(weights_path), 0.9, 36_500)
                .expect("RWKV load failed");
        let warmup_started = std::time::Instant::now();
        inference
            .warm_up_reviews(reviews.clone(), false)
            .expect("RWKV warm-up failed");
        let warmup_ms = warmup_started.elapsed().as_secs_f64() * 1000.0;

        // Query every sampled card today, from its latest historical review.
        let today = reviews
            .iter()
            .filter_map(|review| review.day_offset)
            .max()
            .unwrap_or(0);
        let mut latest_by_card: HashMap<i64, (&ReviewInput, i64)> = HashMap::new();
        for review in &reviews {
            let entry = latest_by_card.entry(review.card_id).or_insert((review, 0));
            entry.0 = review;
            entry.1 += 1;
        }
        let mut card_ids = latest_by_card.keys().copied().collect::<Vec<_>>();
        card_ids.sort_unstable();
        let stride = (card_ids.len() / cards.max(1)).max(1);
        let inputs = card_ids
            .iter()
            .step_by(stride)
            .take(cards)
            .map(|card_id| {
                let (review, reps) = latest_by_card[card_id];
                let elapsed_days = (today - review.day_offset.unwrap_or(today)).max(0);
                RwkvWorkloadSimulationInput {
                    review_input: ReviewInput {
                        is_query: true,
                        ease: None,
                        duration_millis: None,
                        card_type: Some(CardType::Review as i64),
                        day_offset: Some(today),
                        current_elapsed_days: Some(elapsed_days),
                        current_elapsed_seconds: Some(elapsed_days * 86_400),
                        target_retentions: [None; 4],
                        ..review.clone()
                    },
                    interval_days: Some(elapsed_days.max(1)),
                    ease_factor: Some(2500),
                    reps: Some(reps),
                    lapses: Some(0),
                }
            })
            .collect::<Vec<_>>();
        let config = || RwkvWorkloadSimulationConfig {
            min_dr,
            max_dr,
            target_dr_step: dr_step,
            days_to_simulate: days,
            review_limit: 9_999,
            new_limit: 0,
            new_cards_ignore_review_limit: false,
            max_interval: 36_500,
            review_order: ReviewCardOrder::RetrievabilityAscending,
            suspend_after_lapses: None,
            state_update_interval,
            review_model: RwkvWorkloadReviewModel {
                grade_seconds: [12.0, 9.0, 7.0, 5.0],
                bucket_probabilities: vec![],
            },
        };

        println!("collection_reviews={}", reviews.len());
        println!("warmup_ms={warmup_ms:.3}");
        println!("sim_cards={}", inputs.len());
        println!(
            "sim_state_bytes={}",
            inference
                .warm_up_states
                .state_len_for(&StreamIds::for_workload_inputs(&inputs))
        );
        for round in 0..rounds {
            let started = std::time::Instant::now();
            let output = inference
                .simulate_workload_from_warm_up(inputs.clone(), config(), &mut |_, _| Ok(()))
                .expect("simulation failed");
            let elapsed_ms = started.elapsed().as_secs_f64() * 1000.0;
            let mut hash = 0xcbf29ce484222325u64;
            let mut mix = |value: u32| {
                hash ^= u64::from(value);
                hash = hash.wrapping_mul(0x100000001b3);
            };
            mix(output.reviewless_end_memorized.to_bits());
            mix(output.reviewless_end_weighted_memorized.to_bits());
            let mut reviews_total = 0u64;
            for (dr, point) in &output.points {
                mix(*dr);
                mix(point.memorized.to_bits());
                mix(point.weighted_memorized.to_bits());
                mix(point.cost.to_bits());
                mix(point.review_count);
                reviews_total += u64::from(point.review_count);
            }
            println!(
                "round={round} sim_ms={elapsed_ms:.1} simulated_reviews={reviews_total} output_hash={hash:016x}"
            );
        }
    }

    #[test]
    #[ignore]
    fn rwkv_scan_collection_benchmark() {
        let Ok(collection_path) = std::env::var("ANKI_RWKV_SCAN_BENCH_COLLECTION") else {
            eprintln!(
                "set ANKI_RWKV_SCAN_BENCH_COLLECTION to a copied collection.anki2 path to run this benchmark"
            );
            return;
        };
        let weights_path = std::env::var("ANKI_RWKV_SCAN_BENCH_MODEL").unwrap_or_else(|_| {
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join(MODEL_PATH)
                .display()
                .to_string()
        });
        let review_limit = scan_bench_env_usize("ANKI_RWKV_SCAN_BENCH_LIMIT", 0);
        let leaf_size = scan_bench_env_usize("ANKI_RWKV_SCAN_BENCH_LEAF_SIZE", 64);
        let layer_id = scan_bench_env_usize("ANKI_RWKV_SCAN_BENCH_LAYER_ID", MODULE_LAYERS[4] - 1);
        let deck_id = std::env::var("ANKI_RWKV_SCAN_BENCH_DECK_ID")
            .ok()
            .and_then(|value| value.parse().ok());
        let memory_budget_bytes = scan_bench_env_u64(
            "ANKI_RWKV_SCAN_BENCH_MEMORY_BUDGET_BYTES",
            16 * 1024 * 1024 * 1024,
        );
        let extend_to_memory_budget = std::env::var("ANKI_RWKV_SCAN_BENCH_EXTEND_TO_MEMORY_BUDGET")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let profile_single_timestep = std::env::var("ANKI_RWKV_SCAN_BENCH_PROFILE_SINGLE_TIMESTEP")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let profile_warmup = std::env::var("ANKI_RWKV_SCAN_BENCH_PROFILE_WARMUP")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let selected_layer_step_bytes = 6 * D_MODEL * std::mem::size_of::<f32>();
        let all_layer_step_bytes = selected_layer_step_bytes * MODULE_LAYERS.iter().sum::<usize>();

        let collection_path = std::path::PathBuf::from(collection_path);
        let weights_path = std::path::PathBuf::from(weights_path);
        let load_reviews_started = std::time::Instant::now();
        let reviews = collection_reviews_for_scan_bench(&collection_path, review_limit, deck_id)
            .expect("collection review load failed");
        let load_reviews_ms = load_reviews_started.elapsed().as_secs_f64() * 1000.0;
        assert!(
            !reviews.is_empty(),
            "collection review load returned no rows"
        );
        assert!(
            layer_id < MODULE_LAYERS[4],
            "global layer id is out of range"
        );

        let mut inference =
            RwkvInference::load(weights_path.clone(), 0.9, 36_500).expect("RWKV load failed");
        start_rwkv_scan_capture(4, layer_id);
        if profile_single_timestep {
            start_rwkv_single_timestep_profile();
        }
        if profile_warmup {
            start_rwkv_warmup_profile();
        }
        let warmup_started = std::time::Instant::now();
        inference
            .warm_up_reviews(reviews.clone(), false)
            .expect("RWKV warm-up failed");
        let warmup_ms = warmup_started.elapsed().as_secs_f64() * 1000.0;
        let warmup_profile = if profile_warmup {
            Some(stop_rwkv_warmup_profile())
        } else {
            None
        };
        let single_timestep_profile = if profile_single_timestep {
            Some(stop_rwkv_single_timestep_profile())
        } else {
            None
        };
        let mut steps = take_rwkv_scan_capture();
        assert_eq!(steps.len(), reviews.len());
        let runtime_matrix = scan_bench_global_layer_matrix(&inference, layer_id)
            .expect("captured global layer state is missing")
            .to_vec();
        let captured_steps = steps.len();
        let capture_state = sequential_time_mixer_state(&steps);
        let capture_error = max_abs_diff(&capture_state, &runtime_matrix);
        if let Ok(capture_path) = std::env::var("ANKI_RWKV_SCAN_BENCH_CAPTURE_OUTPUT") {
            write_scan_bench_capture(std::path::Path::new(&capture_path), &steps, &capture_state)
                .expect("failed to write RWKV scan capture");
            println!("capture_output={capture_path}");
        }

        if extend_to_memory_budget {
            let target_steps = (memory_budget_bytes / all_layer_step_bytes as u64)
                .max(1)
                .try_into()
                .unwrap_or(usize::MAX);
            if steps.len() < target_steps {
                steps.reserve(target_steps - steps.len());
                for index in steps.len()..target_steps {
                    steps.push(steps[index % captured_steps].clone());
                }
            }
        }

        let sequential_started = std::time::Instant::now();
        let sequential_state = sequential_time_mixer_state(&steps);
        let sequential_ms = sequential_started.elapsed().as_secs_f64() * 1000.0;

        let bulk_sizes = std::env::var("ANKI_RWKV_SCAN_BENCH_BULK_SIZES")
            .ok()
            .map(|value| parse_scan_bench_sizes(&value))
            .filter(|sizes| !sizes.is_empty())
            .unwrap_or_else(|| {
                auto_scan_bench_sizes(steps.len(), memory_budget_bytes, all_layer_step_bytes)
            });

        let mut csv_rows = vec![
            "bulk_size,selected_layer_memory_mb,all_layer_memory_mb,scan_ms,speedup_vs_sequential,max_abs_error"
                .to_string(),
        ];

        println!("collection={}", collection_path.display());
        println!("weights={}", weights_path.display());
        println!("collection_reviews={captured_steps}");
        println!("benchmark_steps={}", steps.len());
        println!("extended_to_memory_budget={extend_to_memory_budget}");
        println!("captured_global_module_layer={layer_id}");
        println!("load_reviews_ms={load_reviews_ms:.3}");
        println!("warmup_ms={warmup_ms:.3}");
        if let Some(profile) = &warmup_profile {
            for (name, calls, nanos) in profile {
                let ms = *nanos as f64 / 1_000_000.0;
                println!("warmup_profile_{name}_calls={calls}");
                println!("warmup_profile_{name}_ms={ms:.3}");
                println!(
                    "warmup_profile_{name}_fraction={:.6}",
                    ms / warmup_ms.max(f64::MIN_POSITIVE)
                );
            }
        }
        if let Some((calls, nanos)) = single_timestep_profile {
            println!("single_timestep_calls={calls}");
            println!("single_timestep_ms={:.3}", nanos as f64 / 1_000_000.0);
            println!(
                "single_timestep_warmup_fraction={:.6}",
                (nanos as f64 / 1_000_000.0) / warmup_ms.max(f64::MIN_POSITIVE)
            );
        }
        println!("sequential_single_timestep_ms={sequential_ms:.3}");
        println!("capture_vs_runtime_max_abs_error={capture_error:.9}");
        println!("leaf_size={leaf_size}");
        println!("memory_budget_bytes={memory_budget_bytes}");
        println!("bulk_size,selected_layer_memory_mb,all_layer_memory_mb,scan_ms,speedup_vs_sequential,max_abs_error");

        for bulk_size in bulk_sizes {
            let started = std::time::Instant::now();
            let state = scan_reduce_time_mixer_state(&steps, bulk_size, leaf_size);
            let scan_ms = started.elapsed().as_secs_f64() * 1000.0;
            let error = max_abs_diff(&state, &sequential_state);
            let speedup = sequential_ms / scan_ms.max(f64::MIN_POSITIVE);
            let selected_layer_memory_mb =
                bulk_size as f64 * selected_layer_step_bytes as f64 / (1024.0 * 1024.0);
            let all_layer_memory_mb =
                bulk_size as f64 * all_layer_step_bytes as f64 / (1024.0 * 1024.0);
            let row = format!(
                "{bulk_size},{selected_layer_memory_mb:.3},{all_layer_memory_mb:.3},{scan_ms:.3},{speedup:.6},{error:.9}"
            );
            println!("{row}");
            csv_rows.push(row);
            std::hint::black_box(state);
        }

        if let Ok(output_path) = std::env::var("ANKI_RWKV_SCAN_BENCH_OUTPUT") {
            std::fs::write(&output_path, csv_rows.join("\n"))
                .expect("failed to write RWKV scan benchmark CSV");
            println!("output={output_path}");
        }
    }

    #[test]
    fn single_timestep_state_update_supports_affine_composition() {
        let r1 = deterministic_values(D_MODEL, 0.013);
        let k1 = deterministic_values(D_MODEL, 0.017);
        let v1 = deterministic_values(D_MODEL, 0.019);
        let w1 = deterministic_values(D_MODEL, 0.023);
        let a1 = deterministic_values(D_MODEL, 0.029);
        let k_deformed1 = deterministic_values(D_MODEL, 0.031);
        let r2 = deterministic_values(D_MODEL, 0.037);
        let k2 = deterministic_values(D_MODEL, 0.041);
        let v2 = deterministic_values(D_MODEL, 0.043);
        let w2 = deterministic_values(D_MODEL, 0.047);
        let a2 = deterministic_values(D_MODEL, 0.053);
        let k_deformed2 = deterministic_values(D_MODEL, 0.059);
        let state0 = deterministic_values(HEADS * HEAD_SIZE * HEAD_SIZE, 0.061);

        let (_, state1) = single_timestep(&r1, &k1, &v1, &w1, &a1, &k_deformed1, &state0);
        let (_, sequential_state) = single_timestep(&r2, &k2, &v2, &w2, &a2, &k_deformed2, &state1);

        let mut composed_state = vec![0.0; HEADS * HEAD_SIZE * HEAD_SIZE];
        for head in 0..HEADS {
            let matrix_base = head * HEAD_SIZE * HEAD_SIZE;
            for row in 0..HEAD_SIZE {
                let row_start = matrix_base + row * HEAD_SIZE;
                let first =
                    time_mixer_transform_for_head_row(head, row, &k1, &v1, &w1, &a1, &k_deformed1);
                let second =
                    time_mixer_transform_for_head_row(head, row, &k2, &v2, &w2, &a2, &k_deformed2);
                let composed = compose_affine_transforms(&first, &second);
                let row_state =
                    apply_affine_transform(&state0[row_start..row_start + HEAD_SIZE], &composed);
                composed_state[row_start..row_start + HEAD_SIZE].copy_from_slice(&row_state);
            }
        }

        assert_close(&composed_state, &sequential_state, 1e-5);
    }

    #[test]
    fn single_timestep_chunked_affine_reduction_matches_sequential() {
        let steps = (0..32).map(deterministic_step_vectors).collect::<Vec<_>>();
        let state0 = deterministic_values(HEADS * HEAD_SIZE * HEAD_SIZE, 0.061);

        let mut sequential_state = state0.clone();
        for step in &steps {
            let (_, next_state) = single_timestep(
                &step.r,
                &step.k,
                &step.v,
                &step.w,
                &step.a,
                &step.k_deformed,
                &sequential_state,
            );
            sequential_state = next_state;
        }

        let transform = chunked_time_mixer_state_transform(&steps, 8);
        let reduced_state = apply_time_mixer_state_transform(&state0, &transform);

        assert_close(&reduced_state, &sequential_state, 1e-4);
    }

    fn embedded_weights_path() -> Option<PathBuf> {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        path.exists().then_some(path)
    }

    fn bulk_parity_reviews(count: usize) -> Vec<ReviewInput> {
        let mut state = 0x9e3779b97f4a7c15_u64;
        let mut next = move || {
            state = state
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            (state >> 33) as i64
        };
        (0..count)
            .map(|index| {
                let card_id = 100 + next().rem_euclid(23);
                ReviewInput {
                    card_id,
                    note_id: (index % 11 != 3).then_some(1000 + card_id / 2),
                    deck_id: (index % 13 != 5).then_some(2000 + next().rem_euclid(3)),
                    preset_id: (index % 17 != 7).then_some(3000 + next().rem_euclid(2)),
                    is_query: false,
                    ease: (index % 19 != 11).then_some((next().rem_euclid(4) + 1) as u8),
                    duration_millis: Some(1500 + next().rem_euclid(20_000)),
                    card_type: Some(next().rem_euclid(3)),
                    day_offset: Some(7300 + (index as i64) / 7),
                    current_elapsed_days: Some(next().rem_euclid(45) - 1),
                    current_elapsed_seconds: Some(next().rem_euclid(45 * 86_400) - 1),
                    target_retentions: [Some(0.9), Some(0.9), Some(0.9), Some(0.9)],
                    enforce_grade_order: true,
                }
            })
            .collect()
    }

    fn bulk_benchmark_reviews(count: usize) -> Vec<ReviewInput> {
        let mut state = 0x243f6a8885a308d3_u64;
        let mut next = move || {
            state = state
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            (state >> 33) as i64
        };
        (0..count)
            .map(|index| {
                let card_id = 100 + index as i64 % 5_000;
                ReviewInput {
                    card_id,
                    note_id: Some(10_000 + card_id / 2),
                    deck_id: Some(20_000 + card_id.rem_euclid(8)),
                    preset_id: Some(30_000 + card_id.rem_euclid(4)),
                    is_query: false,
                    ease: Some((next().rem_euclid(4) + 1) as u8),
                    duration_millis: Some(1_500 + next().rem_euclid(20_000)),
                    card_type: Some(next().rem_euclid(3)),
                    day_offset: Some(7_300 + index as i64 / 10),
                    current_elapsed_days: Some(next().rem_euclid(45) - 1),
                    current_elapsed_seconds: Some(next().rem_euclid(45 * 86_400) - 1),
                    target_retentions: [Some(0.9), Some(0.9), Some(0.9), Some(0.9)],
                    enforce_grade_order: true,
                }
            })
            .collect()
    }

    #[test]
    #[ignore]
    fn rwkv_synthetic_bulk_benchmark() {
        let rows = scan_bench_env_usize("ANKI_RWKV_SYNTHETIC_BENCH_ROWS", 50_000);
        let samples = scan_bench_env_usize("ANKI_RWKV_SYNTHETIC_BENCH_SAMPLES", 5).max(1);
        let warmup_samples = scan_bench_env_usize("ANKI_RWKV_SYNTHETIC_BENCH_WARMUPS", 1);
        let chunk_rows = std::env::var("ANKI_RWKV_SYNTHETIC_BENCH_CHUNK_ROWS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .filter(|&value| value > 0);
        let record_predictions = std::env::var("ANKI_RWKV_BULK_BENCH_RECORD_PREDICTIONS")
            .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"));
        let model_path = embedded_weights_path().expect("RWKV parity model is missing");
        let reviews = bulk_benchmark_reviews(rows);
        let mut timings = Vec::with_capacity(samples);
        let mut log_losses = Vec::with_capacity(samples);

        println!("synthetic_rows={rows}");
        println!("samples={samples}");
        println!("warmup_samples={warmup_samples}");
        println!("record_predictions={record_predictions}");
        if let Some(chunk_rows) = chunk_rows {
            println!("chunk_rows={chunk_rows}");
        }
        if let Ok(threads) = std::env::var("RAYON_NUM_THREADS") {
            println!("rayon_num_threads={threads}");
        }

        for sample in 0..warmup_samples + samples {
            let mut inference =
                RwkvInference::load(model_path.clone(), 0.9, 36_500).expect("RWKV load failed");
            let sample_reviews = reviews.clone();
            let started = std::time::Instant::now();
            let predictions = if let Some(chunk_rows) = chunk_rows {
                bulk::warm_up_reviews_bulk_fast_query_chunked(
                    &mut inference,
                    sample_reviews,
                    record_predictions,
                    chunk_rows,
                )
            } else {
                inference.warm_up_reviews(sample_reviews, record_predictions)
            }
            .expect("RWKV warm-up failed");
            let elapsed_ms = started.elapsed().as_secs_f64() * 1_000.0;
            let log_loss = record_predictions.then(|| prediction_log_loss(&predictions, &reviews));
            std::hint::black_box((inference, predictions));
            if sample < warmup_samples {
                println!("warmup_{}_ms={elapsed_ms:.3}", sample + 1);
                if let Some(log_loss) = log_loss {
                    println!("warmup_{}_log_loss={log_loss:.15}", sample + 1);
                }
            } else {
                println!("sample_{}_ms={elapsed_ms:.3}", sample - warmup_samples + 1);
                timings.push(elapsed_ms);
                if let Some(log_loss) = log_loss {
                    println!(
                        "sample_{}_log_loss={log_loss:.15}",
                        sample - warmup_samples + 1
                    );
                    log_losses.push(log_loss);
                }
            }
        }

        timings.sort_by(f64::total_cmp);
        let median_ms = if timings.len() % 2 == 0 {
            let middle = timings.len() / 2;
            (timings[middle - 1] + timings[middle]) / 2.0
        } else {
            timings[timings.len() / 2]
        };
        println!("median_ms={median_ms:.3}");
        println!("min_ms={:.3}", timings[0]);
        println!("max_ms={:.3}", timings[timings.len() - 1]);
        if let Some((&first, rest)) = log_losses.split_first() {
            assert!(
                rest.iter().all(|&log_loss| log_loss == first),
                "logloss changed between benchmark samples: {log_losses:?}"
            );
            println!("log_loss={first:.15}");
        }
    }

    fn sorted_states(mut states: Vec<(i64, Vec<u8>)>) -> Vec<(i64, Vec<u8>)> {
        states.sort_by_key(|(id, _)| *id);
        states
    }

    fn sorted_curve_bytes(curves: &HashMap<i64, ReviewCurve>) -> Vec<(i64, Vec<u8>)> {
        let mut serialized: Vec<(i64, Vec<u8>)> = curves
            .iter()
            .map(|(id, curve)| {
                let mut bytes = Vec::new();
                curve.write_cache_state(&mut bytes);
                (*id, bytes)
            })
            .collect();
        serialized.sort_by_key(|(id, _)| *id);
        serialized
    }

    fn assert_warm_up_parity(sequential: &RwkvInference, bulk: &RwkvInference) {
        let sequential_snapshot = sequential.warm_up_snapshot();
        let bulk_snapshot = bulk.warm_up_snapshot();
        assert_eq!(
            sorted_states(sequential_snapshot.card_states),
            sorted_states(bulk_snapshot.card_states),
            "card states diverged"
        );
        assert_eq!(
            sorted_states(sequential_snapshot.note_states),
            sorted_states(bulk_snapshot.note_states),
            "note states diverged"
        );
        assert_eq!(
            sorted_states(sequential_snapshot.deck_states),
            sorted_states(bulk_snapshot.deck_states),
            "deck states diverged"
        );
        assert_eq!(
            sorted_states(sequential_snapshot.preset_states),
            sorted_states(bulk_snapshot.preset_states),
            "preset states diverged"
        );
        assert_eq!(
            sequential_snapshot.global_state, bulk_snapshot.global_state,
            "global state diverged"
        );
        assert_eq!(
            sorted_curve_bytes(&sequential.curves),
            sorted_curve_bytes(&bulk.curves),
            "curves diverged"
        );
    }

    /// Bulk and per-review replays may round compact card and note states at
    /// different points (after each chunk vs after each review), so they are
    /// compared to about that precision.
    fn assert_warm_up_close(sequential: &RwkvInference, bulk: &RwkvInference) {
        fn values(state: &[u8]) -> Vec<f32> {
            deserialize_module_state(Some(state))
                .unwrap()
                .unwrap()
                .layers
                .into_iter()
                .flat_map(|layer| {
                    let time = layer.time.map_or_else(Vec::new, |time| {
                        time.x_shift.into_iter().chain(time.matrix).collect()
                    });
                    time.into_iter()
                        .chain(layer.channel_shift.unwrap_or_default())
                })
                .collect()
        }
        fn close(label: &str, a: &[f32], b: &[f32]) {
            assert_eq!(a.len(), b.len(), "{label} sizes diverged");
            let worst = a
                .iter()
                .zip(b)
                .map(|(a, b)| (a - b).abs())
                .fold(0.0, f32::max);
            assert!(worst <= STATE_TOLERANCE, "{label} diverged by {worst}");
        }
        let a = sequential.warm_up_snapshot();
        let b = bulk.warm_up_snapshot();
        for (label, a, b) in [
            ("card", a.card_states, b.card_states),
            ("note", a.note_states, b.note_states),
            ("deck", a.deck_states, b.deck_states),
            ("preset", a.preset_states, b.preset_states),
        ] {
            let (a, b) = (sorted_states(a), sorted_states(b));
            assert_eq!(
                a.iter().map(|(id, _)| *id).collect::<Vec<_>>(),
                b.iter().map(|(id, _)| *id).collect::<Vec<_>>(),
                "{label} ids diverged"
            );
            for ((id, a), (_, b)) in a.iter().zip(&b) {
                close(&format!("{label} {id} state"), &values(a), &values(b));
            }
        }
        close(
            "global state",
            &values(a.global_state.as_deref().unwrap()),
            &values(b.global_state.as_deref().unwrap()),
        );
        let mut ids: Vec<_> = sequential.curves.keys().copied().collect();
        ids.sort_unstable();
        assert_eq!(ids.len(), bulk.curves.len(), "curves diverged");
        for id in ids {
            let (a, b) = (&sequential.curves[&id], &bulk.curves[&id]);
            close("curve logits", &a.ahead_logits, &b.ahead_logits);
            close("curve weights", &a.weights, &b.weights);
        }
    }

    /// See [assert_warm_up_close].
    const STATE_TOLERANCE: f32 = 0.01;
    const PREDICTION_TOLERANCE: f32 = 1e-3;

    fn assert_prediction_parity(sequential: &[(usize, f32)], bulk: &[(usize, f32)]) {
        assert_eq!(sequential.len(), bulk.len(), "prediction counts diverged");
        for ((sequential_index, sequential_value), (bulk_index, bulk_value)) in
            sequential.iter().zip(bulk)
        {
            assert_eq!(sequential_index, bulk_index, "prediction order diverged");
            assert_eq!(
                sequential_value.to_bits(),
                bulk_value.to_bits(),
                "prediction values diverged at review {sequential_index}"
            );
        }
    }

    fn assert_prediction_close(expected: &[(usize, f32)], actual: &[(usize, f32)], tolerance: f32) {
        assert_eq!(expected.len(), actual.len(), "prediction counts diverged");
        for ((expected_index, expected_value), (actual_index, actual_value)) in
            expected.iter().zip(actual)
        {
            assert_eq!(expected_index, actual_index, "prediction order diverged");
            let delta = (expected_value - actual_value).abs();
            assert!(
                delta <= tolerance,
                "prediction diverged at review {expected_index}: {expected_value} vs {actual_value} (delta {delta})"
            );
        }
    }

    fn prediction_log_loss(predictions: &[(usize, f32)], reviews: &[ReviewInput]) -> f64 {
        let values = predictions
            .iter()
            .map(|(_, prediction)| *prediction)
            .collect::<Vec<_>>();
        let outcomes = predictions
            .iter()
            .map(|(index, _)| reviews[*index].ease != Some(1))
            .collect::<Vec<_>>();
        MetricAccumulator::from_predictions(&values, &outcomes).log_loss
    }

    #[test]
    fn bulk_future_queries_match_independent_frozen_state_queries() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let mut reviews = bulk_parity_reviews(42);
        let mut seen_cards = std::collections::HashSet::new();
        for review in &mut reviews {
            if review.ease.is_none() {
                continue;
            }
            if seen_cards.insert(review.card_id) {
                review.card_type = Some(0);
                review.current_elapsed_days = Some(0);
                review.current_elapsed_seconds = Some(0);
            } else {
                review.card_type = Some(2);
                review.current_elapsed_days = review.current_elapsed_days.map(|days| days.max(0));
                review.current_elapsed_seconds =
                    review.current_elapsed_seconds.map(|seconds| seconds.max(0));
            }
        }
        for precision in [StatePrecision::F32, StatePrecision::Int4CardsInt8Notes] {
            for after in [false, true] {
                let queries: Vec<_> = reviews
                    .iter()
                    .enumerate()
                    .map(|(index, input)| {
                        let mut query = input.clone();
                        query.is_query = true;
                        query.ease = None;
                        query.duration_millis = None;
                        let seconds = [0, 600, 3600, 86400][index % 4];
                        let day_delta = if index % 3 == 0 || seconds == 86400 {
                            1
                        } else {
                            0
                        };
                        query.day_offset = input.day_offset.map(|day| day + day_delta);
                        query.current_elapsed_days = Some(if after {
                            day_delta
                        } else {
                            input.current_elapsed_days.unwrap_or(0).max(0) + day_delta
                        });
                        query.current_elapsed_seconds = Some(if after {
                            seconds
                        } else {
                            input.current_elapsed_seconds.unwrap_or(0).max(0) + seconds
                        });
                        query
                    })
                    .collect();
                let mut reference =
                    RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                        .unwrap();
                let mut expected = Vec::new();
                for (index, (review, query)) in reviews.iter().zip(&queries).enumerate() {
                    if review.ease.is_none() {
                        continue;
                    }
                    if after {
                        reference
                            .warm_up_reviews(vec![review.clone()], false)
                            .unwrap();
                    }
                    let prediction = reference
                        .predict_retrievability_many_from_warm_up(vec![query.clone()])
                        .unwrap()[0];
                    expected.push((index, prediction));
                    if !after {
                        reference
                            .warm_up_reviews(vec![review.clone()], false)
                            .unwrap();
                    }
                }
                let mut bulk =
                    RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                        .unwrap();
                let mut actual = Vec::new();
                for offset in (0..reviews.len()).step_by(13) {
                    let end = (offset + 13).min(reviews.len());
                    actual.extend(
                        bulk.warm_up_reviews_query(
                            reviews[offset..end].to_vec(),
                            queries[offset..end].to_vec(),
                            after,
                        )
                        .unwrap()
                        .into_iter()
                        .map(|(index, value)| (offset + index, value)),
                    );
                }
                assert_prediction_close(&expected, &actual, 1e-5);
                assert_warm_up_parity(&reference, &bulk);
            }
        }
    }

    #[test]
    fn bulk_future_queries_reject_mismatched_scope_before_mutating_state() {
        let Some(weights) = embedded_weights_path() else {
            return;
        };
        let reviews = bulk_parity_reviews(2);
        let mut queries = reviews.clone();
        for query in &mut queries {
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
        }
        queries[1].card_id += 1;
        let mut inference = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        let fresh = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        assert_eq!(
            inference
                .warm_up_reviews_query(reviews, queries, false)
                .unwrap_err()
                .kind(),
            io::ErrorKind::InvalidInput
        );
        assert_warm_up_parity(&fresh, &inference);
    }

    #[test]
    fn bulk_post_answer_predictions_match_independent_queries() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let mut reviews = bulk_parity_reviews(42);
        let mut seen_cards = std::collections::HashSet::new();
        for review in &mut reviews {
            if review.ease.is_none() {
                continue;
            }
            if seen_cards.insert(review.card_id) {
                review.card_type = Some(0);
                review.current_elapsed_days = Some(0);
                review.current_elapsed_seconds = Some(0);
            } else {
                review.card_type = Some(2);
                review.current_elapsed_days = review.current_elapsed_days.map(|days| days.max(0));
                review.current_elapsed_seconds =
                    review.current_elapsed_seconds.map(|secs| secs.max(0));
            }
        }
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            let mut sequential =
                RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                    .unwrap();
            let mut expected = Vec::new();
            for (index, input) in reviews.iter().enumerate() {
                if input.ease.is_none() {
                    continue;
                }
                sequential
                    .warm_up_reviews(vec![input.clone()], false)
                    .unwrap();
                let mut query = input.clone();
                query.is_query = true;
                query.ease = None;
                query.duration_millis = None;
                query.current_elapsed_days = Some(0);
                query.current_elapsed_seconds = Some(0);
                let value = sequential
                    .predict_retrievability_many_from_warm_up(vec![query])
                    .unwrap()[0];
                expected.push((index, value));
            }

            let mut bulk =
                RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                    .unwrap();
            let actual =
                bulk::warm_up_reviews_post_bulk_chunked(&mut bulk, reviews.clone(), 7).unwrap();
            assert_prediction_close(&expected, &actual, 1e-5);
            assert_warm_up_parity(&sequential, &bulk);

            let mut resumed =
                RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                    .unwrap();
            let mut split =
                bulk::warm_up_reviews_post_bulk_chunked(&mut resumed, reviews[..17].to_vec(), 7)
                    .unwrap();
            split.extend(
                bulk::warm_up_reviews_post_bulk_chunked(&mut resumed, reviews[17..].to_vec(), 7)
                    .unwrap()
                    .into_iter()
                    .map(|(index, value)| (index + 17, value)),
            );
            assert_prediction_close(&expected, &split, 1e-5);
            assert_warm_up_parity(&sequential, &resumed);
        }
    }

    #[test]
    fn bulk_warm_up_matches_sequential() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(230);
        for record_predictions in [false, true] {
            let mut sequential = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
            let sequential_predictions = sequential
                .warm_up_reviews_sequential(reviews.clone(), record_predictions)
                .unwrap();

            let mut bulk = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
            let bulk_predictions =
                bulk::warm_up_reviews_bulk(&mut bulk, reviews.clone(), record_predictions).unwrap();

            assert_prediction_close(
                &sequential_predictions,
                &bulk_predictions,
                PREDICTION_TOLERANCE,
            );
            if record_predictions {
                let sequential_log_loss = prediction_log_loss(&sequential_predictions, &reviews);
                let bulk_log_loss = prediction_log_loss(&bulk_predictions, &reviews);
                let log_loss_delta = (bulk_log_loss - sequential_log_loss).abs();
                assert!(
                    log_loss_delta <= 1e-4,
                    "logloss changed: before={sequential_log_loss:.15}, after={bulk_log_loss:.15}, delta={log_loss_delta:.15}"
                );
            }
            assert_warm_up_close(&sequential, &bulk);
            assert_eq!(
                sequential.cache_state().len(),
                bulk.cache_state().len(),
                "runtime cache size diverged (record={record_predictions})"
            );
        }
    }

    #[test]
    fn stashed_warm_up_state_restores_resident_state_and_pending_delta() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        for (spill_to_file, global_dirty) in
            [(false, false), (false, true), (true, false), (true, true)]
        {
            let dir = tempfile::tempdir().unwrap();
            let path = dir.path().join("stash-spill.bin");
            let reviews = bulk_parity_reviews(40);
            let mut inference = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
            let empty_cache_state = inference.cache_state();
            inference
                .warm_up_reviews_sequential(reviews[..16].to_vec(), false)
                .unwrap();
            inference.warm_up_states.mark_checkpoint_saved();
            inference
                .warm_up_reviews_sequential(reviews[16..24].to_vec(), false)
                .unwrap();
            // Include dirty deletions as well as updated and clean entries.
            inference.warm_up_states.dirty_card.insert(-1);
            inference.warm_up_states.dirty_note.insert(-2);
            inference.warm_up_states.dirty_deck.insert(-3);
            inference.warm_up_states.dirty_preset.insert(-4);
            inference.warm_up_states.global_dirty = global_dirty;
            let cache_state = inference.cache_state();
            let snapshot = inference.warm_up_snapshot();
            let pending_delta_len = inference
                .warm_up_states
                .checkpoint_delta_len(false)
                .unwrap();
            let mut pending_delta = Vec::new();
            inference
                .warm_up_states
                .write_checkpoint_delta(&mut pending_delta, false)
                .unwrap();
            assert!(!snapshot.card_states.is_empty());
            assert!(
                pending_delta_len
                    > ReviewStateMaps::default()
                        .checkpoint_delta_len(false)
                        .unwrap()
            );

            if spill_to_file {
                inference.stash_warm_up_state_to_file(&path).unwrap();
                let stashed = inference.stashed_warm_up_state.as_ref().unwrap();
                assert!(stashed.warm_up_states.card.is_empty());
                assert!(stashed.warm_up_states.note.is_empty());
                assert_eq!(stashed.warm_up_states.card.capacity(), 0);
                assert_eq!(stashed.warm_up_states.note.capacity(), 0);
                assert!(path.exists());
            } else {
                inference.stash_warm_up_state().unwrap();
            }
            assert!(inference.stash_warm_up_state().is_err());
            assert!(inference.stash_warm_up_state_to_file(&path).is_err());
            assert_eq!(inference.cache_state(), empty_cache_state);
            assert!(inference.warm_up_snapshot().card_states.is_empty());
            assert!(inference.warm_up_snapshot().global_state.is_none());

            inference.reset_warm_up_state();
            inference
                .warm_up_reviews_sequential(reviews[8..].to_vec(), true)
                .unwrap();
            inference.restore_stashed_warm_up_state().unwrap();
            assert!(!path.exists());
            assert!(inference.restore_stashed_warm_up_state().is_err());

            let restored = inference.warm_up_snapshot();
            assert_eq!(inference.cache_state(), cache_state);
            assert_eq!(restored.card_states, snapshot.card_states);
            assert_eq!(restored.note_states, snapshot.note_states);
            assert_eq!(restored.deck_states, snapshot.deck_states);
            assert_eq!(restored.preset_states, snapshot.preset_states);
            assert_eq!(restored.global_state, snapshot.global_state);
            // Unlike a serialized snapshot restore, the stash keeps the changes
            // that the next delta checkpoint still has to write.
            assert_eq!(
                inference
                    .warm_up_states
                    .checkpoint_delta_len(false)
                    .unwrap(),
                pending_delta_len
            );
            let mut restored_delta = Vec::new();
            inference
                .warm_up_states
                .write_checkpoint_delta(&mut restored_delta, false)
                .unwrap();
            assert_eq!(restored_delta, pending_delta);
        }
    }

    #[test]
    fn stash_round_trip_keeps_later_predictions_bit_identical_after_temporary_replay() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        for spill_to_file in [false, true] {
            let dir = tempfile::tempdir().unwrap();
            let path = dir.path().join("stash-spill.bin");
            let reviews = bulk_parity_reviews(120);
            let (history, later) = reviews.split_at(60);
            let queries = later
                .iter()
                .map(|review| ReviewInput {
                    is_query: true,
                    ease: None,
                    duration_millis: None,
                    ..review.clone()
                })
                .collect::<Vec<_>>();
            let mut untouched = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
            let mut stashed = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
            untouched.warm_up_reviews(history.to_vec(), false).unwrap();
            stashed.warm_up_reviews(history.to_vec(), false).unwrap();

            if spill_to_file {
                stashed.stash_warm_up_state_to_file(&path).unwrap();
            } else {
                stashed.stash_warm_up_state().unwrap();
            }
            stashed.reset_warm_up_state();
            stashed.warm_up_reviews(reviews.clone(), true).unwrap();
            stashed.restore_stashed_warm_up_state().unwrap();

            let bits = |predictions: Vec<f32>| {
                predictions
                    .into_iter()
                    .map(f32::to_bits)
                    .collect::<Vec<_>>()
            };
            assert_eq!(
                bits(
                    stashed
                        .predict_retrievability_many_from_warm_up(queries.clone())
                        .unwrap()
                ),
                bits(
                    untouched
                        .predict_retrievability_many_from_warm_up(queries)
                        .unwrap()
                ),
            );
            // Continuing from the restored state must match continuing from a
            // state that was never stashed: features, curves and states included.
            assert_prediction_parity(
                &untouched.warm_up_reviews(later.to_vec(), true).unwrap(),
                &stashed.warm_up_reviews(later.to_vec(), true).unwrap(),
            );
            assert_warm_up_parity(&untouched, &stashed);
            assert_eq!(stashed.cache_state(), untouched.cache_state());
        }
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn spill_stash_restores_empty_resident_state() {
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let before = inference.cache_state();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("stash-spill.bin");
        inference.stash_warm_up_state_to_file(&path).unwrap();
        inference
            .warm_up_reviews(bulk_parity_reviews(8), true)
            .unwrap();
        inference.restore_stashed_warm_up_state().unwrap();
        assert_eq!(inference.cache_state(), before);
        assert!(inference.warm_up_snapshot().card_states.is_empty());
        assert!(inference.warm_up_snapshot().note_states.is_empty());
        assert!(!inference.warm_up_states.global_dirty);
        assert!(!path.exists());
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn spill_restore_keeps_stash_for_retry_when_file_is_missing_truncated_or_corrupt() {
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let reviews = bulk_parity_reviews(8);
        inference.warm_up_reviews(reviews.clone(), false).unwrap();
        let before = inference.cache_state();
        let mut before_delta = Vec::new();
        inference
            .warm_up_states
            .write_checkpoint_delta(&mut before_delta, true)
            .unwrap();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("stash-spill.bin");
        inference.stash_warm_up_state_to_file(&path).unwrap();
        let valid = fs::read(&path).unwrap();
        inference.warm_up_reviews(reviews, true).unwrap();

        fs::remove_file(&path).unwrap();
        assert!(inference.restore_stashed_warm_up_state().is_err());
        assert!(inference.stashed_warm_up_state.is_some());

        let first_length = WARM_UP_STATE_SPILL_MAGIC.len() + 4 + 8;
        let first_state = first_length + 4;
        let mut corrupt_length = valid.clone();
        corrupt_length[first_length..first_state].copy_from_slice(&u32::MAX.to_le_bytes());
        let mut corrupt_layers = valid.clone();
        let layers_offset = first_state + b"ARWKVMODSTATE1".len();
        corrupt_layers[layers_offset..layers_offset + 4].copy_from_slice(&u32::MAX.to_le_bytes());
        // A valid floating-point byte change must also fail integrity checking.
        let mut corrupt_payload = valid.clone();
        corrupt_payload[first_state + 22] ^= 1;
        let mut trailing = valid.clone();
        trailing.push(0);
        for bytes in [
            valid[..1].to_vec(),
            valid[..valid.len() / 2].to_vec(),
            valid[..valid.len() - 1].to_vec(),
            corrupt_length,
            corrupt_layers,
            corrupt_payload,
            trailing,
        ] {
            fs::write(&path, bytes).unwrap();
            let error = inference.restore_stashed_warm_up_state().unwrap_err();
            assert!(matches!(
                error.kind(),
                io::ErrorKind::InvalidData | io::ErrorKind::UnexpectedEof
            ));
            let stashed = inference.stashed_warm_up_state.as_ref().unwrap();
            assert!(stashed.spill.is_some());
            assert!(stashed.warm_up_states.card.is_empty());
            assert!(stashed.warm_up_states.note.is_empty());
            assert!(path.exists());
        }

        fs::write(&path, valid).unwrap();
        inference.restore_stashed_warm_up_state().unwrap();
        assert!(!path.exists());
        assert_eq!(inference.cache_state(), before);
        let mut restored_delta = Vec::new();
        inference
            .warm_up_states
            .write_checkpoint_delta(&mut restored_delta, true)
            .unwrap();
        assert_eq!(restored_delta, before_delta);
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn discard_stashed_warm_up_state_allows_another_stash() {
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("stash-spill.bin");
        for spill_failure in [None, Some("corrupt"), Some("missing")] {
            inference
                .warm_up_reviews(bulk_parity_reviews(8), false)
                .unwrap();
            if let Some(failure) = spill_failure {
                inference.stash_warm_up_state_to_file(&path).unwrap();
                if failure == "missing" {
                    fs::remove_file(&path).unwrap();
                } else {
                    fs::write(&path, b"corrupt").unwrap();
                }
                assert!(inference.restore_stashed_warm_up_state().is_err());
            } else {
                inference.stash_warm_up_state().unwrap();
            }

            inference.discard_stashed_warm_up_state();
            assert!(inference.stashed_warm_up_state.is_none());
            assert!(!path.exists());
            assert!(inference.restore_stashed_warm_up_state().is_err());
            // Discard is also safe when no stash is active.
            inference.discard_stashed_warm_up_state();
            inference.stash_warm_up_state_to_file(&path).unwrap();
            inference.restore_stashed_warm_up_state().unwrap();
            assert!(!path.exists());
        }
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn discard_stashed_warm_up_state_ignores_spill_delete_errors() {
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("stash-spill.bin");
        inference.stash_warm_up_state_to_file(&path).unwrap();
        fs::remove_file(&path).unwrap();
        fs::create_dir(&path).unwrap();

        inference.discard_stashed_warm_up_state();

        assert!(inference.stashed_warm_up_state.is_none());
        inference.stash_warm_up_state().unwrap();
        inference.restore_stashed_warm_up_state().unwrap();
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn spill_write_failure_keeps_resident_state_and_allows_in_memory_stash() {
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        inference
            .warm_up_reviews(bulk_parity_reviews(8), false)
            .unwrap();
        let before = inference.cache_state();
        let mut before_delta = Vec::new();
        inference
            .warm_up_states
            .write_checkpoint_delta(&mut before_delta, false)
            .unwrap();
        let dir = tempfile::tempdir().unwrap();
        // Fail both temporary-file creation and the final rename, after writing.
        for path in [
            dir.path().join("missing/stash-spill.bin"),
            dir.path().to_owned(),
        ] {
            assert!(inference.stash_warm_up_state_to_file(&path).is_err());
            assert!(inference.stashed_warm_up_state.is_none());
            assert_eq!(inference.cache_state(), before);
            let mut delta = Vec::new();
            inference
                .warm_up_states
                .write_checkpoint_delta(&mut delta, false)
                .unwrap();
            assert_eq!(delta, before_delta);
            assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
        }
        inference.stash_warm_up_state().unwrap();
        inference.restore_stashed_warm_up_state().unwrap();
        assert_eq!(inference.cache_state(), before);
    }

    #[test]
    fn delta_state_store_restores_checkpoint_chain() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(40);
        let prefix = reviews[..32].to_vec();
        let suffix = reviews[..8].to_vec();
        let temporary_dir = tempfile::tempdir().unwrap();
        let store_path = temporary_dir.path().join("state.sqlite3");
        let generation = "test-generation";

        let mut expected_prefix = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        expected_prefix
            .warm_up_reviews(prefix.clone(), false)
            .unwrap();

        let mut expected_final = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        expected_final.warm_up_reviews(prefix, false).unwrap();
        let mut store = StateCacheStoreWriter::open(store_path.clone(), generation, false).unwrap();
        store.delta_chunk_bytes = 64 * 1024;
        expected_final.state_cache_store = Some(store);
        let first_segment = expected_final
            .write_warm_up_state_checkpoint(
                store_path.clone(),
                generation,
                None,
                32,
                32,
                "prefix-hash",
                "replay-key",
                b"previous-ids",
                b"previous-intervals",
                b"review-counts",
                false,
                false,
            )
            .unwrap();
        expected_final
            .warm_up_reviews(suffix.clone(), false)
            .unwrap();
        let second_segment = expected_final
            .write_warm_up_state_checkpoint(
                store_path.clone(),
                generation,
                Some(first_segment),
                40,
                40,
                "final-hash",
                "replay-key",
                b"previous-ids",
                b"previous-intervals",
                b"review-counts",
                false,
                false,
            )
            .unwrap();
        assert!(expected_final.state_cache_store.is_some());
        expected_final.finish_warm_up_state_checkpoints().unwrap();
        assert!(expected_final.state_cache_store.is_none());

        let connection = Connection::open(&store_path).unwrap();
        let page_size: i64 = connection
            .pragma_query_value(None, "page_size", |row| row.get(0))
            .unwrap();
        assert_eq!(page_size, STATE_CACHE_STORE_PAGE_SIZE);
        let first_state_bytes: i64 = connection
            .query_row(
                "select coalesce(sum(length(state_delta)), 0) \
                 from segment_state_chunks where segment_id = ?",
                [first_segment],
                |row| row.get(0),
            )
            .unwrap();
        let second_state_bytes: i64 = connection
            .query_row(
                "select coalesce(sum(length(state_delta)), 0) \
                 from segment_state_chunks where segment_id = ?",
                [second_segment],
                |row| row.get(0),
            )
            .unwrap();
        let first_chunk_count: i64 = connection
            .query_row(
                "select count(*) from segment_state_chunks where segment_id = ?",
                [first_segment],
                |row| row.get(0),
            )
            .unwrap();
        assert!(
            first_chunk_count > 1,
            "test checkpoint should exercise multi-chunk state"
        );
        assert!(
            second_state_bytes < first_state_bytes,
            "suffix checkpoint should contain only dirty states: {second_state_bytes} >= {first_state_bytes}"
        );

        let mut restored_prefix = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        restored_prefix
            .restore_warm_up_state_checkpoint(store_path.clone(), generation, first_segment)
            .unwrap();
        assert_warm_up_parity(&expected_prefix, &restored_prefix);

        let mut restored_final = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        restored_final
            .restore_warm_up_state_checkpoint(store_path, generation, second_segment)
            .unwrap();
        assert_warm_up_parity(&expected_final, &restored_final);
    }

    #[test]
    fn streamed_module_state_matches_existing_serialization() {
        let state = ModuleState {
            packed: None,
            layers: vec![
                LayerState {
                    time: Some(TimeState {
                        x_shift: vec![1.0, -2.5, f32::INFINITY],
                        matrix: vec![0.0, f32::NAN, 42.25, -0.0],
                        packed_matrix: None,
                    }),
                    channel_shift: Some(vec![3.5, -7.75]),
                },
                LayerState {
                    time: None,
                    channel_shift: None,
                },
            ],
        };
        let serialized = serialize_module_state(&state);
        let mut streamed = Vec::new();
        write_serialized_module_state(&mut streamed, &state).unwrap();
        assert_eq!(streamed, serialized);

        let mut expected_snapshot = Vec::new();
        write_snapshot_bytes(&mut expected_snapshot, &serialized).unwrap();
        let mut streamed_snapshot = Vec::new();
        write_snapshot_module_state(&mut streamed_snapshot, &state).unwrap();
        assert_eq!(streamed_snapshot, expected_snapshot);
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn full_state_checkpoint_bounds_sqlite_memory() {
        // SQLite's memory high-water counter is process-wide. Isolate this
        // measurement from other tests, including when the suite runs in parallel.
        const CHILD_ENV: &str = "ANKI_RWKV_CHECKPOINT_MEMORY_CHILD";
        if std::env::var_os(CHILD_ENV).is_none() {
            let output = std::process::Command::new(std::env::current_exe().unwrap())
                .args([
                    "--exact",
                    "rwkv::tests::full_state_checkpoint_bounds_sqlite_memory",
                    "--nocapture",
                    "--test-threads=1",
                ])
                .env(CHILD_ENV, "1")
                .output()
                .unwrap();
            eprintln!("{}", String::from_utf8_lossy(&output.stderr));
            assert!(
                output.status.success(),
                "isolated checkpoint test failed: {}",
                String::from_utf8_lossy(&output.stdout)
            );
            return;
        }
        // Match the real warm-up: Anki's collection has a populated 40 MiB
        // cache of 4 KiB pages when the 64 KiB state-store writer starts.
        // SQLite's shared LRU pool must not turn that page count into hundreds
        // of MiB of state-store pages.
        let collection_dir = tempfile::tempdir().unwrap();
        let collection =
            Connection::open(collection_dir.path().join("collection.sqlite3")).unwrap();
        collection
            .pragma_update(None, "cache_size", -40 * 1024)
            .unwrap();
        collection
            .execute_batch(
                "create table test (id integer primary key, data blob);
            with recursive ids(id) as (values(1) union all select id+1 from ids where id < 10000)
            insert into test select id, zeroblob(4000) from ids;",
            )
            .unwrap();
        let weights = embedded_weights_path().expect("embedded RWKV weights");
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        // 200 MiB of synthetic state spans four production 64 MiB chunks.
        inference.warm_up_states.card = (0..800)
            .map(|id| {
                (
                    id,
                    ModuleState {
                        packed: None,
                        layers: vec![LayerState {
                            time: None,
                            channel_shift: Some(vec![id as f32; 64 * 1024]),
                        }],
                    },
                )
            })
            .collect();
        assert!(inference.warm_up_states.checkpoint_delta_len(true).unwrap() >= 200 * 1024 * 1024);
        let temporary_dir = tempfile::tempdir().unwrap();
        for durable in [false, true] {
            let path = temporary_dir
                .path()
                .join(format!("state-{durable}.sqlite3"));
            for parent in [None, Some(1), Some(2)] {
                if parent == Some(2) {
                    // Also check a reopened store whose page size is persisted.
                    inference.finish_warm_up_state_checkpoints().unwrap();
                }
                // Repopulate the collection cache between checkpoints too.
                let bytes: i64 = collection
                    .query_row("select sum(length(data)) from test", [], |row| row.get(0))
                    .unwrap();
                assert_eq!(bytes, 40_000_000);
                // SAFETY: this only resets SQLite's allocator statistics, and
                // no other test is using SQLite in this child process.
                unsafe { rusqlite::ffi::sqlite3_memory_highwater(1) };
                let segment = inference
                    .write_warm_up_state_checkpoint(
                        path.clone(),
                        "generation",
                        parent,
                        0,
                        0,
                        "hash",
                        "key",
                        b"",
                        b"",
                        b"",
                        true,
                        durable,
                    )
                    .unwrap();
                // SAFETY: this reads SQLite's process-wide allocator statistics.
                let peak = unsafe { rusqlite::ffi::sqlite3_memory_highwater(0) };
                eprintln!("checkpoint {segment}, durable={durable}: SQLite peak {peak} bytes");
                assert!(peak > 0, "SQLite memory statistics must be enabled");
                assert!(peak < 64 * 1024 * 1024, "SQLite peak was {peak} bytes");
                let store = inference.state_cache_store.as_ref().unwrap();
                let connection = store.connection.lock().unwrap();
                let chunk_lengths: Vec<usize> = connection
                    .prepare(
                        "select length(state_delta) from segment_state_chunks \
                         where segment_id = ? order by chunk_index",
                    )
                    .unwrap()
                    .query_map([segment], |row| row.get(0))
                    .unwrap()
                    .collect::<rusqlite::Result<_>>()
                    .unwrap();
                assert_eq!(chunk_lengths.len(), 4);
                assert_eq!(&chunk_lengths[..3], &[STATE_CACHE_DELTA_CHUNK_BYTES; 3]);
                let mut used = 0;
                let mut highwater = 0;
                // SAFETY: the connection is open and locked, and both output
                // pointers refer to live integers for the duration of the call.
                let result = unsafe {
                    rusqlite::ffi::sqlite3_db_status(
                        connection.handle(),
                        rusqlite::ffi::SQLITE_DBSTATUS_CACHE_USED,
                        &mut used,
                        &mut highwater,
                        0,
                    )
                };
                assert_eq!(result, rusqlite::ffi::SQLITE_OK);
                eprintln!("checkpoint {segment}, durable={durable}: SQLite cache {used} bytes");
                assert!(used < 4 * 1024 * 1024, "SQLite cache retained {used} bytes");
            }
            inference
                .restore_warm_up_state_checkpoint(path, "generation", 3)
                .unwrap();
            assert_eq!(inference.warm_up_states.card.len(), 800);
            for (id, state) in inference.warm_up_states.card.iter() {
                assert_eq!(state.layers.len(), 1);
                let time = state.layers[0].time.as_ref().unwrap();
                assert!(time.x_shift.is_empty());
                assert!(time.matrix.is_empty());
                assert_eq!(
                    state.layers[0].channel_shift.as_ref().unwrap(),
                    &vec![*id as f32; 64 * 1024]
                );
            }
        }
    }

    #[test]
    fn state_delta_chunk_reader_seeks_across_chunks() {
        const CHUNK_BYTES: usize = 64 * 1024;
        let temporary_dir = tempfile::tempdir().unwrap();
        let path = temporary_dir.path().join("state.sqlite3");
        let mut connection = open_state_cache_store(&path, "generation", false).unwrap();
        let data: Vec<u8> = (0..3 * CHUNK_BYTES + 123)
            .map(|index| (index % 251) as u8)
            .collect();
        let transaction = connection.transaction().unwrap();
        transaction
            .execute(
                "insert into segments values (1, null, 0, 0, '', '', x'', x'', x'', x'')",
                [],
            )
            .unwrap();
        let chunk_ids =
            insert_state_cache_delta_chunks(&transaction, 1, data.len(), CHUNK_BYTES, 0).unwrap();
        let mut writer =
            StateCacheDeltaChunkWriter::new(&transaction, chunk_ids, data.len()).unwrap();
        io::Write::write_all(&mut writer, &data).unwrap();
        writer.finish().unwrap();
        transaction.commit().unwrap();

        let chunk_ids = state_cache_delta_chunk_ids(&connection, 1, 0).unwrap();
        assert_eq!(chunk_ids.len(), 4);
        let mut reader = io::BufReader::with_capacity(
            1000,
            StateCacheDeltaChunkReader::new(&connection, chunk_ids).unwrap(),
        );
        let mut position = 0;
        for (skip, read) in [
            (0, 10),
            (5, 3_000),
            (CHUNK_BYTES, 7),
            (2 * CHUNK_BYTES - 20_000, 500),
        ] {
            io::Seek::seek_relative(&mut reader, skip as i64).unwrap();
            position += skip;
            let mut bytes = vec![0; read];
            io::Read::read_exact(&mut reader, &mut bytes).unwrap();
            assert_eq!(bytes, data[position..position + read], "at {position}");
            position += read;
        }
        let remaining = data.len() - position;
        assert!(io::Seek::seek_relative(&mut reader, remaining as i64 + 1).is_err());
    }

    #[test]
    fn same_card_multi_answer_prediction_matches_individual_branches() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(64);
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        inference.warm_up_reviews(reviews.clone(), false).unwrap();

        let mut query = reviews.last().unwrap().clone();
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        let answers = ANSWER_EASES
            .into_iter()
            .map(|ease| simulated_answer_input(&query, ease))
            .collect::<Vec<_>>();
        let snapshot = || {
            let states = inference.warm_up_snapshot();
            RwkvWorkloadSimulationSnapshot {
                card_states: states.card_states,
                note_states: states.note_states,
                deck_states: states.deck_states,
                preset_states: states.preset_states,
                global_state: states.global_state,
                runtime_state: Some(inference.cache_state()),
            }
        };

        let expected = answers
            .iter()
            .map(|answer| {
                inference
                    .predict_retrievability_many_after_review(
                        answer.clone(),
                        vec![query.clone()],
                        snapshot(),
                    )
                    .unwrap()[0]
            })
            .collect::<Vec<_>>();
        let snapshot_actual = inference
            .predict_retrievability_many_after_reviews(
                answers.clone(),
                vec![query.clone()],
                snapshot(),
            )
            .unwrap()
            .into_iter()
            .map(|scores| scores[0])
            .collect::<Vec<_>>();
        let resident_state_before = inference.cache_state();
        let resident_actual = inference
            .predict_retrievability_many_after_reviews_from_warm_up(answers, vec![query])
            .unwrap()
            .into_iter()
            .map(|scores| scores[0])
            .collect::<Vec<_>>();

        assert_close(&snapshot_actual, &expected, 1e-6);
        assert_close(&resident_actual, &expected, 1e-6);
        assert_eq!(inference.cache_state(), resident_state_before);
    }

    #[test]
    fn bulk_warm_up_chunked_calls_match_sequential() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(160);

        let mut sequential = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        let sequential_predictions = sequential
            .warm_up_reviews_sequential(reviews.clone(), true)
            .unwrap();

        // Uneven bridge-style calls plus a tiny internal chunk size, so both
        // cross-call and cross-chunk stream continuity are exercised.
        let mut bulk = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let mut bulk_predictions = Vec::new();
        let mut offset = 0;
        for call in [37, 96, 27] {
            let calls: Vec<ReviewInput> = reviews[offset..offset + call].to_vec();
            let predictions =
                bulk::warm_up_reviews_bulk_chunked(&mut bulk, calls, true, 7).unwrap();
            bulk_predictions.extend(
                predictions
                    .into_iter()
                    .map(|(index, value)| (index + offset, value)),
            );
            offset += call;
        }

        assert_prediction_close(
            &sequential_predictions,
            &bulk_predictions,
            PREDICTION_TOLERANCE,
        );
        assert_warm_up_close(&sequential, &bulk);
    }

    #[test]
    fn bulk_state_only_wavefront_matches_sequential_across_chunks() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(160);

        let mut sequential = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        sequential
            .warm_up_reviews_sequential(reviews.clone(), false)
            .unwrap();

        let mut wavefront = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let mut offset = 0;
        for call in [37, 96, 27] {
            let predictions = bulk::warm_up_reviews_bulk_chunked(
                &mut wavefront,
                reviews[offset..offset + call].to_vec(),
                false,
                7,
            )
            .unwrap();
            assert!(predictions.is_empty());
            offset += call;
        }

        assert_warm_up_close(&sequential, &wavefront);
    }

    #[test]
    fn bulk_fast_query_preserves_state_with_small_prediction_delta() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let reviews = bulk_parity_reviews(160);

        let mut exact = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        let exact_predictions =
            bulk::warm_up_reviews_bulk_chunked(&mut exact, reviews.clone(), true, 7).unwrap();
        let mut fast = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        let fast_predictions =
            bulk::warm_up_reviews_bulk_fast_query_chunked(&mut fast, reviews, true, 7).unwrap();

        assert_eq!(exact_predictions.len(), fast_predictions.len());
        let mut max_delta = 0.0_f32;
        for ((exact_index, exact), (fast_index, fast)) in
            exact_predictions.iter().zip(&fast_predictions)
        {
            assert_eq!(exact_index, fast_index);
            max_delta = max_delta.max((exact - fast).abs());
        }
        assert!(
            max_delta <= 1e-5,
            "max fast bulk prediction delta: {max_delta}"
        );
        assert_warm_up_parity(&exact, &fast);
    }

    #[test]
    fn clamped_interval_days_rounds_up_to_target_crossing() {
        assert_eq!(clamped_interval_days(1.0, 10), 1);
        assert_eq!(clamped_interval_days(1.1, 10), 2);
        assert_eq!(clamped_interval_days(12.0, 10), 10);
    }

    #[test]
    fn interval_for_curve_returns_max_when_target_not_reached() {
        if SMALL_MODEL {
            return; // the big model's 128-curve head
        }
        let curve = ReviewCurve {
            power: None,
            ahead_logits: vec![20.0],
            weights: vec![1.0],
        };

        assert_eq!(interval_for_curve(&curve, 0.0, 365), Some(365));
    }

    fn basis_curve(basis_index: usize) -> ReviewCurve {
        let mut weights = vec![0.0; basis_index + 1];
        weights[basis_index] = 1.0;
        ReviewCurve {
            power: None,
            ahead_logits: vec![0.0],
            weights,
        }
    }

    #[test]
    fn rwkv_crossings_are_fractional_and_lie_on_the_curve() {
        if SMALL_MODEL {
            return; // the big model's 128-curve head
        }
        for basis in [32, 60, 90] {
            let curve = basis_curve(basis);
            let crossing = unrounded_interval_for_curve(&curve, 0.9, 36_500).unwrap();
            let at = |days: f32| predict_curve(&curve, days * SECONDS_PER_DAY as f32);
            assert!(at(crossing) <= 0.9 + 1e-6, "basis {basis}: {crossing}");
            let earlier = crossing - 5.0 / SECONDS_PER_DAY as f32 - crossing * 2e-6;
            assert!(at(earlier) > 0.9, "basis {basis}: {crossing}");
        }

        let fast = unrounded_interval_for_curve(&basis_curve(32), 0.9, 36_500).unwrap();
        assert!(fast > 0.03 && fast < 0.05, "{fast}");
        let slow = unrounded_interval_for_curve(&basis_curve(60), 0.9, 36_500).unwrap();
        assert!(slow > 1.0 && slow != slow.round(), "{slow}");
    }

    #[test]
    fn rwkv_crossings_already_at_target_use_one_second() {
        let power_curve = ReviewCurve {
            power: Some((vec![0.0], vec![0.0])),
            ahead_logits: vec![0.0],
            weights: vec![1.0],
        };
        let one_second = 1.0 / SECONDS_PER_DAY as f32;
        for curve in [basis_curve(0), power_curve] {
            for target in [0.9, predict_curve(&curve, 1.0)] {
                assert_eq!(
                    unrounded_interval_for_curve(&curve, target, 36_500),
                    Some(one_second)
                );
                for enforce_grade_order in [false, true] {
                    assert_eq!(
                        unrounded_intervals_for_answer_curves(
                            [&curve; 4],
                            [target; 4],
                            36_500,
                            enforce_grade_order,
                        ),
                        [Some(one_second); 4]
                    );
                }
            }
        }
    }

    #[test]
    fn rwkv_subminute_crossings_keep_immediate_grade_before_later_grades() {
        let intervals = crossings_on_grid(30, |days| {
            [-1.0, 5.0, 15.0, 45.0].map(|seconds| seconds / SECONDS_PER_DAY as f32 - days)
        });
        for (days, expected_seconds) in intervals.into_iter().zip([1.0, 5.0, 15.0, 45.0]) {
            assert!((days * SECONDS_PER_DAY as f32 - expected_seconds).abs() < 0.001);
        }
    }

    #[test]
    fn predict_curve_ignores_ahead_logits_and_is_monotonic() {
        if SMALL_MODEL {
            return; // the big model's 128-curve head
        }
        let curve = ReviewCurve {
            power: None,
            ahead_logits: vec![-20.0, 20.0, -20.0, 20.0],
            weights: vec![0.2, 0.3, 0.5],
        };
        let same_weights = ReviewCurve {
            power: None,
            ahead_logits: vec![20.0, -20.0, 20.0, -20.0],
            weights: curve.weights.clone(),
        };
        let elapsed_seconds = [1.0, 60.0, 3_600.0, 86_400.0, 2_592_000.0];
        let values = elapsed_seconds.map(|seconds| predict_curve(&curve, seconds));

        assert_eq!(
            values,
            elapsed_seconds.map(|seconds| predict_curve(&same_weights, seconds))
        );
        assert!(values.windows(2).all(|pair| pair[0] >= pair[1]));
    }

    #[test]
    fn pava_averages_adjacent_grade_order_violations() {
        let adjusted = pava_non_decreasing([0.30, 0.25, 0.60, 0.90]);

        assert!((adjusted[0] - 0.275).abs() < 1e-6);
        assert!((adjusted[1] - 0.275).abs() < 1e-6);
        assert_eq!(adjusted[2], 0.60);
        assert_eq!(adjusted[3], 0.90);
    }

    #[test]
    fn pava_merges_again_when_an_averaged_block_still_violates_order() {
        let adjusted = pava_non_decreasing([0.55, 0.40, 0.35, 0.90]);
        let pooled = (0.55 + 0.40 + 0.35) / 3.0;

        assert!(adjusted[..3]
            .iter()
            .all(|value| (*value - pooled).abs() < 1e-6));
        assert_eq!(adjusted[3], 0.90);
    }

    #[test]
    fn pava_crossings_are_ordered_with_per_grade_targets() {
        let raw_crossing_days = [80.0, 20.0, 60.0, 90.0];

        let crossings = crossings_on_grid(100, |day| {
            pava_non_decreasing(std::array::from_fn(|index| {
                (raw_crossing_days[index] - day) / 1_024.0
            }))
        });

        for (crossing, expected) in crossings.into_iter().zip([50.0, 50.0, 60.0, 90.0]) {
            assert!((crossing - expected).abs() < 1e-3, "{crossings:?}");
        }
    }

    #[test]
    fn answer_curve_grade_order_enforcement_can_be_disabled() {
        if SMALL_MODEL {
            return; // the big model's 128-curve head
        }
        let curve_for_basis_index = |basis_index: usize| {
            let mut weights = vec![0.0; basis_index + 1];
            weights[basis_index] = 1.0;
            ReviewCurve {
                power: None,
                ahead_logits: vec![0.0],
                weights,
            }
        };
        let curves = [
            curve_for_basis_index(100),
            curve_for_basis_index(80),
            curve_for_basis_index(90),
            curve_for_basis_index(110),
        ];
        let curve_refs = std::array::from_fn(|index| &curves[index]);

        let raw = unrounded_intervals_for_answer_curves(curve_refs, [0.75; 4], 36_500, false);
        let adjusted = unrounded_intervals_for_answer_curves(curve_refs, [0.75; 4], 36_500, true);

        assert!(raw[0] > raw[1]);
        assert!(adjusted
            .windows(2)
            .all(|pair| pair[0].unwrap() <= pair[1].unwrap()));
        assert_ne!(adjusted, raw);
    }

    #[test]
    fn simulated_answer_input_preserves_review_context() {
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: true,
            ease: None,
            duration_millis: None,
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [Some(0.81), Some(0.82), Some(0.83), Some(0.84)],
            enforce_grade_order: true,
        };

        let good = simulated_answer_input(&input, 3);

        assert!(!good.is_query);
        assert_eq!(good.ease, Some(3));
        assert_eq!(good.card_id, input.card_id);
        assert_eq!(good.current_elapsed_days, input.current_elapsed_days);
        assert_eq!(good.target_retentions, input.target_retentions);
        assert!(good.enforce_grade_order);
    }

    #[test]
    fn new_card_creation_elapsed_is_query_only() {
        let query = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: true,
            ease: None,
            duration_millis: None,
            card_type: Some(0),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [Some(0.81), Some(0.82), Some(0.83), Some(0.84)],
            enforce_grade_order: true,
        };

        assert_eq!(elapsed_seconds(&query), 259_200);
        assert_eq!(elapsed_days(&query, elapsed_seconds(&query)), 3);

        let answer = simulated_answer_input(&query, 3);

        assert_eq!(elapsed_seconds(&answer), -1);
        assert_eq!(elapsed_days(&answer, elapsed_seconds(&answer)), -1);
    }

    #[test]
    fn current_curve_retrievability_uses_elapsed_seconds_then_days() {
        if SMALL_MODEL {
            return; // the big model's 128-curve head
        }
        let mut input = ReviewInput {
            card_id: 123,
            note_id: None,
            deck_id: None,
            preset_id: None,
            is_query: true,
            ease: None,
            duration_millis: None,
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(123_456),
            target_retentions: [Some(0.9); 4],
            enforce_grade_order: true,
        };
        let curve = ReviewCurve {
            power: None,
            ahead_logits: vec![],
            weights: vec![0.2, 0.3, 0.5],
        };

        assert_eq!(
            current_curve_retrievability(&input, &curve),
            Some(predict_curve(&curve, 123_456.0))
        );
        input.current_elapsed_seconds = None;
        assert_eq!(
            current_curve_retrievability(&input, &curve),
            Some(predict_curve(&curve, 3.0 * SECONDS_PER_DAY as f32))
        );
        input.current_elapsed_days = Some(-1);
        assert_eq!(current_curve_retrievability(&input, &curve), None);
    }

    #[test]
    fn query_timestep_matches_stateful_output_without_retaining_state() {
        let values = (0..D_MODEL)
            .map(|index| (index as f32 - 64.0) / 128.0)
            .collect::<Vec<_>>();
        let r = values.iter().map(|value| value * 0.7).collect::<Vec<_>>();
        let k = values.iter().map(|value| value * -0.4).collect::<Vec<_>>();
        let v = values.iter().map(|value| value * 0.3).collect::<Vec<_>>();
        let w = values
            .iter()
            .map(|value| 0.85 + value * 0.05)
            .collect::<Vec<_>>();
        let a = values
            .iter()
            .map(|value| 0.5 + value * 0.1)
            .collect::<Vec<_>>();
        let k_deformed = values.iter().map(|value| value * -0.2).collect::<Vec<_>>();
        let state = (0..HEADS * HEAD_SIZE * HEAD_SIZE)
            .map(|index| (index % 31) as f32 * 0.002 - 0.03)
            .collect::<Vec<_>>();
        let zero_state = vec![0.0; state.len()];

        for query_state in [None, Some(state.as_slice())] {
            let stateful_state = query_state.unwrap_or(&zero_state);
            let (expected, _) = single_timestep(&r, &k, &v, &w, &a, &k_deformed, stateful_state);
            let mut actual = [0.0; D_MODEL];
            let mut next_row = [0.0; HEAD_SIZE];
            single_timestep_query_into(
                &r,
                &k,
                &v,
                &w,
                &a,
                &k_deformed,
                query_state,
                &mut actual,
                &mut next_row,
            );

            assert_eq!(actual.as_slice(), expected);

            let mut fast = [0.0; D_MODEL];
            single_timestep_query_fast_into(
                &r,
                &k,
                &v,
                &w,
                &a,
                &k_deformed,
                query_state,
                &mut fast,
            );
            let max_delta = fast
                .iter()
                .zip(&expected)
                .map(|(fast, expected)| (fast - expected).abs())
                .fold(0.0_f32, f32::max);
            assert!(max_delta <= 1e-6, "max fast timestep delta: {max_delta}");
        }
    }

    #[test]
    #[cfg_attr(not(rwkv_models), ignore = "needs RWKV model files in qt/aqt/rwkv_inference")]
    fn batched_retrievability_matches_scalar_query_path() {
        let model_path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        assert!(model_path.exists(), "RWKV parity model is missing");

        fn input(index: usize, is_query: bool) -> ReviewInput {
            ReviewInput {
                card_id: 10_000 + (index % 97) as i64,
                note_id: Some(20_000 + (index % 53) as i64),
                deck_id: Some(30_000 + (index % 7) as i64),
                preset_id: Some(40_000 + (index % 3) as i64),
                is_query,
                ease: (!is_query).then_some((index % 4 + 1) as u8),
                duration_millis: (!is_query).then_some(500 + index as i64 % 2_000),
                card_type: Some((index % 3) as i64),
                day_offset: Some((index / 11) as i64),
                current_elapsed_days: Some((index % 31) as i64),
                current_elapsed_seconds: Some((index % 31 * 86_400) as i64),
                target_retentions: [Some(0.9); 4],
                enforce_grade_order: true,
            }
        }

        let mut inference = RwkvInference::load(model_path, 0.9, 36_500).unwrap();
        inference
            .warm_up_reviews((0..257).map(|index| input(index, false)).collect(), false)
            .unwrap();
        let inputs = (0..129)
            .map(|index| input(index + 257, true))
            .collect::<Vec<_>>();
        let features = inputs
            .iter()
            .map(|input| inference.features.features_for(input))
            .collect::<Vec<_>>();
        let views = inputs
            .iter()
            .map(|input| inference.warm_up_states.state_view(input))
            .collect::<Vec<_>>();
        let items = views
            .iter()
            .zip(&features)
            .map(|(view, features)| ReviewPredictionQueryRef {
                features,
                state: view.as_ref(),
            })
            .collect::<Vec<_>>();

        let scalar = inference
            .model
            .review_retrievability_query_refs_scalar(&items)
            .unwrap();
        assert!(inference
            .model
            .review_retrievability_query_refs_batched(&[])
            .unwrap()
            .is_empty());
        let batched = inference
            .model
            .review_retrievability_query_refs_batched(&items)
            .unwrap();
        let max_delta = scalar
            .iter()
            .zip(&batched)
            .map(|(scalar, batched)| (scalar - batched).abs())
            .fold(0.0_f32, f32::max);

        assert!(max_delta <= 1e-6, "max batch prediction delta: {max_delta}");
    }

    #[test]
    fn batched_query_activations_match_scalar_functions() {
        let values = (-200_000..=200_000)
            .map(|i| i as f32 / 1_000.0)
            .chain([
                0.0,
                -0.0,
                1e-30,
                -1e-30,
                1e30,
                -1e30,
                f32::INFINITY,
                f32::NEG_INFINITY,
            ])
            .collect::<Vec<_>>();
        let check = |name: &str, batch: fn(&mut [f32]), scalar: &dyn Fn(f32) -> f32| {
            let mut actual = values.clone();
            batch(&mut actual);
            for (value, actual) in values.iter().zip(&actual) {
                let expected = scalar(*value);
                let tolerance = (4.0 * f32::EPSILON * expected.abs()).max(1e-30);
                assert!(
                    (actual - expected).abs() <= tolerance
                        || actual.to_bits() == expected.to_bits(),
                    "{name}({value}): {actual} vs {expected}"
                );
            }
        };
        check("sigmoid", query_sigmoid_batch_in_place, &sigmoid);
        check("silu", query_silu_batch_in_place, &|value| {
            value * sigmoid(value)
        });
        check("tanh", query_tanh_batch_in_place, &f32::tanh);
        check("decay", query_decay_batch_in_place, &query_decay);
    }

    /// Deterministic values spanning signs, zeros and several magnitudes, so
    /// vectorized reductions cannot hide a reordering behind uniform inputs.
    fn varied_values(len: usize, seed: u32) -> Vec<f32> {
        (0..len)
            .map(|index| {
                let phase = (index as u32)
                    .wrapping_mul(2_654_435_761)
                    .wrapping_add(seed);
                let magnitude = [1e-6, 0.03, 0.7, 5.0, 300.0][(phase >> 7) as usize % 5];
                match phase % 29 {
                    0 => 0.0,
                    1 => -0.0,
                    _ => ((phase % 997) as f32 / 498.5 - 1.0) * magnitude,
                }
            })
            .collect()
    }

    fn bits(values: &[f32]) -> Vec<u32> {
        values.iter().map(|value| value.to_bits()).collect()
    }

    #[test]
    fn query_timestep_kernel_matches_scalar_bits() {
        let [r, k, v, w, a, k_deformed] =
            std::array::from_fn(|seed| varied_values(D_MODEL, seed as u32 + 1));
        let w = w
            .iter()
            .map(|value| value.abs().min(1.0))
            .collect::<Vec<_>>();
        let state = varied_values(HEADS * HEAD_SIZE * HEAD_SIZE, 17);

        for state in [None, Some(state.as_slice())] {
            let mut expected = [0.0; D_MODEL];
            single_timestep_query_fast_scalar(
                &r,
                &k,
                &v,
                &w,
                &a,
                &k_deformed,
                state,
                &mut expected,
            );
            let mut actual = [0.0; D_MODEL];
            single_timestep_query_fast_into(&r, &k, &v, &w, &a, &k_deformed, state, &mut actual);

            assert_eq!(bits(&actual), bits(&expected), "state: {}", state.is_some());
        }
    }

    #[test]
    fn fused_head_normalization_matches_separate_passes_bits() {
        let mut zero_head = varied_values(D_MODEL, 3);
        zero_head[HEAD_SIZE..2 * HEAD_SIZE].fill(0.0);
        for values in [varied_values(D_MODEL, 5), zero_head] {
            let scales = [0.25, -1.5, 1.0, 3e-3];
            let mut expected = values.clone();
            normalize_heads_in_place(&mut expected);
            scale_heads_in_place(&mut expected, &scales);
            let mut actual = values;
            normalize_scale_heads_in_place(&mut actual, &scales);

            assert_eq!(bits(&actual), bits(&expected));
        }
    }

    #[test]
    fn fused_time_shift_mix_matches_per_mix_lerp_bits() {
        let lerp_weights = varied_values(8 * D_MODEL, 60);
        let x = varied_values(D_MODEL, 61);
        let x_shift = varied_values(D_MODEL, 62);
        // A row without time state uses its own input as the shift.
        for (row, x_shift) in [(0, &x_shift), (2, &x)] {
            let mut mixed: [Vec<f32>; 8] = std::array::from_fn(|_| vec![7.0; 3 * D_MODEL]);
            time_shift_mix_row(&x, x_shift, &lerp_weights, &mut mixed, row);

            for (mix, (mixed, weights)) in
                mixed.iter().zip(lerp_weights.chunks(D_MODEL)).enumerate()
            {
                let expected = (0..D_MODEL)
                    .map(|channel| lerp(x[channel], x_shift[channel], weights[channel]))
                    .collect::<Vec<_>>();
                let rows = mixed.chunks(D_MODEL).collect::<Vec<_>>();
                assert_eq!(bits(rows[row]), bits(&expected), "mix {mix} row {row}");
                for other in (0..3).filter(|other| *other != row) {
                    assert!(rows[other].iter().all(|value| *value == 7.0));
                }
            }
        }
    }

    #[test]
    fn bonus_gate_kernel_matches_scalar_bits() {
        let [output, r, bonus, k, v, g] =
            std::array::from_fn(|seed| varied_values(D_MODEL, seed as u32 + 40));
        let mut expected = output.clone();
        bonus_gate_scalar(&mut expected, &r, &bonus, &k, &v, &g);
        let mut actual = output;
        bonus_gate_in_place(&mut actual, &r, &bonus, &k, &v, &g);

        assert_eq!(bits(&actual), bits(&expected));
    }

    #[test]
    fn batched_normalization_preserves_scalar_bits() {
        for (dim, groups) in [(16, 1), (128, 1), (128, 4), (128, 8), (256, 1), (512, 1)] {
            let norm = Norm {
                dim,
                groups,
                eps: 1e-5,
                weight: (0..dim).map(|i| (i as f32 * 0.17).sin()).collect(),
                bias: (0..dim).map(|i| (i as f32 * 0.03).cos()).collect(),
            };
            for rows in [0, 1, 3, 4, 5, 127, 128, 129] {
                for magnitude in [0.0, 1e-8, 1.0, 1e4] {
                    let input = (0..rows * dim)
                        .map(|i| (i as f32 * 0.37).sin() * magnitude)
                        .collect::<Vec<_>>();
                    let expected = input
                        .chunks(dim)
                        .flat_map(|row| norm.apply(row))
                        .map(f32::to_bits)
                        .collect::<Vec<_>>();
                    let mut actual = Vec::new();
                    norm.apply_batch(&input, rows, &mut actual);
                    assert_eq!(
                        actual.into_iter().map(f32::to_bits).collect::<Vec<_>>(),
                        expected,
                        "dim={dim} groups={groups} rows={rows} magnitude={magnitude}"
                    );
                }
            }
        }
    }

    #[test]
    fn query_decay_matches_original_transform() {
        let mut max_delta = 0.0_f32;
        let values = (-100_000..=100_000).map(|i| i as f32 / 1_000.0).chain([
            f32::NEG_INFINITY,
            f32::INFINITY,
            -f32::MAX,
            f32::MAX,
            -0.0,
            f32::MIN_POSITIVE,
        ]);
        for value in values {
            let decay = -0.5 - softplus(-value);
            let expected = (-decay.exp()).exp();
            let actual = query_decay(value);
            assert!(actual.is_finite() && (0.0..=1.0).contains(&actual));
            max_delta = max_delta.max((actual - expected).abs());
        }
        assert!(max_delta <= 1.2e-7, "maximum decay delta: {max_delta}");
        assert!(query_decay(f32::NAN).is_nan());
    }

    #[test]
    fn restore_serialized_warm_up_state_replaces_and_removes_scopes() {
        let serialized = serialize_module_state(&ModuleState {
            packed: None,
            layers: vec![LayerState {
                time: Some(TimeState {
                    x_shift: vec![1.0, 2.0],
                    matrix: vec![3.0, 4.0],
                    packed_matrix: None,
                }),
                channel_shift: Some(vec![5.0, 6.0]),
            }],
        });
        let populated = ReviewStateOwned {
            card: Some(serialized.clone()),
            note: Some(serialized.clone()),
            deck: Some(serialized.clone()),
            preset: Some(serialized.clone()),
            global: Some(serialized.clone()),
        };
        let mut states = ReviewStateMaps::default();

        states
            .restore_serialized(1, Some(2), Some(3), Some(4), &populated)
            .unwrap();

        assert_eq!(
            serialize_module_state(&states.card.state(&1).unwrap()),
            serialized
        );
        assert_eq!(
            serialize_module_state(&states.note.state(&2).unwrap()),
            serialized
        );
        assert_eq!(
            serialize_module_state(states.deck.get(&3).unwrap()),
            serialized
        );
        assert_eq!(
            serialize_module_state(states.preset.get(&4).unwrap()),
            serialized
        );
        assert_eq!(
            serialize_module_state(states.global.as_ref().unwrap()),
            serialized
        );

        states
            .restore_serialized(
                1,
                Some(2),
                Some(3),
                Some(4),
                &ReviewStateOwned {
                    card: None,
                    note: None,
                    deck: None,
                    preset: None,
                    global: None,
                },
            )
            .unwrap();

        assert!(states.card.is_empty());
        assert!(states.note.is_empty());
        assert!(states.deck.is_empty());
        assert!(states.preset.is_empty());
        assert!(states.global.is_none());
    }

    #[test]
    fn workload_intervals_honor_grade_order() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        inference
            .warm_up_reviews(bulk_parity_reviews(60), false)
            .unwrap();
        let mut query = bulk_parity_reviews(1).remove(0);
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        query.target_retentions = [Some(0.3), Some(0.99), Some(0.3), Some(0.99)];
        query.enforce_grade_order = true;

        let states = SimulationStates::new(&inference.warm_up_states);
        let mut worker = inference.workload_worker(inference.features.clone(), HashMap::new());
        let heads: [ReviewHeads; 4] = ANSWER_EASES.map(|ease| {
            worker
                .review_heads_for_state(
                    &simulated_answer_input(&query, ease),
                    states.state_view(&query).as_ref(),
                )
                .unwrap()
        });
        let (ordered, _) = worker.answer_intervals(&query, &heads);
        let mut without_order = query.clone();
        without_order.enforce_grade_order = false;
        let (unordered, _) = worker.answer_intervals(&without_order, &heads);
        assert_ne!(ordered, unordered, "fixture must exercise grade ordering");

        let inputs = vec![query; 4];
        let actual = worker.selected_answer_intervals_for_inputs(&inputs, &states, &ANSWER_EASES);
        let expected = ordered.map(|days| days.map(|days| clamped_interval_days(days, 36_500)));
        assert_eq!(actual, expected);
    }

    #[test]
    fn forecast_decoded_cache_matches_packed_streams_after_answers() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        for precision in [StatePrecision::F32, StatePrecision::Int4CardsInt8Notes] {
            let mut inference =
                RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                    .unwrap();
            let reviews = precision_tests::reviews(14);
            inference.warm_up_reviews(reviews.clone(), false).unwrap();
            let resident_before = precision_tests::snapshot_bytes(&inference);
            let inputs = reviews
                .iter()
                .map(|review_input| RwkvWorkloadSimulationInput {
                    review_input: review_input.clone(),
                    interval_days: Some(1),
                    ease_factor: Some(2500),
                    reps: Some(1),
                    lapses: Some(0),
                })
                .collect::<Vec<_>>();
            let ids = StreamIds::for_workload_inputs(&inputs);
            let cache =
                ForecastDecodedStates::new(&inference.warm_up_states, &ids, 8_000_000).unwrap();
            assert_eq!(cache.bytes == 0, precision == StatePrecision::F32);
            assert!(cache.bytes <= 8_000_000);
            let empty = ForecastDecodedStates::new(&inference.warm_up_states, &ids, 0).unwrap();
            assert_eq!(empty.bytes, 0);

            for own_budget in [0, 16_384, 8_000_000] {
                let mut reference = SimulationStates::new(&inference.warm_up_states);
                let mut cached = SimulationStates::with_forecast_cache(
                    &inference.warm_up_states,
                    &cache,
                    own_budget,
                );
                let mut reference_worker =
                    inference.workload_worker(inference.features.clone(), HashMap::new());
                let mut cached_worker =
                    inference.workload_worker(inference.features.clone(), HashMap::new());
                for answer in reviews.iter().take(12) {
                    for query in reviews.iter().take(8) {
                        let left = reference_worker
                            .review_heads_for_state(query, reference.state_view(query).as_ref())
                            .unwrap();
                        let right = cached_worker
                            .review_heads_for_state(query, cached.state_view(query).as_ref())
                            .unwrap();
                        assert_eq!(
                            left.retrievability.to_bits(),
                            right.retrievability.to_bits()
                        );
                    }
                    reference_worker
                        .apply_simulation_answer(answer, &mut reference)
                        .unwrap();
                    cached_worker
                        .apply_simulation_answer(answer, &mut cached)
                        .unwrap();
                    assert!(cached.decoded_own_bytes <= own_budget);
                }
            }
            if precision != StatePrecision::F32 {
                let input = &reviews[0];
                let mut cached = SimulationStates::with_forecast_cache(
                    &inference.warm_up_states,
                    &cache,
                    8_000_000,
                );
                let mut worker =
                    inference.workload_worker(inference.features.clone(), HashMap::new());
                let mut state = worker
                    .review_heads_for_state(input, cached.state_view(input).as_ref())
                    .unwrap()
                    .next_state;
                state.card.packed = Some(b"invalid state".to_vec());
                assert!(cached.store(input, state).is_err());
            }
            assert_eq!(precision_tests::snapshot_bytes(&inference), resident_before);
        }
    }

    #[test]
    fn workload_simulation_matches_full_state_sequential_reference() {
        let Some(weights) = embedded_weights_path() else {
            eprintln!("skipping: embedded RWKV weights not found");
            return;
        };
        let mut inference = RwkvInference::load(weights, 0.9, 36_500).unwrap();
        inference
            .warm_up_reviews(bulk_parity_reviews(60), false)
            .unwrap();
        // A few of the 23 reviewed cards plus a new one, so the simulation must
        // ignore the other streams.
        let inputs = [(100, 2), (103, 2), (107, 2), (111, 2), (118, 2), (999, 0)]
            .into_iter()
            .map(|(card_id, card_type)| RwkvWorkloadSimulationInput {
                review_input: ReviewInput {
                    card_id,
                    note_id: Some(1000 + card_id / 2),
                    deck_id: Some(2000 + card_id % 3),
                    preset_id: Some(3000 + card_id % 2),
                    is_query: true,
                    ease: None,
                    duration_millis: None,
                    card_type: Some(card_type),
                    day_offset: Some(7310),
                    current_elapsed_days: Some(card_id % 9),
                    current_elapsed_seconds: Some((card_id % 9) * 86_400 + 300),
                    target_retentions: [None; 4],
                    enforce_grade_order: true,
                },
                interval_days: Some(card_id % 9 + 1),
                ease_factor: Some(2500),
                reps: Some(3),
                lapses: Some(0),
            })
            .collect::<Vec<_>>();
        let config = || RwkvWorkloadSimulationConfig {
            min_dr: 70,
            max_dr: 95,
            target_dr_step: 5,
            days_to_simulate: 20,
            review_limit: 4,
            new_limit: 1,
            new_cards_ignore_review_limit: false,
            max_interval: 36_500,
            review_order: ReviewCardOrder::RetrievabilityAscending,
            suspend_after_lapses: None,
            state_update_interval: 3,
            review_model: RwkvWorkloadReviewModel {
                grade_seconds: [12.0, 9.0, 7.0, 5.0],
                bucket_probabilities: vec![],
            },
        };

        // Reference: every target replays over a full copy of the resident
        // state, one target at a time.
        let full_state = inference.warm_up_states.clone_states();
        let mut reviewless_worker =
            inference.workload_worker(inference.features.clone(), inference.curves.clone());
        let introduced_inputs = inputs[..5].to_vec();
        let (reviewless_end_memorized, reviewless_end_weighted_memorized) = reviewless_worker
            .simulation_memorized_for_inputs(
                &introduced_inputs,
                &SimulationStates::new(&full_state),
                S90_TARGET_RETENTION,
                20,
            )
            .unwrap();
        let reference_config = config();
        let points = target_drs(70, 95, 5)
            .into_iter()
            .map(|dr| {
                let mut worker =
                    inference.workload_worker(inference.features.clone(), inference.curves.clone());
                let mut state = SimulationStates::new(&full_state);
                let point = worker
                    .simulate_workload_for_target(
                        &inputs,
                        &mut state,
                        RwkvWorkloadTargetConfig {
                            dr,
                            days_to_simulate: 20,
                            review_limit: 4,
                            new_limit: 1,
                            new_cards_ignore_review_limit: false,
                            max_interval: 36_500,
                            review_order: ReviewCardOrder::RetrievabilityAscending,
                            suspend_after_lapses: None,
                            state_update_interval: 3,
                            review_model: &reference_config.review_model,
                        },
                    )
                    .unwrap();
                (dr, point)
            })
            .collect::<Vec<_>>();
        let reference = RwkvWorkloadSimulationOutput {
            reviewless_end_memorized,
            reviewless_end_weighted_memorized,
            points,
        };

        let warm_up_snapshot = inference.warm_up_snapshot();
        let snapshot = RwkvWorkloadSimulationSnapshot {
            card_states: warm_up_snapshot.card_states,
            note_states: warm_up_snapshot.note_states,
            deck_states: warm_up_snapshot.deck_states,
            preset_states: warm_up_snapshot.preset_states,
            global_state: warm_up_snapshot.global_state,
            runtime_state: Some(inference.cache_state()),
        };
        let mut progress_steps = Vec::new();
        let from_snapshot = inference
            .simulate_workload(inputs.clone(), snapshot, config(), &mut |current, total| {
                progress_steps.push((current, total));
                Ok(())
            })
            .unwrap();
        let from_warm_up = inference
            .simulate_workload_from_warm_up(inputs, config(), &mut |_, _| Ok(()))
            .unwrap();

        let output_bits = |output: &RwkvWorkloadSimulationOutput| {
            let mut bits = vec![
                output.reviewless_end_memorized.to_bits(),
                output.reviewless_end_weighted_memorized.to_bits(),
            ];
            for (dr, point) in &output.points {
                bits.extend([
                    *dr,
                    point.memorized.to_bits(),
                    point.weighted_memorized.to_bits(),
                    point.cost.to_bits(),
                    point.review_count,
                ]);
            }
            bits
        };
        assert!(reference
            .points
            .iter()
            .any(|(_, point)| point.review_count > 0));
        assert_eq!(output_bits(&from_snapshot), output_bits(&reference));
        assert_eq!(output_bits(&from_warm_up), output_bits(&reference));
        assert_eq!(
            progress_steps,
            (0..=7).map(|step| (step, 7)).collect::<Vec<_>>()
        );
    }

    #[test]
    fn feature_state_scales_duration_from_milliseconds() {
        let mut features = FeatureState::default();
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        let values = features.features_for(&input);

        assert_eq!(values[8], scale_duration(1234.0));

        let query_values = features.features_for(&ReviewInput {
            is_query: true,
            ease: None,
            duration_millis: None,
            ..input
        });
        assert_eq!(query_values[8], 0.0);
    }

    #[test]
    fn feature_state_scales_dataset_review_state() {
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        for (state, expected_scaled_state) in [(2, 0.0), (4, 2.0), (5, 3.0)] {
            let values = FeatureState::default().features_for(&ReviewInput {
                card_type: Some(state),
                ..input.clone()
            });
            assert_eq!(values[22], expected_scaled_state);
        }

        let query_values = FeatureState::default().features_for(&ReviewInput {
            is_query: true,
            ease: None,
            card_type: Some(4),
            ..input
        });
        assert_eq!(query_values[22], 0.0);
    }

    #[test]
    fn feature_state_normalizes_day_offset_to_first_raw_review_day() {
        let mut features = FeatureState::default();
        let first = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(100),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };
        let second = ReviewInput {
            card_id: 124,
            note_id: Some(457),
            day_offset: Some(101),
            ..first.clone()
        };

        features.store_review(&first);
        let values = features.features_for(&second);

        assert_eq!(features.first_day_offset, Some(100));
        assert_eq!(values[17], -2.0 / 3.0);
    }

    #[test]
    fn feature_state_uses_card_specific_placeholder_for_missing_note_id() {
        let mut features = FeatureState::default();
        let input = ReviewInput {
            card_id: 123,
            note_id: None,
            deck_id: None,
            preset_id: None,
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        features.store_review(&input);

        assert!(features
            .id_encodings
            .contains_key(&(IdKind::Note, ID_PLACEHOLDER + input.card_id)));
        assert!(!features
            .id_encodings
            .contains_key(&(IdKind::Note, ID_PLACEHOLDER)));
    }

    #[test]
    fn feature_state_uses_benchmark_id_encoding_stream() {
        let mut features = FeatureState::default();
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        let values = features.features_for(&input);

        assert_eq!(
            &values[24..36],
            &[0.5, -1.5, 1.5, -0.5, -1.5, -1.5, 1.5, 1.5, 0.5, -0.5, 0.5, -0.5]
        );
        assert_eq!(
            &values[36..48],
            &[1.5, -0.5, 1.5, -0.5, -0.5, -1.5, -1.5, 0.5, 0.5, -0.5, 0.5, 1.5]
        );
        assert_eq!(
            &values[48..56],
            &[1.5, 1.5, -0.5, -1.5, -0.5, 1.5, -0.5, 1.5]
        );
        assert_eq!(
            &values[56..64],
            &[-0.5, -1.5, -0.5, 1.5, 1.5, 1.5, -1.5, -0.5]
        );
    }

    #[test]
    fn feature_state_initializes_today_like_benchmark() {
        let mut features = FeatureState::default();
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(0),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        let values = features.features_for(&input);

        assert_eq!(features.today, -1);
        assert_eq!(values[21], scale_cum_reviews_today(0));
    }

    #[test]
    fn feature_state_cache_round_trips_id_encodings_and_rng() {
        let mut features = FeatureState::default();
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1234),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };
        let first_values = features.features_for(&input);

        let mut cache = Vec::new();
        features.write_cache_state(&mut cache);
        let mut cursor = Cursor::new(&cache);
        let mut restored = FeatureState::read_cache_state(&mut cursor).unwrap();
        cursor.expect_end().unwrap();

        let restored_first_values = restored.features_for(&input);
        assert_eq!(&restored_first_values[24..64], &first_values[24..64]);

        let next_input = ReviewInput {
            card_id: 124,
            note_id: Some(457),
            deck_id: Some(790),
            preset_id: Some(11),
            ..input
        };
        let expected_next_values = features.features_for(&next_input);
        let restored_next_values = restored.features_for(&next_input);
        assert_eq!(&restored_next_values[24..64], &expected_next_values[24..64]);
    }

    fn id_feature_review_input(card_id: i64) -> ReviewInput {
        ReviewInput {
            card_id,
            note_id: Some(card_id + 1000),
            deck_id: Some(card_id + 2000),
            preset_id: Some(card_id + 3000),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1200),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        }
    }

    #[test]
    fn query_features_for_unseen_ids_do_not_shift_later_id_encodings() {
        let mut queried = FeatureState::default();
        let mut fresh = FeatureState::default();
        queried.features_for(&ReviewInput {
            is_query: true,
            ease: None,
            ..id_feature_review_input(123)
        });

        let answer = id_feature_review_input(124);
        let queried_values = queried.features_for(&answer);
        queried.store_review(&answer);
        let fresh_values = fresh.features_for(&answer);
        fresh.store_review(&answer);

        assert_eq!(queried_values, fresh_values);
        let mut queried_cache = Vec::new();
        queried.write_cache_state(&mut queried_cache);
        let mut fresh_cache = Vec::new();
        fresh.write_cache_state(&mut fresh_cache);
        assert_eq!(queried_cache, fresh_cache);
    }

    #[test]
    fn state_for_review_restore_rolls_back_new_id_encodings() {
        let mut features = FeatureState::default();
        let mut original_cache = Vec::new();
        features.write_cache_state(&mut original_cache);
        let answer = id_feature_review_input(123);
        let before = features.state_for_review(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
        );

        features.features_for(&answer);
        features.store_review(&answer);
        features.restore_state(&before);

        let mut restored_cache = Vec::new();
        features.write_cache_state(&mut restored_cache);
        assert_eq!(restored_cache, original_cache);

        let next_answer = id_feature_review_input(124);
        let restored_values = features.features_for(&next_answer);
        features.store_review(&next_answer);
        let mut fresh = FeatureState::default();
        let fresh_values = fresh.features_for(&next_answer);
        fresh.store_review(&next_answer);

        assert_eq!(restored_values, fresh_values);
        let mut restored_cache = Vec::new();
        features.write_cache_state(&mut restored_cache);
        let mut fresh_cache = Vec::new();
        fresh.write_cache_state(&mut fresh_cache);
        assert_eq!(restored_cache, fresh_cache);
    }

    #[test]
    fn state_for_review_restore_after_snapshot_reapplies_id_encodings() {
        let mut features = FeatureState::default();
        let answer = id_feature_review_input(123);
        let before = features.state_for_review(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
        );

        features.features_for(&answer);
        features.store_review(&answer);
        let after = features.state_for_review(
            answer.card_id,
            answer.note_id,
            answer.deck_id,
            answer.preset_id,
        );
        let mut answered_cache = Vec::new();
        features.write_cache_state(&mut answered_cache);

        features.restore_state(&before);
        assert!(features.id_encodings.is_empty());
        features.restore_state(&after);

        let mut restored_cache = Vec::new();
        features.write_cache_state(&mut restored_cache);
        assert_eq!(restored_cache, answered_cache);
    }

    #[test]
    fn features_for_then_store_review_matches_committed_encoding() {
        let mut features = FeatureState::default();
        for answer in [
            id_feature_review_input(123),
            ReviewInput {
                card_id: 124,
                deck_id: Some(2124),
                ..id_feature_review_input(123)
            },
            ReviewInput {
                note_id: None,
                deck_id: None,
                preset_id: None,
                ..id_feature_review_input(125)
            },
            ReviewInput {
                preset_id: Some(9999),
                ..id_feature_review_input(123)
            },
        ] {
            let values = features.features_for(&answer);
            features.store_review(&answer);

            let mut offset = 24;
            for key in id_encoding_keys(
                answer.card_id,
                answer.note_id,
                answer.deck_id,
                answer.preset_id,
            ) {
                let dim = id_encoding_dim(key.0);
                assert_eq!(
                    &values[offset..offset + dim],
                    features.id_encodings[&key].as_slice(),
                    "committed encoding differs for {key:?}"
                );
                offset += dim;
            }
        }
    }

    #[test]
    fn feature_state_for_card_restores_review_mutations() {
        let mut features = FeatureState::default();
        let before = features.state_for_card(123);
        let input = ReviewInput {
            card_id: 123,
            note_id: Some(456),
            deck_id: Some(789),
            preset_id: Some(10),
            is_query: false,
            ease: Some(3),
            duration_millis: Some(1200),
            card_type: Some(2),
            day_offset: Some(42),
            current_elapsed_days: Some(3),
            current_elapsed_seconds: Some(259_200),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };

        features.store_review(&input);
        assert_eq!(features.review_index, 1);
        assert!(features.card_set.contains_key(&123));
        assert_eq!(features.last_i.get(&123), Some(&0));
        assert_eq!(features.card_elapsed_days_cumulative.get(&123), Some(&3));
        assert_eq!(
            features.card_elapsed_seconds_cumulative.get(&123),
            Some(&259_200)
        );

        features.restore_state(&before);
        assert_eq!(features.review_index, 0);
        assert_eq!(features.today, -1);
        assert!(!features.card_set.contains_key(&123));
        assert!(!features.last_i.contains_key(&123));
        assert!(!features.last_new_cards.contains_key(&123));
        assert!(!features.card_first_day_offset.contains_key(&123));
        assert!(!features.card_elapsed_days_cumulative.contains_key(&123));
        assert!(!features.card_elapsed_seconds_cumulative.contains_key(&123));
        assert_eq!(features.previous_day_offset, None);
        assert_eq!(features.today_reviews, 0);
        assert_eq!(features.today_new_cards, 0);
    }

    /// kuma3's state file: a state with another shape than the model's, or a
    /// count that can't fit in the data, is refused (it would panic or abort
    /// later); a state of the right shape reads back.
    #[test]
    fn offline_state_reader_refuses_wrong_shapes_and_counts() {
        let state = |x_shift: usize| ModuleState {
            layers: vec![LayerState {
                time: Some(TimeState {
                    x_shift: vec![0.5; x_shift],
                    matrix: vec![0.25; HEADS * HEAD_SIZE * HEAD_SIZE],
                    packed_matrix: None,
                }),
                channel_shift: Some(vec![1.0; D_MODEL]),
            }],
            packed: None,
        };
        let read = |state: &ModuleState, layers: usize| {
            let mut bytes = Vec::new();
            write_half_module_state(&mut bytes, state).unwrap();
            read_half_module_state(&mut bytes.as_slice(), &mut Vec::new(), layers)
        };
        let back = read(&state(D_MODEL), 1).unwrap();
        assert_eq!(
            back.layers[0].time.as_ref().unwrap().x_shift,
            vec![0.5; D_MODEL]
        );
        assert!(
            read(&state(D_MODEL - 1), 1).is_err(),
            "x shift of the wrong length"
        );
        assert!(read(&state(D_MODEL), 2).is_err(), "wrong layer count");
        let mut empty = state(D_MODEL);
        empty.layers[0].time = None;
        assert!(
            read(&empty, 1).is_err(),
            "absent parts are written empty and refused"
        );

        // card/note states are written as the engine keeps them, f32 or compact
        for precision in [StatePrecision::F32, StatePrecision::Int8] {
            let mut values = state(D_MODEL);
            values.layers[0].time.as_mut().unwrap().matrix[7] = 0.333_333_34;
            // real modules have 2 or 3 layers (the compact format checks it)
            values.layers.push(values.layers[0].clone());
            let kept = values.compact(precision);
            let map: SharedStateMap = [(1, kept.clone())].into_iter().collect();
            let mut bytes = Vec::new();
            map.write_file_state(&1, &mut bytes).unwrap();
            let back = read_file_module_state(&mut bytes.as_slice(), 2).unwrap();
            assert_eq!(serialize_module_state(&back), serialize_module_state(&kept));
            assert!(
                read_file_module_state(&mut bytes.as_slice(), 3).is_err(),
                "wrong layer count"
            );
        }

        let huge = u32::MAX.to_le_bytes();
        assert!(
            Cursor::new(&huge).count(8).is_err(),
            "count larger than the data"
        );
        let mut small = 2_u32.to_le_bytes().to_vec();
        small.extend([0; 16]);
        assert_eq!(Cursor::new(&small).count(8).unwrap(), 2);
    }

    #[test]
    #[ignore]
    fn rwkv_state_compression_metrics() {
        let Ok(collection_path) = env::var("ANKI_RWKV_STATE_COMPRESSION_COLLECTION") else {
            eprintln!("set ANKI_RWKV_STATE_COMPRESSION_COLLECTION to a copied collection.anki2");
            return;
        };
        let weights_path = env::var("ANKI_RWKV_STATE_COMPRESSION_MODEL").unwrap_or_else(|_| {
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join(MODEL_PATH)
                .display()
                .to_string()
        });
        let limit = env_usize("ANKI_RWKV_STATE_COMPRESSION_LIMIT", 5_000);
        let power_iterations = env_usize("ANKI_RWKV_STATE_COMPRESSION_POWER_ITERS", 8);
        let selected_configs = env::var("ANKI_RWKV_STATE_COMPRESSION_CONFIGS")
            .ok()
            .map(|configs| {
                configs
                    .split(',')
                    .map(str::trim)
                    .filter(|config| !config.is_empty())
                    .map(str::to_string)
                    .collect::<Vec<_>>()
            })
            .filter(|configs| !configs.is_empty());
        let target_deck_ids = env_i64s("ANKI_RWKV_STATE_COMPRESSION_TARGET_DECK_IDS");

        let load_start = Instant::now();
        let (reviews, outcomes, target_reviews) =
            load_metric_reviews(&collection_path, limit, &target_deck_ids)
                .expect("failed to load metric reviews");
        assert!(!reviews.is_empty(), "no eligible reviews loaded");
        eprintln!(
            "rwkv_state_compression_metric_inputs reviews={} limit={} load_ms={:.1}",
            reviews.len(),
            limit,
            load_start.elapsed().as_secs_f64() * 1000.0
        );
        if let Some(configs) = &selected_configs {
            eprintln!(
                "rwkv_state_compression_metric_configs configs={}",
                configs.join(",")
            );
        }

        let configs = [
            ("raw", None),
            (
                "shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: HEAD_SIZE,
                    matrix_bits: None,
                    power_iterations,
                }),
            ),
            (
                "rank4-int8-shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: 4,
                    matrix_bits: Some(8),
                    power_iterations,
                }),
            ),
            (
                "rank4-int8",
                Some(StateCompression {
                    shift_bits: None,
                    matrix_rank: 4,
                    matrix_bits: Some(8),
                    power_iterations,
                }),
            ),
            (
                "rank4-int4-shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: 4,
                    matrix_bits: Some(4),
                    power_iterations,
                }),
            ),
            (
                "rank2-int8-shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: 2,
                    matrix_bits: Some(8),
                    power_iterations,
                }),
            ),
            (
                "rank2-int8",
                Some(StateCompression {
                    shift_bits: None,
                    matrix_rank: 2,
                    matrix_bits: Some(8),
                    power_iterations,
                }),
            ),
            (
                "rank2-int4-shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: 2,
                    matrix_bits: Some(4),
                    power_iterations,
                }),
            ),
            (
                "rank1-int4-shift-int4",
                Some(StateCompression {
                    shift_bits: Some(4),
                    matrix_rank: 1,
                    matrix_bits: Some(4),
                    power_iterations,
                }),
            ),
        ];

        let mut baseline = None;
        println!(
            "config,reviews,log_loss,delta_log_loss,rmse_bins,delta_rmse_bins,rmse_bins_weighted,delta_rmse_bins_weighted,elapsed_ms"
        );
        for (label, compression) in configs {
            if let Some(configs) = &selected_configs {
                if !configs.iter().any(|config| config == label) {
                    continue;
                }
            }
            let start = Instant::now();
            let mut inference = RwkvInference::load(PathBuf::from(&weights_path), 0.9, 36_500)
                .expect("RWKV load failed");
            let predictions =
                inference.warm_up_reviews_with_state_compression(&reviews, compression);
            assert_eq!(predictions.len(), outcomes.len());
            let target_predictions = predictions
                .iter()
                .zip(&target_reviews)
                .filter_map(|(prediction, target)| target.then_some(*prediction))
                .collect::<Vec<_>>();
            let target_outcomes = outcomes
                .iter()
                .zip(&target_reviews)
                .filter_map(|(outcome, target)| target.then_some(*outcome))
                .collect::<Vec<_>>();
            let metrics =
                MetricAccumulator::from_predictions(&target_predictions, &target_outcomes);
            let baseline_metrics = baseline.get_or_insert(metrics);
            println!(
                "{label},{},{:.9},{:.9},{:.9},{:.9},{:.9},{:.9},{:.1}",
                target_outcomes.len(),
                metrics.log_loss,
                metrics.log_loss - baseline_metrics.log_loss,
                metrics.rmse_bins,
                metrics.rmse_bins - baseline_metrics.rmse_bins,
                metrics.rmse_bins_weighted,
                metrics.rmse_bins_weighted - baseline_metrics.rmse_bins_weighted,
                start.elapsed().as_secs_f64() * 1000.0,
            );
        }
    }

    fn env_usize(name: &str, default: usize) -> usize {
        env::var(name)
            .ok()
            .and_then(|value| value.parse().ok())
            .unwrap_or(default)
    }

    fn env_i64s(name: &str) -> Vec<i64> {
        env::var(name)
            .ok()
            .map(|values| {
                values
                    .split(',')
                    .filter_map(|value| value.trim().parse().ok())
                    .collect()
            })
            .unwrap_or_default()
    }

    fn load_metric_reviews(
        collection_path: &str,
        limit: usize,
        target_deck_ids: &[i64],
    ) -> rusqlite::Result<(Vec<ReviewInput>, Vec<bool>, Vec<bool>)> {
        let connection = Connection::open_with_flags(
            collection_path,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY,
        )?;
        let created_secs: i64 =
            connection.query_row("select crt from col limit 1", [], |row| row.get(0))?;
        let preset_by_deck = deck_config_ids_by_deck(&connection)?;
        let mut previous_review_id_by_card = HashMap::new();
        let mut reviews = Vec::new();
        let mut outcomes = Vec::new();
        let mut target_reviews = Vec::new();
        let sql = format!(
            "\
with eligible as (
  select
    r.id,
    r.cid,
    c.nid,
    c.did,
    r.ease,
    r.time,
    r.type,
    lag(r.type) over (partition by r.cid order by r.id) as previous_type
  from revlog r
  join cards c on c.id = r.cid
  where r.ease between 1 and 4
    and r.type in (0, 1, 2, 3, 4, 5)
    and not (r.type = 3 and r.factor = 0)
), retained_starts as (
  select cid, max(id) as start_id
  from eligible
  where type = 0 and (previous_type is null or previous_type != 0)
  group by cid
)
select e.id, e.cid, e.nid, e.did, e.ease, e.time, e.type, e.id = s.start_id
from eligible e
join retained_starts s on s.cid = e.cid
where e.id >= s.start_id
order by e.id, e.cid
{}",
            if limit > 0 { "limit ?" } else { "" }
        );
        let mut statement = connection.prepare(&sql)?;
        let mut rows = if limit > 0 {
            statement.query([limit as i64])?
        } else {
            statement.query([])?
        };
        while let Some(row) = rows.next()? {
            let review_id: i64 = row.get(0)?;
            let card_id: i64 = row.get(1)?;
            let note_id: i64 = row.get(2)?;
            let deck_id: i64 = row.get(3)?;
            let ease: u8 = row.get(4)?;
            let duration_millis: i64 = row.get(5)?;
            let review_kind: i64 = row.get(6)?;
            let is_learning_start: bool = row.get(7)?;
            let previous_review_id = previous_review_id_by_card.insert(card_id, review_id);
            let elapsed_seconds = previous_review_id
                .map(|previous| ((review_id - previous) / 1000).max(0))
                .unwrap_or(-1);
            let review_secs = review_id / 1000;
            let day_offset = ((review_secs - created_secs).max(0)) / SECONDS_PER_DAY;
            let elapsed_days = previous_review_id.map_or(-1, |previous| {
                let previous_secs = previous / 1000;
                let previous_day_offset = ((previous_secs - created_secs).max(0)) / SECONDS_PER_DAY;
                (day_offset - previous_day_offset).max(0)
            });
            let preset_id = preset_by_deck.get(&deck_id).copied().flatten();
            outcomes.push(ease != 1);
            target_reviews.push(target_deck_ids.is_empty() || target_deck_ids.contains(&deck_id));
            reviews.push(ReviewInput {
                card_id,
                note_id: Some(note_id),
                deck_id: Some(deck_id),
                preset_id,
                is_query: false,
                ease: Some(ease),
                duration_millis: Some(duration_millis),
                card_type: Some(historical_state(review_kind, is_learning_start)),
                day_offset: Some(day_offset),
                current_elapsed_days: Some(elapsed_days),
                current_elapsed_seconds: Some(elapsed_seconds),
                target_retentions: [None; 4],
                enforce_grade_order: true,
            });
        }
        Ok((reviews, outcomes, target_reviews))
    }

    fn historical_state(review_kind: i64, is_learning_start: bool) -> i64 {
        if is_learning_start {
            0
        } else {
            review_kind + 1
        }
    }

    #[derive(Clone, Copy)]
    struct MetricSummary {
        log_loss: f64,
        rmse_bins: f64,
        rmse_bins_weighted: f64,
    }

    struct MetricAccumulator {
        log_loss_sum: f64,
        bins: [MetricBin; 20],
    }

    #[derive(Clone, Copy, Default)]
    struct MetricBin {
        count: usize,
        prediction_sum: f64,
        outcome_sum: f64,
    }

    impl MetricAccumulator {
        fn from_predictions(predictions: &[f32], outcomes: &[bool]) -> MetricSummary {
            let mut accumulator = Self {
                log_loss_sum: 0.0,
                bins: [MetricBin::default(); 20],
            };
            for (&prediction, &outcome) in predictions.iter().zip(outcomes) {
                accumulator.record(prediction, outcome);
            }
            accumulator.finish(predictions.len())
        }

        fn record(&mut self, prediction: f32, outcome: bool) {
            let prediction = valid_probability(prediction as f64);
            let outcome_value = if outcome { 1.0 } else { 0.0 };
            self.log_loss_sum += -(outcome_value * prediction.ln()
                + (1.0 - outcome_value) * (1.0 - prediction).ln());
            let bin =
                ((prediction * self.bins.len() as f64).floor() as usize).min(self.bins.len() - 1);
            let metric_bin = &mut self.bins[bin];
            metric_bin.count += 1;
            metric_bin.prediction_sum += prediction;
            metric_bin.outcome_sum += outcome_value;
        }

        fn finish(self, count: usize) -> MetricSummary {
            let mut unweighted_sum = 0.0;
            let mut weighted_sum = 0.0;
            let mut bin_count = 0;
            for bin in self.bins {
                if bin.count == 0 {
                    continue;
                }
                let predicted = bin.prediction_sum / bin.count as f64;
                let observed = bin.outcome_sum / bin.count as f64;
                let squared = (predicted - observed).powi(2);
                unweighted_sum += squared;
                weighted_sum += squared * bin.count as f64;
                bin_count += 1;
            }
            MetricSummary {
                log_loss: self.log_loss_sum / count as f64,
                rmse_bins: (unweighted_sum / bin_count as f64).sqrt(),
                rmse_bins_weighted: (weighted_sum / count as f64).sqrt(),
            }
        }
    }

    fn valid_probability(value: f64) -> f64 {
        if value.is_finite() {
            value.clamp(1e-6, 1.0 - 1e-6)
        } else {
            0.5
        }
    }
}
