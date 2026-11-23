// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! RWKV inference engine, ported from the JSchoreels/anki fork
//! (rslib/src/rwkv). Only the small-model engine is included, together with
//! the bundled show-time instant-only model (SM98), which predicts the
//! probability of recall when a card is shown ("RWKV-Instant").

// Engine code is kept close to upstream to ease future syncs.
#![allow(dead_code)]

const SMALL_MODEL: bool = true;
mod answer_time;
mod api;
pub(crate) mod instant;
mod paired;
include!("engine.rs");
