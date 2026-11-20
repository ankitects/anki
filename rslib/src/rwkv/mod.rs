// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Both verified RWKV architectures, with one specialized engine selected per
//! profile.

// Both engines intentionally compile the same sources with different dimensions.
#![allow(clippy::duplicate_mod)]

// The feature controls only the primary engine used by the offline benchmark
// API.
const SMALL_MODEL: bool = cfg!(feature = "rwkv-sm40");
// kuma3's offline runtime runs the small model through this one
// (scheduler/rwkv/offline_engine.rs)
pub(crate) mod alternate;
mod answer_time;
mod api;
pub mod paired;
mod runtime;
pub use answer_time::AnswerTimeHistory;
pub use answer_time::AnswerTimePrediction;
pub use answer_time::ANSWER_TIME_BINS;
pub use answer_time::ANSWER_TIME_FEATURES;
pub use runtime::ModelKind;
pub use runtime::RwkvRuntime;
pub use runtime::RwkvRuntimeState;
include!("engine.rs");
