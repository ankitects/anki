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

#[path = "architecture.rs"]
mod architecture;
#[path = "bulk.rs"]
mod bulk;
use architecture::*;
#[path = "precision.rs"]
mod precision;
pub use precision::StatePrecision;
#[cfg(target_os = "macos")]
#[path = "matmul.rs"]
mod matmul;
#[cfg(target_arch = "aarch64")]
#[path = "query_kernels.rs"]
mod query_kernels;

fn relative_overdueness(retrievability: f32, target_retention: f32) -> f32 {
    retrievability / target_retention.max(0.0001)
}

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
const STATE_CACHE_READ_BUFFER_BYTES: usize = 16 * 1024 * 1024;

/// Rows per block in the bulk replay's blocked projection kernels. Bounds the
/// stack scratch used per block; larger blocks amortize weight-matrix streaming
/// over more rows.
const LINEAR_BLOCK_ROWS: usize = 32;
/// Upper bound on the recurrent state copied for workload-simulation targets
/// that run concurrently. A sampled simulation needs a few tens of MB per
/// target; one over a whole collection falls back to one target at a time.
const WORKLOAD_TARGET_STATE_BUDGET_BYTES: usize = 1024 * 1024 * 1024;


















#[cfg(not(target_os = "macos"))]
const MAX_CHANNEL_MIXER_DIM: usize = 256;
#[cfg(not(target_os = "macos"))]
const MAX_LORA_RANK: usize = 16;
#[cfg(target_os = "macos")]
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
            for (id, state) in states {
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
    #[cfg(target_os = "macos")]
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

    /// Loads a model from in-memory weights (no answer-time head).
    pub fn load_from_bytes(
        data: &[u8],
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
    ) -> io::Result<Self> {
        Ok(Self::from_model(
            SrsModel::from_bytes(data)?,
            target_retention,
            max_interval_days,
            precision,
        ))
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
        let work_items = inputs
            .iter()
            .zip(features)
            .map(|(input, features)| ReviewPredictionBorrowedWorkItem {
                features,
                state: self.warm_up_states.state_ref(input),
            })
            .collect::<Vec<_>>();

        self.model.review_retrievability_many_borrowed(&work_items)
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
            let state = self.warm_up_states.state_ref(&input);
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
        for (release, paired) in [
            (&self.warm_up_states.card, &instant.warm_up_states.card),
            (&self.warm_up_states.note, &instant.warm_up_states.note),
            (&self.warm_up_states.deck, &instant.warm_up_states.deck),
            (&self.warm_up_states.preset, &instant.warm_up_states.preset),
        ] {
            write_paired_snapshot_state_map(&mut out, release, paired)?;
        }
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
            stashed.warm_up_states.card = card;
            stashed.warm_up_states.note = note;
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
                    if review_count
                        .checked_rem(config.state_update_interval)
                        .map_or(review_count == 0, |rem| rem == 0) {
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
        let work_items = inputs
            .iter()
            .map(|input| ReviewPredictionBorrowedWorkItem {
                features: self.features.features_for(input),
                state: states.state_ref(input),
            })
            .collect::<Vec<_>>();
        let this = &*self;
        this.model
            .review_many_borrowed(&work_items, |index, heads| {
                let (current_interval, current_s90) =
                    this.current_intervals(&inputs[index], &heads);
                RwkvWorkloadQueryPrediction {
                    retrievability: heads.retrievability,
                    current_interval,
                    current_s90,
                }
            })
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
        let work_items = answer_inputs
            .iter()
            .map(|input| ReviewPredictionBorrowedWorkItem {
                features: self.features.features_for(input),
                state: states.state_ref(input),
            })
            .collect::<Vec<_>>();
        let (default_retention, max_interval_days) =
            (self.target_retention, self.max_interval_days);
        let curves = self
            .model
            .review_many_borrowed(&work_items, |_, heads| heads.curve);
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
        let heads = self.review_heads_for_state(input, states.state_ref(input))?;
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
    let curve_count = cursor.u32()? as usize;
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
        features
    }

    fn store_review(&mut self, input: &ReviewInput) {

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

    }

    fn assign_id_encodings(&mut self, input: &ReviewInput) {
        for key in id_encoding_keys(input.card_id, input.note_id, input.deck_id, input.preset_id) {
            self.id_encodings
                .entry(key)
                .or_insert_with(|| self.id_rng.encoding(id_encoding_dim(key.0)));
        }
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
    let count = cursor.u32()? as usize;
    let mut values = HashMap::with_capacity(count);
    for _ in 0..count {
        let kind = IdKind::from_cache_code(cursor.u8()?)?;
        let value = cursor.i64()?;
        let encoding = cursor.f32_vec()?;
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
}

struct Tensor {
    shape: Vec<usize>,
    values: Vec<f32>,
}

impl WeightMap {
    fn load(path: &PathBuf) -> io::Result<Self> {
        Self::from_bytes(&fs::read(path)?)
    }

    fn from_bytes(data: &[u8]) -> io::Result<Self> {
        let mut cursor = Cursor::new(data);
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
            .chunks_exact(std::mem::size_of::<f32>())
            .map(|bytes| f32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
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

#[cfg(not(target_os = "macos"))]
struct ReviewRetrievabilityScratch {
    feature_hidden: [f32; HEAD_DIM],
    normalized_features: [f32; HEAD_DIM],
    module: ModuleQueryScratch,
    prehead: [f32; D_MODEL],
    head: [f32; HEAD_DIM],
    logits: [f32; 4],
    probabilities: [f32; 4],
}

#[cfg(target_os = "macos")]
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

#[cfg(not(target_os = "macos"))]
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

    fn from_bytes(data: &[u8]) -> io::Result<Self> {
        Self::from_weights(WeightMap::from_bytes(data)?)
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
        #[cfg(all(target_os = "macos", not(test)))]
        {
            self.review_retrievability_query_refs_batched(items)
        }

        #[cfg(not(target_os = "macos"))]
        {
            self.review_retrievability_query_refs_scalar(items)
        }
    }

    #[cfg(not(target_os = "macos"))]
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

    #[cfg(target_os = "macos")]
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

    #[cfg(target_os = "macos")]
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
            .zip(scratch.logits.chunks_exact(4))
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

        let x = self.feature_mlp(features);


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

        ReviewHeads {
            retrievability,
            button_probabilities,
            curve,
            next_state,
        }
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

    #[cfg(not(target_os = "macos"))]
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

    #[cfg(target_os = "macos")]
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

#[derive(Default)]
struct ReviewStateMaps {
    card: HashMap<i64, ModuleState>,
    note: HashMap<i64, ModuleState>,
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
        [
            (&self.card, &ids.card),
            (&self.note, &ids.note),
            (&self.deck, &ids.deck),
            (&self.preset, &ids.preset),
        ]
        .into_iter()
        .flat_map(|(states, ids)| ids.iter().filter_map(|id| states.get(id)))
        .chain(&self.global)
        .map(serialized_module_state_len)
        .sum()
    }

    fn state_ref(&self, input: &ReviewInput) -> SrsStateRef<'_> {
        SrsStateRef {
            card: self.card.get(&input.card_id),
            note: input.note_id.and_then(|id| self.note.get(&id)),
            deck: input.deck_id.and_then(|id| self.deck.get(&id)),
            preset: input.preset_id.and_then(|id| self.preset.get(&id)),
            global: self.global.as_ref(),
        }
    }

    fn state_owned(&self, input: &ReviewInput) -> SrsStateOwned {
        SrsStateOwned {
            card: self.card.get(&input.card_id).cloned(),
            note: input.note_id.and_then(|id| self.note.get(&id).cloned()),
            deck: input.deck_id.and_then(|id| self.deck.get(&id).cloned()),
            preset: input.preset_id.and_then(|id| self.preset.get(&id).cloned()),
            global: self.global.clone(),
        }
    }

    fn serialized_state(&self, input: &ReviewInput) -> ReviewStateOwned {
        ReviewStateOwned {
            card: self.card.get(&input.card_id).map(serialize_module_state),
            note: input
                .note_id
                .and_then(|id| self.note.get(&id))
                .map(serialize_module_state),
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
                self.card.get(&query.card_id).cloned()
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
        self.card.insert(input.card_id, state.card);
        self.mark_dirty(input);
        if let Some(note_id) = input.note_id {
            self.note.insert(note_id, state.note);
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
        for (states, dirty) in [
            (&self.card, &self.dirty_card),
            (&self.note, &self.dirty_note),
            (&self.deck, &self.dirty_deck),
            (&self.preset, &self.dirty_preset),
        ] {
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
        for (states, dirty) in [
            (&self.card, &self.dirty_card),
            (&self.note, &self.dirty_note),
            (&self.deck, &self.dirty_deck),
            (&self.preset, &self.dirty_preset),
        ] {
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
    states: &HashMap<i64, ModuleState>,
    dirty: &HashSet<i64>,
    full: bool,
) -> Vec<i64> {
    let mut identities = if full {
        states.keys().copied().collect::<Vec<_>>()
    } else {
        dirty.iter().copied().collect::<Vec<_>>()
    };
    identities.sort_unstable();
    identities
}

fn state_cache_map_delta_len(
    states: &HashMap<i64, ModuleState>,
    dirty: &HashSet<i64>,
    full: bool,
) -> io::Result<usize> {
    let identities = state_cache_delta_identities(states, dirty, full);
    let mut len = 4_usize;
    for identity in identities {
        len = len.checked_add(9).ok_or_else(state_cache_delta_too_large)?;
        if let Some(state) = states.get(&identity) {
            len = len
                .checked_add(4 + serialized_module_state_len(state))
                .ok_or_else(state_cache_delta_too_large)?;
        }
    }
    Ok(len)
}

fn write_state_cache_map_delta(
    out: &mut impl io::Write,
    states: &HashMap<i64, ModuleState>,
    dirty: &HashSet<i64>,
    full: bool,
) -> io::Result<()> {
    let identities = state_cache_delta_identities(states, dirty, full);
    write_snapshot_u32(out, identities.len())?;
    for identity in identities {
        out.write_all(&identity.to_le_bytes())?;
        if let Some(state) = states.get(&identity) {
            out.write_all(&[1])?;
            write_snapshot_module_state(out, state)?;
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
    states: &mut HashMap<i64, ModuleState>,
    id: i64,
    state: Option<&[u8]>,
) -> io::Result<()> {
    if let Some(state) = deserialize_module_state(state)? {
        states.insert(id, state);
    } else {
        states.remove(&id);
    }
    Ok(())
}

fn restore_optional_serialized_state(
    states: &mut HashMap<i64, ModuleState>,
    id: Option<i64>,
    state: Option<&[u8]>,
) -> io::Result<()> {
    if let Some(id) = id {
        restore_serialized_state(states, id, state)?;
    }
    Ok(())
}

fn branched_optional_module_state(
    map: &HashMap<i64, ModuleState>,
    query_id: Option<i64>,
    answer_id: Option<i64>,
    next_state: &ModuleState,
) -> Option<ModuleState> {
    match query_id {
        Some(id) if Some(id) == answer_id => Some(next_state.clone()),
        Some(id) => map.get(&id).cloned(),
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
            source: &HashMap<i64, ModuleState>,
            ids: &HashSet<i64>,
            target: &mut HashMap<i64, ModuleState>,
            bytes: &mut usize,
            budget: usize,
        ) -> io::Result<()> {
            for id in ids {
                if *bytes >= budget {
                    break;
                }
                let Some(state) = source.get(id).filter(|state| state.packed.is_some()) else {
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

    fn state_ref(&self, input: &ReviewInput) -> SrsStateRef<'_> {
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
        SrsStateRef {
            card: lookup(
                &self.card,
                self.decoded.map(|cache| &cache.card),
                &self.base.card,
                input.card_id,
            ),
            note: input.note_id.and_then(|id| {
                lookup(
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

fn deserialize_state_map_subset(
    states: &[(i64, Vec<u8>)],
    ids: &HashSet<i64>,
) -> io::Result<HashMap<i64, ModuleState>> {
    let mut map = HashMap::with_capacity(ids.len());
    for (key, state) in states {
        if !ids.contains(key) {
            continue;
        }
        if let Some(state) = deserialize_module_state(Some(state.as_slice()))? {
            map.insert(*key, state);
        }
    }
    Ok(map)
}

fn deserialize_state_map(states: &[(i64, Vec<u8>)]) -> io::Result<HashMap<i64, ModuleState>> {
    let mut map = HashMap::with_capacity(states.len());
    for (key, state) in states {
        let Some(state) = deserialize_module_state(Some(state.as_slice()))? else {
            continue;
        };
        map.insert(*key, state);
    }
    Ok(map)
}

fn serialize_state_map(states: &HashMap<i64, ModuleState>) -> Vec<(i64, Vec<u8>)> {
    let mut states: Vec<_> = states
        .iter()
        .map(|(key, state)| (*key, serialize_module_state(state)))
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

fn write_snapshot_module_state(out: &mut impl io::Write, state: &ModuleState) -> io::Result<()> {
    write_snapshot_u32(out, serialized_module_state_len(state))?;
    write_serialized_module_state(out, state)
}

fn write_snapshot_state_map(
    out: &mut impl io::Write,
    states: &HashMap<i64, ModuleState>,
) -> io::Result<()> {
    write_snapshot_u32(out, states.len())?;
    let mut states = states.iter().collect::<Vec<_>>();
    states.sort_unstable_by_key(|(identity, _)| **identity);
    for (identity, state) in states {
        out.write_all(&identity.to_le_bytes())?;
        write_snapshot_module_state(out, state)?;
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
    release: &HashMap<i64, ModuleState>,
    instant: &HashMap<i64, ModuleState>,
) -> io::Result<()> {
    if release.len() != instant.len() {
        return Err(paired_streams_differ());
    }
    write_snapshot_u32(out, release.len())?;
    let mut states = release.iter().collect::<Vec<_>>();
    states.sort_unstable_by_key(|(identity, _)| **identity);
    for (identity, state) in states {
        let paired = instant.get(identity).ok_or_else(paired_streams_differ)?;
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
        for (states, seen) in [
            (&mut result.card, &mut seen_card),
            (&mut result.note, &mut seen_note),
            (&mut result.deck, &mut seen_deck),
            (&mut result.preset, &mut seen_preset),
        ] {
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
    states: &mut HashMap<i64, ModuleState>,
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
                        states.insert(identity, state);
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
    let count = cursor.u32()? as usize;
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
    let count = cursor.u32()? as usize;
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
        .chunks_exact_mut(std::mem::size_of::<f32>())
        .zip(values)
    {
        bytes.copy_from_slice(&value.to_le_bytes());
    }
}

fn write_state_cache_f32_slice(out: &mut impl io::Write, values: &[f32]) -> io::Result<()> {
    write_state_cache_u32(out, values.len())?;
    let mut buffer = [0_u8; 8192];
    for values in values.chunks(buffer.len() / 4) {
        for (destination, value) in buffer.chunks_exact_mut(4).zip(values) {
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

#[cfg(not(target_os = "macos"))]
struct ModuleQueryScratch {
    next: [f32; D_MODEL],
    layer: LayerQueryScratch,
}

#[cfg(target_os = "macos")]
#[derive(Default)]
struct ModuleQueryBatchScratch {
    next: Vec<f32>,
    layer: LayerQueryBatchScratch,
}

#[cfg(not(target_os = "macos"))]
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

#[cfg(not(target_os = "macos"))]
struct LayerQueryScratch {
    time_output: [f32; D_MODEL],
    time: TimeMixerQueryScratch,
    channel: ChannelMixerQueryScratch,
}

#[cfg(target_os = "macos")]
#[derive(Default)]
struct LayerQueryBatchScratch {
    time_output: Vec<f32>,
    time: TimeMixerQueryBatchScratch,
    channel: ChannelMixerQueryBatchScratch,
}

#[cfg(not(target_os = "macos"))]
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

    #[cfg(not(target_os = "macos"))]
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

    #[cfg(target_os = "macos")]
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

#[derive(Clone)]
struct LayerState {
    time: Option<TimeState>,
    channel_shift: Option<Vec<f32>>,
}

struct TimeMixer {
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
    v_lora: LoraSimple,
    a_lora: LoraSimple,
    d_lora: LoraSimple,
    lora_a_g: Linear,
    lora_b_g: Linear,
    out_group_norm: Norm,
}

#[cfg(not(target_os = "macos"))]
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

#[cfg(target_os = "macos")]
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

#[cfg(not(target_os = "macos"))]
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

        let x = self.layer_norm.apply(input);
        let (x_shift, state_matrix) = match state {
            Some(state) => (state.x_shift.as_slice(), state.matrix.as_slice()),
            None => (x.as_slice(), &[0.0; HEADS * HEAD_SIZE * HEAD_SIZE][..]),
        };

        let parts = self.mix_parts(&x, x_shift, v0);

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

        (
            out,
            parts.next_v0,
            TimeState {
                x_shift: x,
                matrix: next_matrix,
                packed_matrix: None,
            },
        )
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

    #[cfg(not(target_os = "macos"))]
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

    #[cfg(target_os = "macos")]
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

#[cfg(not(target_os = "macos"))]
struct ChannelMixerQueryScratch {
    x: [f32; D_MODEL],
    mixed: [f32; D_MODEL],
    hidden: [f32; MAX_CHANNEL_MIXER_DIM],
    projected: [f32; D_MODEL],
}

#[cfg(target_os = "macos")]
#[derive(Default)]
struct ChannelMixerQueryBatchScratch {
    x: Vec<f32>,
    mixed: Vec<f32>,
    hidden: Vec<f32>,
    projected: Vec<f32>,
}

#[cfg(not(target_os = "macos"))]
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

        let x = self.layer_norm.apply(input);
        let x_shift = state.map_or(x.as_slice(), |state| state.as_slice());
        let out = self.mix_value(&x, x_shift, input);
        (out, x)
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

    #[cfg(not(target_os = "macos"))]
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

    #[cfg(target_os = "macos")]
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

    #[cfg(not(target_os = "macos"))]
    fn apply_sigmoid_into(&self, input: &[f32], hidden: &mut [f32], out: &mut [f32]) {
        let hidden = &mut hidden[..self.a.output];
        self.a.apply_into(input, hidden);
        self.b.apply_into(hidden, out);
        sigmoid_in_place(out);
    }

    #[cfg(target_os = "macos")]
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

    #[cfg(not(target_os = "macos"))]
    fn apply_tanh_into(&self, input: &[f32], hidden: &mut [f32], out: &mut [f32]) {
        let hidden = &mut hidden[..self.a.output];
        self.a.apply_into(input, hidden);
        for value in hidden.iter_mut() {
            *value = value.tanh();
        }
        self.b.apply_into(hidden, out);
    }

    #[cfg(target_os = "macos")]
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
}

impl Linear {
    #[inline(always)]
    fn weight_values(&self) -> Cow<'_, [f32]> {
        Cow::Borrowed(&self.weight_by_input)
    }

    fn apply(&self, input: &[f32]) -> Vec<f32> {
        let mut out = vec![0.0; self.output];
        self.apply_into(input, &mut out);
        out
    }

    fn apply_into(&self, input: &[f32], out: &mut [f32]) {
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
    }

    #[cfg(target_os = "macos")]
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

        #[cfg(target_os = "macos")]
        let weight_by_input = self.weight_values();
        #[cfg(target_os = "macos")]
        matmul::matrix_times_matrix(input, &weight_by_input, rows, self.output, self.input, out);

        #[cfg(not(target_os = "macos"))]
        out.par_chunks_mut(self.output)
            .zip(input.par_chunks(self.input))
            .for_each(|(output, input)| self.apply_into(input, output));

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

    }

    #[cfg(target_os = "macos")]
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

#[cfg(not(target_os = "macos"))]
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
#[cfg(not(target_arch = "aarch64"))]
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
#[cfg(target_os = "macos")]
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
    for (mixed, weights) in mixed.iter_mut().zip(lerp_weights.chunks_exact(D_MODEL)) {
        let mixed = &mut mixed[row * D_MODEL..(row + 1) * D_MODEL];
        for channel in 0..D_MODEL {
            mixed[channel] = x[channel] + weights[channel] * shift_delta[channel];
        }
    }
}

/// `normalize_heads_in_place()` followed by `scale_heads_in_place()`.
#[cfg(target_os = "macos")]
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
#[cfg(target_os = "macos")]
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
#[cfg(all(target_os = "macos", not(target_arch = "aarch64")))]
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

#[cfg(all(target_os = "macos", not(target_arch = "aarch64")))]
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
#[cfg(target_os = "macos")]
const QUERY_DECAY_SCALE: f32 = 0.606_530_67;

/// Scalar reference for `query_decay_batch_in_place()`.

// Batched query-only activations. On macOS they use Accelerate's vectorized
// transcendental functions, which may differ from the scalar ones in the last
// bit; answer rows and warm-up state updates keep the scalar functions.

#[cfg(target_os = "macos")]
fn query_sigmoid_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::sigmoid_in_place(values);
    #[cfg(not(target_os = "macos"))]
    sigmoid_in_place(values);
}

#[cfg(target_os = "macos")]
fn query_silu_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::silu_in_place(values);
    #[cfg(not(target_os = "macos"))]
    silu_in_place(values);
}

#[cfg(target_os = "macos")]
fn query_tanh_batch_in_place(values: &mut [f32]) {
    #[cfg(target_os = "macos")]
    matmul::tanh_in_place(values);
    #[cfg(not(target_os = "macos"))]
    values.iter_mut().for_each(|value| *value = value.tanh());
}

#[cfg(target_os = "macos")]
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








